package com.example.photomission

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * v1.3.2: the app owns the camera UI using Google's official CameraX libraries.
 *
 * This deliberately avoids launching the phone vendor's external Camera app. On the user's
 * Android 16 device, that camera app hangs/crashes when launched through external camera intents.
 * Captures are saved directly to Pictures/TaskMode, then the user decides whether the task
 * is complete.
 */
class CaptureActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var shutterButton: Button
    private lateinit var switchCameraButton: Button
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var dialogVisible = false
    private val uiExecutor = Executor { command -> runOnUiThread(command) }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相機權限才能執行任務", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!MissionState.isActive(this)) {
            Toast.makeText(this, "目前沒有進行中的任務", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_capture)

        previewView = findViewById(R.id.previewView)
        shutterButton = findViewById(R.id.shutterButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        statusText = findViewById(R.id.cameraStatus)
        val backButton: Button = findViewById(R.id.backButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        backButton.setOnClickListener { finish() }
        shutterButton.setOnClickListener { takePhoto() }
        switchCameraButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            bindCameraUseCases()
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        statusText.text = "正在準備…"
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                statusText.text = "啟動失敗"
                Toast.makeText(this, "無法啟動：${e.message ?: "未知錯誤"}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, uiExecutor)
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            if (!provider.hasCamera(selector)) {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                Toast.makeText(this, "目前不可用", Toast.LENGTH_SHORT).show()
                return
            }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, imageCapture)

            shutterButton.isEnabled = true
            statusText.text = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                "後鏡頭"
            } else {
                "前鏡頭"
            }
        } catch (e: Exception) {
            shutterButton.isEnabled = false
            statusText.text = "設定失敗"
            Toast.makeText(this, "無法使用：${e.message ?: "未知錯誤"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        if (dialogVisible) return

        shutterButton.isEnabled = false
        statusText.text = "正在處理…"

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val displayName = "Task_$timestamp.jpg"

        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TaskMode")
            }
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()
        } else {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "TaskMode").apply {
                mkdirs()
            }
            ImageCapture.OutputFileOptions.Builder(File(dir, displayName)).build()
        }

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        shutterButton.isEnabled = true
                        statusText.text = "已完成處理"
                        showCompletionDialog()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        shutterButton.isEnabled = true
                        statusText.text = "操作失敗"
                        Toast.makeText(
                            this@CaptureActivity,
                            "處理失敗：${exception.message ?: "未知錯誤"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private fun showCompletionDialog() {
        if (dialogVisible || isFinishing || !MissionState.isActive(this)) return
        dialogVisible = true

        AlertDialog.Builder(this)
            .setTitle("任務確認")
            .setMessage("任務完成了嗎？")
            .setCancelable(false)
            .setNegativeButton("尚未完成") { _, _ ->
                dialogVisible = false
                statusText.text = if (lensFacing == CameraSelector.LENS_FACING_BACK) "後鏡頭" else "前鏡頭"
            }
            .setPositiveButton("完成") { _, _ ->
                dialogVisible = false
                MissionState.finish(this)
                val serviceIntent = Intent(this, MissionService::class.java).apply {
                    action = MissionService.ACTION_FINISH
                }
                startService(serviceIntent)
                Toast.makeText(this, "任務已完成", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroy()
    }
}
