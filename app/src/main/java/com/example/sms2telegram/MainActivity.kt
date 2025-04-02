// MainActivity.kt
package com.example.sms2telegram

import android.os.Bundle
import android.view.View
import android.widget.*
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var layoutSettings: LinearLayout
    private lateinit var layoutLog: LinearLayout
    private lateinit var editToken: EditText
    private lateinit var editChatId: EditText
    private lateinit var btnSendTest: Button
    private lateinit var tvLog: TextView
    private lateinit var logReceiver: BroadcastReceiver
    private var logReceiverRegistered = false

    private val PERMISSIONS = arrayOf(
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_CALL_LOG
    )
    private val PERM_REQ_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Initialize UI components
        tabLayout = findViewById(R.id.tabLayout)
        layoutSettings = findViewById(R.id.layoutSettings)
        layoutLog = findViewById(R.id.layoutLog)
        editToken = findViewById(R.id.editToken)
        editChatId = findViewById(R.id.editChatId)
        btnSendTest = findViewById(R.id.btnSendTest)
        tvLog = findViewById(R.id.tvLog)
        // Load saved token and chat_id
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        editToken.setText(prefs.getString("token", ""))
        editChatId.setText(prefs.getString("chat_id", ""))

        // Text change listeners to save settings
        editToken.addTextChangedListener(SimpleTextWatcher { text ->
            prefs.edit().putString("token", text.trim()).apply()
        })
        editChatId.addTextChangedListener(SimpleTextWatcher { text ->
            prefs.edit().putString("chat_id", text.trim()).apply()
        })

        // Button: send test
        btnSendTest.setOnClickListener {
            val intent = Intent(this, ForwardService::class.java).setAction("ACTION_SEND_TEST")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        // Setup tab selection behavior
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) {
                    layoutSettings.visibility = View.VISIBLE
                    layoutLog.visibility = View.GONE
                    if (logReceiverRegistered) {
                        try {
                            unregisterReceiver(logReceiver)
                        } catch (e: Exception) { }
                        logReceiverRegistered = false
                    }
                } else if (tab.position == 1) {
                    layoutSettings.visibility = View.GONE
                    layoutLog.visibility = View.VISIBLE
                    tvLog.text = readLogFile()
                    if (!logReceiverRegistered) {
                        try {
                            registerReceiver(logReceiver, IntentFilter("ACTION_LOG_APPEND"))
                            logReceiverRegistered = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                if (tab.position == 1 && logReceiverRegistered) {
                    try {
                        unregisterReceiver(logReceiver)
                    } catch (e: Exception) { }
                    logReceiverRegistered = false
                }
            }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        // Define log broadcast receiver
        logReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: Intent) {
                val line = intent.getStringExtra("line") ?: return
                tvLog.append(line + "\n")
            }
        }
        // Select the Settings tab by default
        tabLayout.getTabAt(0)?.select()

        // Request necessary permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERM_REQ_CODE)
        } else {
            startForwardService()
        }
    }

    override fun onPause() {
        super.onPause()
        if (logReceiverRegistered) {
            try {
                unregisterReceiver(logReceiver)
            } catch (e: Exception) { }
            logReceiverRegistered = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startForwardService()
            } else {
                Toast.makeText(this, "Permissions are required for app functionality.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun startForwardService() {
        val intent = Intent(this, ForwardService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun readLogFile(): String {
        val file = File(filesDir, "log.txt")
        return if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }
}

// Helper class for text change listener
private class SimpleTextWatcher(val onTextChanged: (text: String) -> Unit) : android.text.TextWatcher {
    override fun afterTextChanged(s: android.text.Editable?) {
        onTextChanged(s?.toString() ?: "")
    }
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
