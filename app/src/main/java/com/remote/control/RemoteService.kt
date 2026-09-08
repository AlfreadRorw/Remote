package com.remote.control

import android.app.*
import android.content.*
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RemoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("remote", MODE_PRIVATE) }
    private var torchOn = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1001, notification("Remote companion aktif"))
        scope.launch { loop() }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private suspend fun loop() {
        while (scope.isActive) {
            val base = prefs.getString("server_url", "")?.trimEnd('/') ?: ""
            if (base.isNotBlank()) {
                try { connect(base); poll(base) } catch (_: Exception) {}
            }
            delay(5000)
        }
    }
    private fun deviceId(): String = prefs.getString("device_id", null) ?: "unknown"
    private fun deviceName(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".take(80)
    private fun connect(base: String) { post("$base/api/connect", JSONObject().put("device_id", deviceId()).put("device_name", deviceName()).toString()) }
    private fun poll(base: String) {
        val req = Request.Builder().url("$base/api/check?device_id=${Uri.encode(deviceId())}").get().build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return
            val body = r.body?.string() ?: return
            val arr = JSONObject(body).optJSONArray("commands") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i); val id = c.optString("command_id"); val action = c.optString("action")
                val result = execute(action)
                val response = JSONObject().put("device_id", deviceId()).put("command_id", id).put("status", if (result.first) "success" else "failed").put("result", result.second).toString()
                post("$base/api/response", response)
            }
        }
    }
    private fun execute(action: String): Pair<Boolean, String> = try {
        when (action) {
            "flashlight_on" -> { setTorch(true); true to "Flashlight enabled" }
            "flashlight_off" -> { setTorch(false); true to "Flashlight disabled" }
            "open_tiktok" -> {
                val pm = packageManager; val launch = pm.getLaunchIntentForPackage("com.zhiliaoapp.musically") ?: pm.getLaunchIntentForPackage("com.ss.android.ugc.trill")
                if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); true to "TikTok opened" } else false to "TikTok is not installed"
            }
            else -> false to "Unsupported action"
        }
    } catch (e: Exception) { false to (e.message ?: "Execution failed") }
    private fun setTorch(enabled: Boolean) {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: throw IllegalStateException("No flashlight available")
        manager.setTorchMode(id, enabled); torchOn = enabled
    }
    private fun post(url: String, json: String) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        client.newCall(Request.Builder().url(url).post(body).build()).execute().use { }
    }
    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("remote", "Remote Control", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(text: String): Notification = NotificationCompat.Builder(this, "remote").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Remote Control Companion").setContentText(text).setOngoing(true).build()
}
