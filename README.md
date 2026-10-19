# OrderEat 點餐系統

團體每天決定吃什麼、輪流點餐、記錄吃過哪間的 Android App。
一支手機直立當 **單機 POS** 輪流點，點完一次送出；後端是 **Google Sheets + Apps Script**（免費、不綁卡）。

## 功能

- **隨機選店**：分類篩選 → 隨機 → 不滿意重抽 → 確定；可「排除最近 N 天吃過的店」（N = 1–14）
- **點餐 POS**：成員頭像格（已點／未點）、品項列表、客製選項（單選／多選、必選、加價、可選數量）、數量與備註，也可手輸品項
- **確認送出**：依品項彙總、依人金額、總額；提示尚未點餐的成員；一鍵複製彙總文字
- **開單跟單**：App 開單後，其他人可用 [Chrome 擴充功能](#chrome-擴充功能跟單)各自跟單，App 再「結單並送出」
- **菜單照片**：現場拍照或從相簿挑，壓縮後上傳 Google Drive，一店一張，離線可看
- **Google 地圖／線上菜單**：從抽店結果或點餐頁開啟店家地圖、線上菜單
- **歷史紀錄**：依日期列出各團，可重開修改後覆蓋重送
- **成員管理**：App 內新增／改名／停用，自動備份到 Sheet
- **離線**：菜單快取在 Room；送出、成員同步、照片上傳失敗會排入佇列，由 WorkManager 在有網路時自動重試

收款、統計不在 App 內做：Sheet 的 `點餐明細` 有「已付款」勾選欄，`今日彙總`、`統計` 分頁用公式產生。

## Google Sheet

後端試算表：<https://docs.google.com/spreadsheets/d/16vsSFed2TgWrC6T91LhSmvThS3hQ1nGZqB0ESi0Y05I/edit?usp=sharing>

分頁：`餐廳`、`品項`、`客製選項`（手動維護菜單）／`成員`、`每日記錄`、`點餐明細`（App 寫入）／`今日彙總`、`統計`（公式）。欄位說明見 [`docs/SPEC.md`](docs/SPEC.md#google-sheet-結構)。

## 技術棧

| 類別 | 使用 |
|---|---|
| 語言／UI | Kotlin、Jetpack Compose、Material 3 |
| 架構 | Clean Architecture（`domain` / `data` / `presentation`）+ MVI |
| DI | Hilt |
| 導覽 | Navigation 3 |
| 本機資料 | Room、DataStore |
| 網路 | OkHttp + kotlinx.serialization |
| 背景工作 | WorkManager |
| 圖片 | Coil 3、Photo Picker、TakePicture |
| 測試 | JUnit、kotlinx-coroutines-test、Turbine |

建置環境：AGP 9.3、Kotlin 2.3、compileSdk / targetSdk 37、minSdk 29、Java 11。

## 專案結構

```
OrderEat/
├── app/src/main/java/com/symmetry/ordereat/
│   ├── domain/          # model、repository interface、usecase（純 Kotlin）
│   ├── data/
│   │   ├── local/       # Room DB、DAO、DataStore
│   │   ├── remote/      # Apps Script API、DTO
│   │   ├── repository/  # repository 實作
│   │   ├── mapper/      # DTO / Entity ↔ domain
│   │   ├── sync/        # WorkManager 上傳排程
│   │   └── photo/       # 菜單照片壓縮
│   ├── presentation/    # 各畫面 Screen + ViewModel（MVI）、navigation
│   ├── di/              # Hilt module
│   └── ui/theme/
├── apps-script/
│   ├── Code.gs          # Google Apps Script 後端
│   └── DevSeed.gs       # 開發用測試資料（test_ 開頭的團）
├── extension/           # Chrome 擴充功能（Manifest V3，跟單用）
└── docs/
    ├── SPEC.md                   # 規格與 API 說明
    ├── SETUP_SHEET.md            # Google Sheet 後端建立教學
    ├── EXTENSION_INSTALL_DEV.md  # 擴充功能本地安裝（開發者）
    ├── EXTENSION_INSTALL_USER.md # 擴充功能安裝（一般使用者）
    └── EXTENSION_PLAN.md         # 擴充功能規劃與決策
```

MVI：每個畫面有 `State`（immutable）、`Intent`（sealed）、`Effect`（一次性，Channel），共用 `presentation/mvi/MviViewModel`。

## 快速開始

### 1. 建立 Google Sheet 後端

照 [`docs/SETUP_SHEET.md`](docs/SETUP_SHEET.md) 操作（約 15 分鐘）：建立試算表 → 貼上 `apps-script/Code.gs` → 執行 `setup()` → 部署為網頁應用程式，取得 **Web App 網址** 與 **API Token**。

### 2. 設定 `local.properties`

專案根目錄的 `local.properties`（不進 git）加入：

```properties
ORDEREAT_SCRIPT_URL=https://script.google.com/macros/s/你的ID/exec
ORDEREAT_TOKEN=你的Token
```

建置時會寫入 `BuildConfig.SCRIPT_URL`、`BuildConfig.API_TOKEN`。

### 3. 建置與執行

用 Android Studio 開啟專案直接執行，或：

```bash
./gradlew installDebug
```

第一次開 App 後，到首頁或設定按「同步」下載菜單（App 開啟時不會自動同步）。

### 測試

```bash
./gradlew testDebugUnitTest        # 單元測試（domain usecase）
./gradlew connectedDebugAndroidTest # 需連接裝置或模擬器
```

## Chrome 擴充功能（跟單）

`extension/` 是給團員在電腦上各自跟單的 Chrome 擴充功能（Manifest V3），不上架商店，用「載入未封裝項目」安裝。Edge、Brave 等 Chromium 瀏覽器也適用。

1. 建立設定檔（`config.js` 含 token，不進 git）：

   ```bash
   cp extension/config.example.js extension/config.js
   ```

   填入 `apiUrl`（Web App 網址，結尾 `/exec`）與 `token`（試算表選單「OrderEat → 顯示 API Token」）；`useMock: true` 可用假資料試看畫面。
2. 開啟 `chrome://extensions` → 右上角開啟「開發人員模式」→「載入未封裝項目」→ 選 `extension/` 資料夾。
3. 點瀏覽器右上角的 OrderEat 圖示開始點餐。

詳細步驟與常見問題：

- 開發者：[`docs/EXTENSION_INSTALL_DEV.md`](docs/EXTENSION_INSTALL_DEV.md)
- 一般使用者（拿 zip 安裝）：[`docs/EXTENSION_INSTALL_USER.md`](docs/EXTENSION_INSTALL_USER.md)
- 設計決策與 API：[`docs/EXTENSION_PLAN.md`](docs/EXTENSION_PLAN.md)

### 測試資料

[`apps-script/DevSeed.gs`](apps-script/DevSeed.gs) 貼到 Apps Script 專案另一個檔案，從編輯器執行：

- `seedTestSession()`：開一個今天的測試團並幫幾個人點好餐
- `clearTestSessions()`：刪除所有 `test_` 開頭的測試團

## 後端 API 概要

Apps Script 回應一律 HTTP 200，格式為 `{ ok: true, data }` 或 `{ ok: false, error }`，需檢查 `ok`。所有請求都要帶 `token`。

| Action | 方法 | 用途 |
|---|---|---|
| `ping` | GET | 測試連線 |
| `bootstrap` | GET | 下載餐廳、品項、客製選項、成員、近 30 天紀錄 |
| `submitSession` | POST | 送出／覆蓋一團的訂單（可帶 `status: open / closed`） |
| `syncMembers` | POST | 備份成員名單 |
| `uploadPhoto` | POST | 上傳菜單照片到 Drive |
| `syncToday`、`addMyLines` 等 | GET / POST | Chrome 擴充功能跟單用，見 [`docs/EXTENSION_PLAN.md`](docs/EXTENSION_PLAN.md) |

完整欄位見 [`docs/SPEC.md`](docs/SPEC.md#apps-script-api)。

> 修改 `Code.gs` 後要在 Apps Script「管理部署作業」更新成**新版本**，不要新增部署，否則網址會變。

## 文件

- [Google Sheet 後端](https://docs.google.com/spreadsheets/d/16vsSFed2TgWrC6T91LhSmvThS3hQ1nGZqB0ESi0Y05I/edit?usp=sharing)
- [規格書 SPEC.md](docs/SPEC.md)：已確定決策、Sheet 結構、畫面流程、API
- [Sheet 建立教學 SETUP_SHEET.md](docs/SETUP_SHEET.md)：後端部署步驟與常見問題
- [擴充功能本地安裝 EXTENSION_INSTALL_DEV.md](docs/EXTENSION_INSTALL_DEV.md)：開發者從 repo 安裝、設定 `config.js`
- [擴充功能安裝教學 EXTENSION_INSTALL_USER.md](docs/EXTENSION_INSTALL_USER.md)：給一般使用者的 zip 安裝步驟
- [擴充功能規劃 EXTENSION_PLAN.md](docs/EXTENSION_PLAN.md)：個人點餐（新團）決策與 API
