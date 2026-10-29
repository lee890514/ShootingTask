package com.example.photomission

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast

/**
 * v1.2 camera flow:
 *
 * Open the phone's ordinary camera UI and let that camera app own capture + storage.
 * PhotoMission does not provide EXTRA_OUTPUT, a FileProvider URI, or a MediaStore destination.
 *
 * When the user leaves the camera and comes back, ask whether the task was completed.
 * This is intentional: the user may have taken an unrelated picture, so returning from camera
 * never auto-completes the mission.
 */
class CaptureActivity : Activity() {

    companion object {
        private const val REQ_NORMAL_CAMERA = 4201
    }

    private var cameraLaunched = false
    private var dialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!MissionState.isActive(this)) {
            Toast.makeText(this, "目前沒有進行中的任務", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        cameraLaunched = savedInstanceState?.getBoolean("cameraLaunched", false) ?: false
        dialogShown = savedInstanceState?.getBoolean("dialogShown", false) ?: false

        if (!cameraLaunched && !dialogShown) {
            launchNormalCamera()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("cameraLaunched", cameraLaunched)
        outState.putBoolean("dialogShown", dialogShown)
        super.onSaveInstanceState(outState)
    }

    private fun launchNormalCamera() {
        cameraLaunched = true

        // This opens the same still-photo mode the camera app normally uses.
        // There is deliberately NO EXTRA_OUTPUT: the camera app saves photos exactly as usual.
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_NORMAL_CAMERA)
        } catch (_: ActivityNotFoundException) {
            cameraLaunched = false
            Toast.makeText(this, "找不到可用的相機 App", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Android SDK; used here without AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_NORMAL_CAMERA || dialogShown) return

        // A normal camera app often returns RESULT_CANCELED even after photos were taken, because
        // this intent is not a capture contract. We therefore never interpret resultCode.
        dialogShown = true
        showCompletionDialog()
    }

    private fun showCompletionDialog() {
        if (isFinishing || !MissionState.isActive(this)) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("外出拍照任務")
            .setMessage("剛才在相機裡有完成今天「至少幫她拍一張照片」的任務嗎？")
            .setCancelable(false)
            .setNegativeButton("沒有，繼續提醒") { _, _ ->
                finish()
            }
            .setPositiveButton("有，完成任務") { _, _ ->
                MissionState.finish(this)
                val serviceIntent = Intent(this, MissionService::class.java).apply {
                    action = MissionService.ACTION_FINISH
                }
                startService(serviceIntent)
                Toast.makeText(this, "今天的拍照任務已完成", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }
}
