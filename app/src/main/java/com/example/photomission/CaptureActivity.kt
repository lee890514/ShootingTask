package com.example.photomission

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureActivity : Activity() {

    companion object {
        private const val REQ_CAMERA = 3001
        private const val REQ_CAMERA_PERMISSION = 3002
        private const val REQ_STORAGE_PERMISSION = 3003
    }

    private var photoUri: Uri? = null
    private var cameraStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!MissionState.isActive(this)) {
            Toast.makeText(this, "目前沒有進行中的任務", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE_PERMISSION)
            return
        }

        ensureCameraPermissionAndLaunch()
    }

    override fun onResume() {
        super.onResume()
        // 避免 Activity 因旋轉等情況重複開相機；真正的結果由 onActivityResult 處理。
    }

    private fun ensureCameraPermissionAndLaunch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        if (cameraStarted) return
        cameraStarted = true

        val uri = createImageUri()
        if (uri == null) {
            Toast.makeText(this, "無法建立照片檔案", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        photoUri = uri

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Android 11+ 會限制 PackageManager 對其他 App 的可見性。
        // 直接啟動標準相機 Intent，真的沒有相機可處理時再捕捉例外，
        // 可避免 resolveActivity() 在 Android 16 等版本上誤判為 null。
        intent.clipData = ClipData.newRawUri("PhotoMission photo", uri)

        try {
            startActivityForResult(intent, REQ_CAMERA)
        } catch (_: ActivityNotFoundException) {
            deleteCurrentPhoto()
            Toast.makeText(this, "找不到可用的相機 App", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Android SDK, intentionally used to avoid AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAMERA) return

        if (resultCode == RESULT_OK) {
            finalizePendingPhoto()
            showCompletionDialog()
        } else {
            deleteCurrentPhoto()
            finish()
        }
    }

    private fun showCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle("外出拍照任務")
            .setMessage("剛才這張照片有完成今天「至少幫她拍一張照片」的任務嗎？")
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

    private fun createImageUri(): Uri? {
        val resolver = contentResolver
        val name = "PhotoMission_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoMission")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun finalizePendingPhoto() {
        if (Build.VERSION.SDK_INT >= 29) {
            photoUri?.let { uri ->
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, values, null, null)
            }
        }
    }

    private fun deleteCurrentPhoto() {
        photoUri?.let {
            try { contentResolver.delete(it, null, null) } catch (_: Exception) {}
        }
        photoUri = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Toast.makeText(this, "需要權限才能拍照並保存照片", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        when (requestCode) {
            REQ_STORAGE_PERMISSION -> ensureCameraPermissionAndLaunch()
            REQ_CAMERA_PERMISSION -> launchCamera()
        }
    }
}
