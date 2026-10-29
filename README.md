# PhotoMission 外出拍照任務

這是一個不依賴第三方 Android library 的原生 Android 小工具。

## 目前功能

- 手動開始「外出拍照任務」
- 以前景服務維持任務狀態
- 在其他 App 上方顯示可拖曳的浮動 📷
- 15 / 30 / 60 分鐘尚未完成時逐步放大
- 點浮動 📷 開啟系統相機
- 照片直接保存到相簿 `Pictures/PhotoMission`
- 每拍完一張都詢問：「這張是否完成今天的任務？」
- 選「沒有」：任務繼續，浮動 📷 不消失
- 選「有」：任務、浮動按鈕、前景服務一起結束
- 任務狀態寫入 SharedPreferences；服務被系統重啟時會嘗試恢復
- 常駐通知也有「拍照」按鈕，可在浮動按鈕異常時當備援

## 第一次在手機使用

1. 安裝 APK。
2. 開啟 App。
3. 按「開始外出拍照任務」。
4. 第一次會要求「顯示在其他應用程式上層」：請允許後返回 App，再按一次開始。
5. Android 13 以上若詢問通知權限，建議允許，因為前景服務會用常駐通知作為第二層備援。
6. 第一次拍照時會要求相機權限。

## 完全不用 Android Studio：用 GitHub Actions 產 APK

### 方法 A：GitHub 網頁上傳

1. 在 GitHub 建立一個新的空 repository。
2. 把這個 ZIP 解壓縮。
3. 將解壓縮後 `PhotoMission` 資料夾中的所有檔案上傳到 repository 根目錄。
   - 必須看得到 `.github/workflows/build-apk.yml`。
4. Commit。
5. GitHub 上方切到 **Actions**。
6. 左側選 **Build APK**。
7. 按 **Run workflow**。
8. 執行成功後，打開該次 workflow run。
9. 頁面下方 **Artifacts** 下載 `PhotoMission-debug-apk`。
10. 解壓 artifact，裡面就是 `app-debug.apk`。

Debug APK 已由 Android build system 使用 debug key 簽署，可以直接安裝測試，不需要另外建立 keystore。

## 技術版本

- compileSdk: 35
- targetSdk: 33
- minSdk: 26
- Android Gradle Plugin: 8.8.2
- Kotlin: 2.0.21
- Gradle (CI): 8.10.2
- JDK: 17

targetSdk 刻意先使用 33，讓第一版前景服務不必處理 Android 14+ 新增的 foreground-service type 分類；仍可在較新的 Android 裝置安裝測試。後續確認功能穩定後，可以再升級 target SDK 並補齊對應服務類型。

## 已知限制 / 第一版取捨

1. 任務只會在你手動按「開始」後啟動，不自動判斷是否一起出門。
2. 浮動按鈕需要 Android 的 SYSTEM_ALERT_WINDOW 權限。
3. Android 廠商（例如某些省電很激進的系統）仍可能限制背景服務。如果發現服務會被清掉，可再針對該手機調整電池最佳化設定。
4. 「拍照後詢問」只針對從本 App 流程開啟的相機；若你自己另外從桌面打開相機，App 不會知道。
5. 目前照片完成與否由你自己確認，這是刻意設計：拍食物、風景等照片不會自動把任務關掉。

## 重要設計原則

這個 App 的目的不是假設你「下次一定會記得」，而是讓未完成任務留在外部環境中：

> 可以延後，但不能因為注意力切換就消失。

## GitHub Actions SDK setup note
The workflow uses `android-actions/setup-android@v4` and explicitly installs only `platform-tools` during SDK setup. This avoids the removed legacy Android SDK `tools` package that can cause `Failed to find package 'tools'` on older setup-android configurations.
