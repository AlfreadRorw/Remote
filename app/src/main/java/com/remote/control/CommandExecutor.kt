package com.remote.control

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

class CommandExecutor(private val context: Context) {
    
    private val cameraManager: CameraManager = 
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    private val powerManager: PowerManager = 
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    private var isFlashlightOn = false
    
    fun execute(action: String): String {
        return when (action) {
            "screen_off" -> turnOffScreen()
            "screen_on" -> turnOnScreen()
            "flashlight_on" -> turnOnFlashlight()
            "flashlight_off" -> turnOffFlashlight()
            "open_tiktok" -> openTikTok()
            "open_youtube" -> openYouTube()
            "open_instagram" -> openInstagram()
            "open_whatsapp" -> openWhatsApp()
            "get_battery" -> getBatteryInfo()
            "vibrate" -> vibrateDevice()
            "open_settings" -> openSettings()
            "take_screenshot" -> takeScreenshot()
            "volume_up" -> volumeUp()
            "volume_down" -> volumeDown()
            else -> "Unknown action: $action"
        }
    }
    
    private fun turnOffScreen(): String {
        return try {
            @Suppress("DEPRECATION")
            powerManager.goToSleep(System.currentTimeMillis())
            
            // Alternatif: Set brightness ke 0
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                0
            )
            
            "Screen turned off"
        } catch (e: Exception) {
            "Failed to turn off screen: ${e.message}"
        }
    }
    
    private fun turnOnScreen(): String {
        return try {
            @Suppress("DEPRECATION")
            powerManager.wakeUp(System.currentTimeMillis())
            
            // Set brightness normal
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            
            "Screen turned on"
        } catch (e: Exception) {
            "Failed to turn on screen: ${e.message}"
        }
    }
    
    private fun turnOnFlashlight(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, true)
                isFlashlightOn = true
                "Flashlight turned on"
            } else {
                "Flashlight not supported on this device"
            }
        } catch (e: Exception) {
            "Failed to turn on flashlight: ${e.message}"
        }
    }
    
    private fun turnOffFlashlight(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, false)
                isFlashlightOn = false
                "Flashlight turned off"
            } else {
                "Flashlight not supported"
            }
        } catch (e: Exception) {
            "Failed to turn off flashlight: ${e.message}"
        }
    }
    
    private fun openTikTok(): String {
        return openApp("com.zhiliaoapp.musically", "TikTok")
    }
    
    private fun openYouTube(): String {
        return openApp("com.google.android.youtube", "YouTube")
    }
    
    private fun openInstagram(): String {
        return openApp("com.instagram.android", "Instagram")
    }
    
    private fun openWhatsApp(): String {
        return openApp("com.whatsapp", "WhatsApp")
    }
    
    private fun openApp(packageName: String, appName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "$appName opened"
            } else {
                "$appName not installed"
            }
        } catch (e: Exception) {
            "Failed to open $appName: ${e.message}"
        }
    }
    
    private fun getBatteryInfo(): String {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            
            val level = batteryIntent?.getIntExtra(
                android.os.BatteryManager.EXTRA_LEVEL,
                -1
            ) ?: -1
            
            val scale = batteryIntent?.getIntExtra(
                android.os.BatteryManager.EXTRA_SCALE,
                -1
            ) ?: -1
            
            val percentage = if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                -1
            }
            
            "Battery: $percentage%"
        } catch (e: Exception) {
            "Failed to get battery: ${e.message}"
        }
    }
    
    private fun vibrateDevice(): String {
        return try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        1000,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }
            
            "Device vibrated"
        } catch (e: Exception) {
            "Failed to vibrate: ${e.message}"
        }
    }
    
    private fun openSettings(): String {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Settings opened"
        } catch (e: Exception) {
            "Failed to open settings: ${e.message}"
        }
    }
    
    private fun takeScreenshot(): String {
        return try {
            // Requires root or MediaProjection
            val process = Runtime.getRuntime().exec("screencap -p /sdcard/screenshot.png")
            process.waitFor()
            "Screenshot saved"
        } catch (e: Exception) {
            "Screenshot failed: ${e.message}"
        }
    }
    
    private fun volumeUp(): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.adjustVolume(android.media.AudioManager.ADJUST_RAISE, 0)
            "Volume increased"
        } catch (e: Exception) {
            "Failed to increase volume: ${e.message}"
        }
    }
    
    private fun volumeDown(): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.adjustVolume(android.media.AudioManager.ADJUST_LOWER, 0)
            "Volume decreased"
        } catch (e: Exception) {
            "Failed to decrease volume: ${e.message}"
        }
    }
    
    fun cleanup() {
        // Matikan senter jika masih nyala
        if (isFlashlightOn) {
            turnOffFlashlight()
        }
    }
}