# 有效期限簿 MCP（手動快照版）

APP 的「快過期ㄉ東東」功能封裝成 agent 可呼叫的唯讀工具。MCP 在電腦本機透過 stdio 執行，不需要架雲端主機，也不直接連手機。官方 Python SDK 固定為 `mcp==2.3.0`。

## 1. 從 APP 匯出

1. 開啟 APP 的「設定 → 匯出 MCP 資料」。
2. 在 Android 的儲存視窗選擇位置，檔名用 `items.json`。
3. 用 USB 或既有檔案傳輸方式，把檔案放到專案的：
   `\expiry-date-app\mcp-server\data\items.json`。
4. 以後手機資料變動，就重新匯出並替換同一個檔案；每次工具呼叫都會重新讀檔，不需重啟伺服器。請完成檔案複製後再查詢，避免讀到傳輸中的半份檔案。


JSON 包含**全部狀態、全部期限的物品及標籤**，不受首頁搜尋或篩選影響。首頁文字清單匯出仍保留，請勿把那份文字當作 MCP JSON。這是查詢用快照，目前沒有將 JSON 匯入／還原 APP 的功能。

## 2. 準備 Python

需要 Python 3.11 以上；以下為 Windows PowerShell，從本資料夾執行。這台開發電腦已建立專用 `.venv`，換電腦才需要重建環境。

```powershell
cd C:\Users\sophi\expiry-date-app\mcp-server
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

若沒有 `py` 指令，可將第一個指令的 `py -3` 換成已安裝的 `python`。`.venv` 不應提交版本管理。

## 3. 先驗證檔案與 MCP

```powershell
.\.venv\Scripts\python.exe server.py --check
.\.venv\Scripts\python.exe demo_client.py --data .\data\items.json
```

第一個指令檢查格式、筆數、時區與匯出時間；第二個會真的啟動 MCP 子程序，執行工具發現與 `tools/call`，顯示結果。`demo_client.py` 是協定測試客戶端，沒有呼叫語言模型，也不需要 API key。

預設資料位置固定在 `mcp-server/data/items.json`，與目前終端機所在目錄無關。若要改位置，兩個指令都可指定 `--data "完整路徑"`。

## 4. 接到 agent

### Codex

依 [OpenAI 官方 MCP 說明](https://developers.openai.com/codex/mcp/)，可在 Codex 的 `config.toml` 設定 stdio 伺服器。請把 [codex-config.example.toml](codex-config.example.toml) 的區段合併到自己的設定檔，保留其他已有設定；專案搬家時要同步調整三個完整路徑。此專案沒有自動修改你的 Codex 設定。

若使用 Codex CLI，也可執行：

```powershell
codex mcp add expiry_book --env PYTHONUTF8=1 -- C:/Users/sophi/expiry-date-app/mcp-server/.venv/Scripts/python.exe C:/Users/sophi/expiry-date-app/mcp-server/server.py --data C:/Users/sophi/expiry-date-app/mcp-server/data/items.json
codex mcp list
```

兩種方式擇一。儲存設定後重新載入使用中的客戶端／工作階段，確認工具列表出現 `get_expiring_items`；是否需要重新啟動依客戶端版本而定。

### 其他支援 stdio 的 MCP 客戶端

對支援 `mcpServers` JSON 格式的客戶端，可參考 [mcp-client.example.json](mcp-client.example.json)。若客戶端提供設定表單，分別填入 Python 完整路徑、`server.py` 路徑與 `--data` 參數。伺服器程序通常由客戶端自動啟動。

只執行 `python server.py` 時，它會等候 MCP 訊息；沒有一般互動選單是正常現象。不要向 stdio 伺服器直接輸入自然語言。

連好後可對 agent 說：

> 請呼叫 expiry_book 的 get_expiring_items，列出七天內即將過期及已過期、仍使用中的物品。

## 工具契約

`get_expiring_items(days_ahead=7)`，輸入為 0–365 的整數。0 表示包含今天及所有已過期物品；7 包含第七天，不含第八天。只取 `in_use`，排除 `used_up` 與 `discarded`。

依快照 `timezone` 的**查詢當天**重新計算，而不是以匯出當天或電腦的其他時區計算。物品狀態則停留在匯出當下。回傳：

- `exported_at`、`timezone`、`as_of`、`cutoff_date`、`days_ahead`。
- `count`、`total_items_in_snapshot`、`items`（含剩餘天數、狀態、標籤、備註）。
- `text`：每行「名稱-有效期限-備註」，日期維持 APP 的年制；名稱與備註中的換行合為空格。

工具不寫入資料檔、不修改手機、沒有自訂檔案路徑參數供 agent 任意讀檔。快照含你匯出的物品資料；應只連接你願意提供這些資料的 agent。

## 格式 v1

頂層欄位：`schema_version: 1`、`app: "有效期限簿"`、`exported_at`（含時區的 ISO 時間）、`timezone`（例如 `Asia/Taipei`）、`tags`、`items`。

| 陣列 | 欄位 |
|---|---|
| tags | id、name、icon、color（#RRGGBB）、sort_order |
| items | id、name、expiry_date、created_date（YYYY-MM-DD 公曆）、status、note、tag_ids、reminder |
| reminder | enabled、days_before、minute_of_day |

`status` 固定為 `in_use`／`used_up`／`discarded`。不要用畫面上的年制日期取代 JSON 公曆日期。缺檔、損壞 JSON、未知版本、無效日期或標籤關聯會回傳清楚的工具錯誤，重新放入正確檔案即可再查詢。

## 作業展示

1. APP 新增一筆七天內到期的物品，再保留一筆八天後到期及一筆已用完的物品。
2. 在設定匯出 JSON，傳到指定位置。
3. 讓 agent 呼叫工具，展示工具名稱、參數、回傳物品及匯出時間。
4. 在 APP 將第一筆改為已用完，重新匯出並替換 JSON。
5. 不重啟 MCP，再呼叫同一工具，確認那筆已從結果移除。

沒有手機時，可另外建立**明確標示為示範**的六筆資料：

```powershell
.\.venv\Scripts\python.exe make_demo_snapshot.py
.\.venv\Scripts\python.exe demo_client.py --data .\data\items.demo.json
```

示範檔不會冒充手機資料，也不會覆蓋已存在的檔案；若檔案已存在，可用 `--output` 指定另一個檔名。

## 測試

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

包含日期邊界、狀態排除、文字格式、時區、缺檔、損壞資料，以及真實 MCP stdio 子程序的發現、查詢、檔案替換、參數驗證與錯誤恢復。

SDK 參考：[官方 Python SDK](https://github.com/modelcontextprotocol/python-sdk)、[工具與結構化回傳](https://py.sdk.modelcontextprotocol.io/servers/tools/)。
