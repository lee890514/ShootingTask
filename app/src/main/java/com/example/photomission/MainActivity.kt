package com.example.photomission

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val startButton: Button = findViewById(R.id.startButton)
        val stopButton: Button = findViewById(R.id.stopButton)

        startButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return@setOnClickListener
            }

            requestNotificationPermissionIfNeeded()
            startMission()
        }

        stopButton.setOnClickListener {
            if (!MissionState.isActive(this)) return@setOnClickListener

            AlertDialog.Builder(this)
                .setTitle("結束任務？")
                .setMessage("照片任務尚未確認完成。確定要手動結束嗎？")
                .setNegativeButton("取消", null)
                .setPositiveButton("確定結束") { _, _ -> finishMission() }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun startMission() {
        if (!MissionState.isActive(this)) {
            MissionState.start(this)
        }
        val intent = Intent(this, MissionService::class.java).apply {
            action = MissionService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus()
    }

    private fun finishMission() {
        MissionState.finish(this)
        startService(Intent(this, MissionService::class.java).apply {
            action = MissionService.ACTION_FINISH
        })
        updateStatus()
    }

    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要顯示在其他 App 上層")
            .setMessage("外出任務需要顯示常駐的浮動 📷。下一頁請允許「顯示在其他應用程式上層」，回到 App 後再按一次開始。")
            .setPositiveButton("前往設定") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
    }

    private fun updateStatus() {
        if (MissionState.isActive(this)) {
            val minutes = ((System.currentTimeMillis() - MissionState.startedAt(this)) / 60_000L)
                .coerceAtLeast(0)
            statusText.text = "📷 任務進行中 · 已經過 ${minutes} 分鐘"
        } else {
            statusText.text = "目前沒有進行中的任務"
        }
    }
}
