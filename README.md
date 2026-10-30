# 有效期限簿

繁體中文、單機單人使用的 Android 有效期限管理 APP。Kotlin 與 Android Views，最低 Android 7.0（API 24）。不需要帳號或網路。

## 開啟與建置

以 Android Studio 開啟本資料夾。使用 Gradle Wrapper 9.6.0、Android Gradle Plugin 9.4.1、Android SDK 37，以及 Android Studio 隨附 JDK。SDK 路徑可透過 `ANDROID_HOME` 或未納入版本管理的 `local.properties` 指定。

## 功能與規則

- 物品新增、詳細資訊、編輯、確認刪除、已用完／已丟棄及復原。
- 物品可有多個標籤。新增預選「其他」，選其他標籤不會取消「其他」。未選任何標籤儲存時補上「其他」。
- 自訂標籤圖示、顏色與拖曳排序；「其他」可排序，不可編輯或刪除。刪除標籤僅移除關聯，沒有剩餘標籤的物品補上「其他」。
- 名稱／備註文字搜尋、單選標籤、單選狀態及包含邊界的有效期限範圍，同時取交集；首頁預設使用中。
- 首頁「即將＋已過期」區的匯出圖示以彈窗列出七天內即將過期及已過期且使用中的物品，不受目前搜尋影響。每行為「名稱-有效期限-備註」，依到期日排序，右上複製圖示可複製全部文字。今天到期列入，八天後到期不列入；無備註保留空欄，多行備註合為單行。
- 日期以 115/10/29 的格式顯示，年制維持原規格；資料庫存公曆 ISO 日期，使用本機時區計算提醒。
- 提醒新增預設關閉。物品表單僅能選通知日期；預設通知日期與時間於 APP 設定調整。新物品繼承預設時間，編輯既有物品保留其時間。設定頁已移除手機系統設定入口。修改預設不改寫既有物品。
- 全域開關只改設定表與畫面可操作狀態，不改物品提醒設定。已用完／已丟棄不發送，復原後重新檢查。
- 過去時間、重開機、全域重新開啟會補發未送出的提醒；相同物品相同提醒時間只發一次。修改到期日、提前天數或時間後視為新的提醒。Android 通知權限未允許時不標記已發送。
- 使用 AlarmManager 非精確提醒，接受系統省電造成延遲。使用者強制停止 APP 後，必須再次開啟才能恢復排程；這是 Android 的系統限制。
- 設定列表新增「匯出 MCP 資料」，透過 Android 儲存視窗輸出全部物品與標籤的 JSON，包含所有狀態及期限，不受首頁篩選影響。

## 本機 MCP 加分功能

`mcp-server/` 提供唯讀工具 `get_expiring_items(days_ahead=7)`，讓 agent 查詢手動匯出的 JSON 快照。將 APP 匯出的 `items.json` 傳到 `mcp-server/data/items.json`；手機資料變更後重新匯出並替換，伺服器每次查詢會重新讀檔。

完整啟動、Codex 設定範例與作業展示步驟見 [MCP 操作說明](mcp-server/README.md)。伺服器不會即時連手機，也不修改手機資料；日期依匯出時區的查詢當天重新計算。

## 程式結構

- `MainActivity.kt`：Views 畫面及操作流程。
- `Store.kt`：內建 SQLite，對應 ERD 的 Stuff、Tag、Stuff_Tag、Setting，關聯更新採交易與外鍵。
- `Rules.kt`：日期、搜尋、排序與提醒計算。
- `ReminderReceiver.kt`：通知排程、開機／時間變更重排程與發送去重。

本機資料不使用網路同步；目前未提供資料庫備份與還原功能。解除安裝會移除資料。

## 驗證

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant tw.expirybook android.permission.POST_NOTIFICATIONS
adb shell am instrument -w tw.expirybook.test/tw.expirybook.StoreInstrumentation
```

單元測試比對規格搜尋資料、日期包含邊界、多標籤與跨年提醒計算。裝置測試請在測試用 API 33 以上模擬器執行。資料庫整合測試使用獨立的 integration-test.db；畫面及通知測試會在 APP 資料庫建立測試物品與標籤、暫時修改通知設定，完成後刪除測試資料並恢復設定，並驗證編輯草稿於 Activity 重建後仍保留。請使用乾淨的測試安裝，不要對日常資料執行此測試。
