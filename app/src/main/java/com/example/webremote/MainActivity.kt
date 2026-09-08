package com.example.webremote

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.webremote.R.layout.activity_main)

        val prefs = getSharedPreferences("remote", Context.MODE_PRIVATE)
        val url = findViewById<EditText>(R.id.serverUrl)
        val token = findViewById<EditText>(R.id.pairToken)
        val name = findViewById<EditText>(R.id.deviceName)
        val status = findViewById<TextView>(R.id.statusText)

        url.setText(prefs.getString("url", ""))
        token.setText(prefs.getString("token", ""))
        name.setText(prefs.getString("name", Build.MODEL))

        findViewById<Button>(R.id.startButton).setOnClickListener {
            val base = url.text.toString().trim().removeSuffix("/")
            val t = token.text.toString().trim()
            if (!base.startsWith("https://") || t.length < 8) {
                status.text = "Status: URL HTTPS dan token minimal 8 karakter diperlukan"
                return@setOnClickListener
            }
            prefs.edit().putString("url", base).putString("token", t)
                .putString("name", name.text.toString().trim()).apply()
            val i = Intent(this, RemoteService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "Status: layanan remote aktif"
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, RemoteService::class.java))
            status.text = "Status: layanan remote dihentikan"
        }

        findViewById<Button>(R.id.adminButton).setOnClickListener {
            val admin = ComponentName(this, RemoteDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Izin ini hanya dipakai untuk perintah Screen Off melalui DevicePolicyManager.")
            startActivity(intent)
        }
    }
}