package com.example.webremote

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.*
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class RemoteService : Service() {
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var deviceId: String

    private val poll = object : Runnable {
        override fun run() {
            checkOnce()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        deviceId = getSharedPreferences("remote", MODE_PRIVATE)
            .getString("deviceId", null) ?: UUID.randomUUID().toString().also {
                getSharedPreferences("remote", MODE_PRIVATE).edit().putString("deviceId", it).apply()
            }
        createChannel()
        startForeground(7, notification("Remote aktif"))
        connect()
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun prefs() = getSharedPreferences("remote", MODE_PRIVATE)
    private fun base() = prefs().getString("url", "")!!.removeSuffix("/")
    private fun token() = prefs().getString("token", "")!!

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, "remote")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Web Remote aktif")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel("remote", "Web Remote",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun connect() {
        val json = JSONObject()
            .put("deviceId", deviceId)
            .put("name", prefs().getString("name", Build.MODEL))
            .put("token", token())
        post("/api/connect", json) { }
    }

    private fun checkOnce() {
        if (base().isBlank() || token().isBlank()) return
        val url = HttpUrl.parse("$base()/api/check")!!.newBuilder()
            .addQueryParameter("deviceId", deviceId)
            .addQueryParameter("token", token()).build()

        client.newCall(Request.Builder().url(url).get().build()).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) return
                    val root = JSONObject(body)
                    if (root.isNull("command")) return
                    val cmd = root.getJSONObject("command")
                    execute(cmd.getString("id"), cmd.getString("command"))
                }
            }
        })
    }

    private fun execute(id: String, command: String) {
        try {
            val result = when (command) {
                "FLASH_ON" -> { setFlash(true); "Flashlight on" }
                "FLASH_OFF" -> { setFlash(false); "Flashlight off" }
                "OPEN_TIKTOK" -> { openTikTok(); "TikTok opened or requested" }
                "VIBRATE" -> { vibrate(); "Vibration triggered" }
                "BATTERY" -> batteryInfo()
                "SCREEN_ON" -> { wakeScreen(); "Wake lock requested" }
                "SCREEN_OFF" -> { lockScreen(); "Screen lock requested" }
                else -> throw IllegalArgumentException("Unknown command")
            }
            respond(id, true, result, null)
        } catch (e: Exception) {
            respond(id, false, null, e.message ?: "Execution failed")
        }
    }

    private fun setFlash(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 23) {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: throw IllegalStateException("No flashlight")
            cm.setTorchMode(id, on)
        } else throw IllegalStateException("Flashlight API requires Android 6+")
    }

    private fun openTikTok() {
        val pm = packageManager
        val launch = pm.getLaunchIntentForPackage("com.zhiliaoapp.musically")
            ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.tiktok.com"))
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(500, 150))
        else @Suppress("DEPRECATION") v.vibrate(500)
    }

    private fun batteryInfo(): String {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "Battery unavailable"
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        return "Battery ${level * 100 / scale}%"
    }

    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "WebRemote:wake")
        wl.acquire(3000)
    }

    private fun lockScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, RemoteDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) throw IllegalStateException("Device Admin not enabled by user")
        dpm.lockNow()
    }

    private fun respond(id: String, ok: Boolean, result: String?, error: String?) {
        val json = JSONObject()
            .put("deviceId", deviceId).put("commandId", id).put("token", token())
            .put("ok", ok).put("result", result).put("error", error)
        post("/api/response", json) { }
    }

    private fun post(path: String, json: JSONObject, done: () -> Unit) {
        val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())
        val req = Request.Builder().url(base() + path).post(body).build()
        client.newCall(req).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close(); done() }
        })
    }
}