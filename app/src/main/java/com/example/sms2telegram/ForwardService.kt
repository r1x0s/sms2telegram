// ForwardService.kt
package com.example.sms2telegram

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.IBinder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telephony.SmsMessage
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForwardService : Service() {
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: PhoneStateListener
    private lateinit var smsReceiver: BroadcastReceiver
    private val httpClient = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("MyPrefs", Context.MODE_PRIVATE) }
    private val channelId = "SMSForwardService"
    private var lastForwardTimeStr: String? = null
    private var lastForwardStatus: String = ""
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        // Create notification channel for foreground service
        notificationManager = getSystemService(NotificationManager::class.java) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SMS Forwarder Service", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Forward SMS and calls to Telegram"
            notificationManager.createNotificationChannel(channel)
        }
        // Setup phone call listener
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    val incomingNumber = phoneNumber ?: "Unknown"
                    forwardMessage("Call from $incomingNumber")
                }
            }
        }
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (_: SecurityException) {
            appendLogLine("${currentTime()} ERROR: Missing CALL permissions")
        }
        // Setup SMS receiver
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
                    val bundle = intent.extras
                    if (bundle != null) {
                        try {
                            val pdus = bundle.get("pdus") as? Array<*>
                            val format = bundle.getString("format")
                            if (!pdus.isNullOrEmpty()) {
                                val sb = StringBuilder()
                                var sender = ""
                                for (pdu in pdus) {
                                    val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                                    } else {
                                        SmsMessage.createFromPdu(pdu as ByteArray)
                                    }
                                    if (sender.isEmpty()) {
                                        sender = sms.originatingAddress ?: sms.displayOriginatingAddress ?: "Unknown"
                                    }
                                    sb.append(sms.messageBody)
                                }
                                val messageBody = sb.toString()
                                if (sender.isNotEmpty() && messageBody.isNotEmpty()) {
                                    forwardMessage("SMS from $sender: $messageBody")
                                }
                            }
                        } catch (e: Exception) {
                            appendLogLine("${currentTime()} ERROR: Failed to process incoming SMS: ${e.message}")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        try {
            registerReceiver(smsReceiver, filter)
        } catch (e: Exception) {
            appendLogLine("${currentTime()} ERROR: Cannot register SMS receiver: ${e.message}")
        }
        appendLogLine("${currentTime()} Service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        unregisterReceiver(smsReceiver)
        appendLogLine("${currentTime()} Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (lastForwardTimeStr == null) {
            startForeground(1, buildNotification("No messages forwarded yet."))
        } else {
            startForeground(1, buildNotification("Last: $lastForwardTimeStr ($lastForwardStatus)"))
        }
        if (intent?.action == "ACTION_SEND_TEST") {
            forwardMessage("Test message at ${currentTime()}")
        }
        return START_STICKY
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val testIntent = Intent(this, ForwardService::class.java).setAction("ACTION_SEND_TEST")
        val testPending = PendingIntent.getService(this, 1, testIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("sms2telegram")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_preferences, "Settings", openPending)
            .addAction(android.R.drawable.ic_menu_send, "Test", testPending)
            .build()
    }

    private fun updateNotification() {
        val text = if (lastForwardTimeStr != null) {
            "Last: $lastForwardTimeStr ($lastForwardStatus)"
        } else {
            "No messages forwarded yet."
        }
        val notification = buildNotification(text)
        notificationManager.notify(1, notification)
    }

    private fun forwardMessage(messageText: String) {
        // Ensure bot token and chat_id are set
        val token = prefs.getString("token", null)
        val chatId = prefs.getString("chat_id", null)
        if (token.isNullOrEmpty() || chatId.isNullOrEmpty()) {
            appendLogLine("${currentTime()} ERROR: Bot token or chat_id not set")
            lastForwardStatus = "Failed"
            lastForwardTimeStr = currentTime(timeOnly = true)
            updateNotification()
            return
        }
        Thread {
            var success = false
            var errorMsg: String? = null
            try {
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("api.telegram.org")
                    .addPathSegment("bot$token")
                    .addPathSegment("sendMessage")
                    .addQueryParameter("chat_id", chatId)
                    .addQueryParameter("text", messageText)
                    .build()
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.contains("\"ok\":false")) {
                        success = false
                        val match = Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)
                        errorMsg = match?.groups?.get(1)?.value ?: "Telegram API error"
                    } else {
                        success = true
                    }
                } else {
                    success = false
                    errorMsg = "HTTP ${response.code}"
                }
                response.close()
            } catch (e: Exception) {
                success = false
                errorMsg = e.message ?: e.toString()
            }
            val statusText = if (success) "OK" else "FAIL"
            val logLine = "${currentTime()} \"$messageText\" -> Telegram $statusText" + if (!success && errorMsg != null) " ($errorMsg)" else ""
            appendLogLine(logLine)
            val nowTime = currentTime(timeOnly = true)
            val statusWord = if (success) "Success" else "Failed"
            Handler(Looper.getMainLooper()).post {
                lastForwardStatus = statusWord
                lastForwardTimeStr = nowTime
                updateNotification()
            }
        }.start()
    }

    @Synchronized
    private fun appendLogLine(line: String) {
        try {
            val logFile = File(filesDir, "log.txt")
            FileWriter(logFile, true).use { it.appendLine(line) }
        } catch (_: Exception) { }

        val intent = Intent("ACTION_LOG_APPEND").apply {
            putExtra("line", line)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun currentTime(timeOnly: Boolean = false): String {
        val format = if (timeOnly) "HH:mm:ss" else "yyyy-MM-dd HH:mm:ss"
        return SimpleDateFormat(format, Locale.getDefault()).format(Date())
    }
}
