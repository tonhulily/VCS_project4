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
    private var logCount = 0
    private var firstStart = true
    private var state = State.IDLE
    enum class State {
        IDLE, RUNNING, CONFIRM
    }
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_CONFIRM_RESET"
        const val ACTION_CONTINUE = "ACTION_CONTINUE"
    }
    private lateinit var controlReceiver: BroadcastReceiver
    private val logRun = object : Runnable {
        override fun run() {
            if (state == State.RUNNING) {
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

        controlReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_START-> handleStart()
                    ACTION_STOP -> stopLogging()
                    ACTION_RESET -> resetLogging()
                    ACTION_CONTINUE -> startLogging()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_START)
            addAction(ACTION_STOP)
            addAction(ACTION_RESET)
            addAction(ACTION_CONTINUE)
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
            state = State.CONFIRM
            updateNotification()
            handler.removeCallbacks(confirmTimeout)
            handler.postDelayed(confirmTimeout, 7000)
        }
    }
    private val confirmTimeout = Runnable {
        if (state == State.CONFIRM) {
            state = State.IDLE
            updateNotification()
        }
    }
    private fun resetLogging() {
        handler.removeCallbacks(confirmTimeout)
        logCount = 0
        startLogging()
    }
    private fun startLogging() {
        state = State.RUNNING
        vibrate()
        handler.removeCallbacks(logRun)
        handler.post(logRun)
        updateNotification()
    }
    private fun stopLogging() {
        handler.removeCallbacks(confirmTimeout)
        state = State.IDLE
        handler.removeCallbacks(logRun)
        updateNotification()
    }
    private fun buildNotification(): Notification {
        val startPI = getPI(ACTION_START, 0)
        val stopPI = getPI(ACTION_STOP, 1)
        val resetPI = getPI(ACTION_RESET, 2)
        val continuePI = getPI(ACTION_CONTINUE, 3)

        val builder = NotificationCompat.Builder(this, "channel_1")
            .setContentTitle("VCS Project 4")
            .setSmallIcon(getIcon())
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        when (state) {
            State.IDLE -> {
                builder.setContentText("Đã dừng • $logCount logs")
                    .addAction(R.drawable.ic_play, "Start", startPI)
            }

            State.RUNNING -> {
                builder.setContentText("Đang chạy • $logCount logs")
                    .addAction(R.drawable.ic_pause, "Stop", stopPI)
            }

            State.CONFIRM -> {
                builder.setContentTitle("Restart log?")
                    .setContentText("Reset hay tiếp tục?")
                    .addAction(android.R.drawable.ic_menu_delete, "Reset", resetPI)
                    .addAction(android.R.drawable.ic_media_play, "Continue", continuePI)
            }
        }
        return builder.build()
    }

    private fun getIcon(): Int {
        return when (state) {
            State.RUNNING -> android.R.drawable.ic_media_pause
            State.IDLE -> android.R.drawable.ic_media_play
            State.CONFIRM -> android.R.drawable.ic_dialog_alert
        }
    }
    private fun getPI(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        handler.removeCallbacks(logRun)
        unregisterReceiver(controlReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}