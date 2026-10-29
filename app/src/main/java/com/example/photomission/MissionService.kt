package com.example.photomission

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class MissionService : Service() {

    companion object {
        const val ACTION_START = "com.example.photomission.START"
        const val ACTION_FINISH = "com.example.photomission.FINISH"
        private const val CHANNEL_ID = "photo_mission_active"
        private const val NOTIFICATION_ID = 4101
    }

    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private val stageUpdater = object : Runnable {
        override fun run() {
            if (MissionState.isActive(this@MissionService)) {
                updateBubbleStage()
                updateNotification()
                handler.postDelayed(this, 60_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FINISH -> {
                MissionState.finish(this)
                removeBubble()
                handler.removeCallbacks(stageUpdater)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (!MissionState.isActive(this)) {
                    MissionState.start(this)
                }
                resumeActiveMission()
            }
            null -> {
                // START_STICKY 讓系統重建 Service 時不會附帶原本 Intent。
                // 只有持久狀態仍標記為 active 才恢復；否則直接停止。
                if (MissionState.isActive(this)) {
                    resumeActiveMission()
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun resumeActiveMission() {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (Settings.canDrawOverlays(this)) showBubble()
        handler.removeCallbacks(stageUpdater)
        handler.post(stageUpdater)
    }

    override fun onDestroy() {
        handler.removeCallbacks(stageUpdater)
        removeBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        if (bubble != null) {
            updateBubbleStage()
            return
        }

        val textView = TextView(this).apply {
            text = "📷"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(220, 38, 38, 38))
            elevation = dp(6).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(220)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var downX = 0f
        var downY = 0f

        textView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    try { windowManager.updateViewLayout(textView, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - downX) + abs(event.rawY - downY)
                    if (moved < dp(12)) openCameraFlow()
                    true
                }
                else -> false
            }
        }

        bubble = textView
        bubbleParams = params
        windowManager.addView(textView, params)
        updateBubbleStage()
    }

    private fun updateBubbleStage() {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        val elapsed = (System.currentTimeMillis() - MissionState.startedAt(this)).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L

        val size = when {
            minutes >= 60 -> 132
            minutes >= 30 -> 104
            minutes >= 15 -> 80
            else -> 56
        }

        view.text = if (minutes >= 60) "📷\n還沒拍" else "📷"
        view.textSize = when {
            minutes >= 60 -> 22f
            minutes >= 30 -> 44f
            minutes >= 15 -> 36f
            else -> 30f
        }
        params.width = dp(size)
        params.height = dp(size)
        try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
    }

    private fun openCameraFlow() {
        val intent = Intent(this, CaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun removeBubble() {
        bubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubble = null
        bubbleParams = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "外出拍照任務",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "任務進行中時維持背景服務"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val cameraIntent = Intent(this, CaptureActivity::class.java)
        val cameraPending = PendingIntent.getActivity(
            this,
            1,
            cameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainPending = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val finishIntent = Intent(this, MissionService::class.java).apply { action = ACTION_FINISH }
        val finishPending = PendingIntent.getService(
            this,
            3,
            finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = ((System.currentTimeMillis() - MissionState.startedAt(this)) / 60_000L)
            .coerceAtLeast(0)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("📷 外出拍照任務進行中")
            .setContentText("尚未確認完成 · ${minutes} 分鐘")
            .setOngoing(true)
            .setContentIntent(mainPending)
            .addAction(android.R.drawable.ic_menu_camera, "拍照", cameraPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "結束", finishPending)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
