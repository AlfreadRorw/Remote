package com.remote.control

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("remote", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 56, 40, 40) }
        val title = TextView(this).apply { text = "Remote Control Companion"; textSize = 24f }
        val info = TextView(this).apply { text = "Masukkan URL deployment Vercel. Aplikasi hanya menjalankan perintah yang didukung saat service aktif."; setPadding(0, 12, 0, 20) }
        urlInput = EditText(this).apply { hint = "https://nama-project.vercel.app"; setText(prefs.getString("server_url", "")); inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI }
        status = TextView(this).apply { setPadding(0, 20, 0, 12) }
        val start = Button(this).apply { text = "Simpan & Mulai"; setOnClickListener { startRemote() } }
        val stop = Button(this).apply { text = "Hentikan Service"; setOnClickListener { stopService(Intent(this@MainActivity, RemoteService::class.java)); status.text = "Service dihentikan" } }
        val id = TextView(this).apply { text = "Device ID: ${deviceId()}"; textSize = 12f; setPadding(0, 16, 0, 0) }
        root.addView(title); root.addView(info); root.addView(urlInput); root.addView(status); root.addView(start); root.addView(stop); root.addView(id)
        setContentView(root)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
    }

    private fun startRemote() {
        val url = urlInput.text.toString().trim().trimEnd('/')
        if (!url.startsWith("https://")) { status.text = "URL harus HTTPS, contoh: https://project.vercel.app"; return }
        prefs.edit().putString("server_url", url).apply()
        ContextCompat.startForegroundService(this, Intent(this, RemoteService::class.java))
        status.text = "Service berjalan. Device akan muncul di panel setelah terhubung." 
    }
    private fun deviceId(): String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
}
