# TaskMode v1.3.2

Android 任務模式 App。

## 本版功能

- 手動開始／結束任務。
- 任務進行時顯示可拖曳的浮動快捷按鈕。
- 15 / 30 / 60 分鐘未完成時，浮動按鈕逐步放大。
- 使用 Google 官方 CameraX 執行內建操作介面。
- 產生的檔案儲存至系統相簿 `Pictures/TaskMode`。
- 每次執行後只詢問「任務完成了嗎？」；選「尚未完成」可繼續。
- 前景服務維持任務狀態。

## GitHub Actions 建置 APK

1. 把整個專案上傳到 GitHub repository。
2. 到 `Actions` → `Build APK` → `Run workflow`。
3. 成功後進入該次 workflow run，於最下方 `Artifacts` 下載 `TaskMode-debug-apk`。
4. 解壓後安裝 `app-debug.apk`。

## 使用

1. 開啟「任務模式」。
2. 按「開始任務」。
3. 第一次使用時允許「顯示在其他應用程式上層」、通知與相機權限。
4. 任務進行中，點浮動快捷按鈕執行。
5. 完成操作後選：
   - `完成`：結束任務。
   - `尚未完成`：任務保持，可繼續執行。

## 備註

- Android 10 以上檔案直接寫入 `Pictures/TaskMode`，不需要額外儲存權限。
- Application ID 保持 `com.example.photomission`，因此可直接覆蓋安裝舊版。
