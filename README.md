# PhotoMission v1.3

Android 外出拍照任務 App。

## v1.3 重點

- 專為 Android 16 外部相機 Intent 相容性問題改版。
- 不再呼叫手機廠商的相機 App。
- 使用 Google 官方 CameraX 在 PhotoMission 內直接顯示相機預覽與快門。
- 照片儲存至系統相簿 `Pictures/PhotoMission`。
- 每拍一張都詢問「這張有完成今天的任務嗎？」；選「沒有」可留在相機繼續拍。
- 任務仍由手動開啟、浮動 📷、15/30/60 分鐘逐步放大與前景服務維持。

## GitHub Actions 產 APK

1. 將整個專案上傳到 GitHub repository（Public 可避免私人 Actions 額度問題）。
2. 開啟 `Actions` → `Build APK` → `Run workflow`。
3. 成功後進入該次 workflow run，於最下方 `Artifacts` 下載 `PhotoMission-debug-apk`。
4. 解壓後安裝 `app-debug.apk`。

GitHub Actions 會自動下載 Android SDK、Gradle 與 CameraX；本機不需要 Android Studio。

## 第一次使用

1. 開啟 PhotoMission。
2. 按「開始外出拍照任務」。
3. 允許「顯示在其他 App 上層」。
4. Android 13+ 建議允許通知。
5. 第一次點浮動 📷 進入內建相機時，允許 Camera 權限。
6. 拍照後選：
   - `有，完成任務`：結束任務。
   - `沒有，繼續提醒`：任務保持，並留在相機畫面可再拍。

## 注意

- v1.3 新增 Google 官方 Jetpack CameraX 1.5.3 dependency；GitHub Actions 會自動下載，不需要手動安裝任何套件。
- Android 10 以上照片直接寫入 `Pictures/PhotoMission`，不需要儲存權限。
