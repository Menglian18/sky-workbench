package com.nana.workbench.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 休息铃声的倒计时服务。
 *
 * 为什么要用"前台服务"：安卓规定，想在**锁屏后**还继续倒计时并在到点时响铃，
 * 就得用前台服务。它按惯例要配一条通知 —— 我们在清单里声明了通知权限，
 * 但**绝不主动弹窗索取**：就算系统不显示通知，铃声照样响，也不会打扰人。
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getLongExtra(EXTRA_SECONDS, 300L).coerceAtLeast(1L)
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "休息" }
                startTimer(seconds, label)
            }
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(seconds: Long, label: String) {
        tickJob?.cancel()
        running.value = true
        remainingSeconds.value = seconds

        startForegroundCompat(label, seconds)

        tickJob = scope.launch {
            var left = seconds
            while (left > 0) {
                remainingSeconds.value = left
                notifyProgress(label, left)
                delay(1000L)
                left -= 1
            }
            remainingSeconds.value = 0
            running.value = false
            ring()
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopTimer() {
        tickJob?.cancel()
        tickJob = null
        remainingSeconds.value = 0
        running.value = false
        stopForegroundCompat()
        stopSelf()
    }

    private fun startForegroundCompat(label: String, seconds: Long) {
        val notification = buildNotification(label, seconds)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            // 起不来也不能崩：倒计时照样在服务里跑，只是没有通知
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    private fun notifyProgress(label: String, seconds: Long) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(label, seconds))
        }
    }

    private fun buildNotification(label: String, seconds: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$label · 还剩 ${TimerFormat.remaining(seconds)}")
            .setContentText("到点会响铃并震动")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "休息倒计时",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "显示休息还剩多久" }
        manager.createNotificationChannel(channel)
    }

    /** 到点了：响一声 + 震动。用系统自带的提示音，不打包任何音频文件。 */
    private fun ring() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2500)
        }
        runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(900L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    companion object {
        const val ACTION_START = "com.nana.workbench.tools.START"
        const val ACTION_STOP = "com.nana.workbench.tools.STOP"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_LABEL = "label"

        private const val CHANNEL_ID = "rest_timer"
        private const val NOTIFICATION_ID = 1001

        /** 界面可以直接观察这两个值 */
        val running = MutableStateFlow(false)
        val remainingSeconds = MutableStateFlow(0L)

        fun start(context: Context, seconds: Long, label: String) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_LABEL, label)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }
    }
}
