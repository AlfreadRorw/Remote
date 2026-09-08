package com.remote.control

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NetworkManager(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )
    
    companion object {
        // GANTI DENGAN URL VERCEL KAMU
        const val API_BASE = "https://your-app.vercel.app/api"
    }
    
    fun registerDevice() {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("device_id", deviceId)
                    put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("android_version", Build.VERSION.RELEASE)
                    put("battery", getBatteryLevel())
                }
                
                val request = Request.Builder()
                    .url("$API_BASE/connect")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        android.util.Log.d("RemoteControl", "Device registered")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RemoteControl", "Register failed: ${e.message}")
            }
        }.start()
    }
    
    fun checkCommands(callback: (List<JSONObject>) -> Unit) {
        Thread {
            try {
                val request = Request.Builder()
                    .url("$API_BASE/check?device_id=$deviceId")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val commandsArray = json.optJSONArray("commands") ?: JSONArray()
                        
                        val commands = mutableListOf<JSONObject>()
                        for (i in 0 until commandsArray.length()) {
                            commands.add(commandsArray.getJSONObject(i))
                        }
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback(commands)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent fail, retry next poll
            }
        }.start()
    }
    
    fun sendResponse(commandId: String, result: String) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("device_id", deviceId)
                    put("command_id", commandId)
                    put("status", "completed")
                    put("result", result)
                }
                
                val request = Request.Builder()
                    .url("$API_BASE/response")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                client.newCall(request).execute()
                
                android.util.Log.d("RemoteControl", "Response sent: $commandId")
            } catch (e: Exception) {
                android.util.Log.e("RemoteControl", "Response failed: ${e.message}")
            }
        }.start()
    }
    
    private fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        return batteryIntent?.getIntExtra(
            android.os.BatteryManager.EXTRA_LEVEL,
            -1
        ) ?: -1
    }
}