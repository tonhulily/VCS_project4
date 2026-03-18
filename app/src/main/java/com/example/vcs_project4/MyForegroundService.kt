package com.example.vcs_project4

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat

class MyForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isLogging = false
    private var logCount = 0
    private var firstStart = true

    private lateinit var controlReceiver: BroadcastReceiver

    private val logRunnable = object : Runnable {
        override fun run() {
            if (isLogging) {
                logCount++
                Log.d("VCS_LOG", "Log lần $logCount")
                updateNotification()
                handler.postDelayed(this, 5000)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        createChannel()
        val manager = getSystemService(NotificationManager::class.java)

        controlReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "ACTION_START" -> handleStart()
                    "ACTION_STOP" -> stopLogging()
                    "ACTION_CONFIRM_RESET" -> {
                        manager.cancel(2)
                        logCount = 0
                        startLogging()
                    }

                    "ACTION_CONTINUE" -> {
                        manager.cancel(2)
                        startLogging()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("ACTION_START")
            addAction("ACTION_STOP")
            addAction("ACTION_CONFIRM_RESET")
            addAction("ACTION_CONTINUE")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        return START_NOT_STICKY
    }

    private fun handleStart() {
        if (firstStart) {
            firstStart = false
            startLogging()
        } else {
            showConfirmNotification()
        }
    }
    private fun startLogging() {
        if (!isLogging) {
            isLogging = true
            vibrate()
            handler.post(logRunnable)
            updateNotification()
        }
    }
    private fun stopLogging() {
        isLogging = false
        handler.removeCallbacks(logRunnable)
        updateNotification()
    }


    private fun buildNotification(): Notification {
        val startIntent = Intent("ACTION_START").apply {
            setPackage(packageName)
        }

        val stopIntent = Intent("ACTION_STOP").apply {
            setPackage(packageName)
        }

        val startPI = PendingIntent.getBroadcast(this, 0, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopPI = PendingIntent.getBroadcast(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, "channel_1")
            .setContentTitle("VCS Project 4")
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (isLogging) {
            builder.setSmallIcon(R.drawable.ic_play)
                .setContentText("Đang chạy • $logCount logs")
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPI)
        } else {
            builder.setSmallIcon(R.drawable.ic_pause)
                .setContentText("Đã dừng • $logCount logs")
                .addAction(android.R.drawable.ic_media_play, "Start", startPI)
        }

        return builder.build()
    }
    private fun showConfirmNotification() {

        val resetIntent = Intent("ACTION_CONFIRM_RESET").setPackage(packageName)
        val continueIntent = Intent("ACTION_CONTINUE").setPackage(packageName)

        val resetPI = PendingIntent.getBroadcast(this, 2, resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val continuePI = PendingIntent.getBroadcast(this, 3, continueIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "channel_1")
            .setContentTitle("Restart log?")
            .setContentText("Reset hay tiếp tục?")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .addAction(android.R.drawable.ic_menu_delete, "Reset", resetPI)
            .addAction(android.R.drawable.ic_media_play, "Continue", continuePI)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2, notification)
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification())
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            "channel_1",
            "Foreground Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }


    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createOneShot(200, 100))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(logRunnable)
        unregisterReceiver(controlReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}