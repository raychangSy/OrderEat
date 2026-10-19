# OrderEat 點餐系統 — SPEC

## Context
團體每天要決定吃什麼、輪流點餐、記錄吃過哪間。現況：空白 Compose 專案（`com.symmetry.ordereat`，AGP 9.3、minSdk 29、compileSdk 36），無既有程式。
狀態：規格定稿 v1（2026-10-07），尚未實作。

## 已確定決策
| 項目 | 決定 |
|---|---|
| 使用模式 | **單機 POS**：一支 Android 手機**直立式**輪流點，點完一次送出 |
| 後端 | **Google Sheets + Apps Script Web App**（免費、不綁卡） |
| 閱讀 | Sheet 只有團主自己看（不共享）；其他人看 POS 手機 |
| 菜單維護 | Sheet 結構化（品項/價格/客製選項）＝表單；每店 **1 張**菜單照片當參考；無品項的店可看照片手輸 |
| 成員 | **App 內新增/編輯**成員名單，同步備份到 Sheet；可臨時加訪客 |
| 訂單 | 價格、總額、客製選項（大小/甜度/冰塊/加料）；**不算外送費、不做折扣分攤** |
| 收款 | **App 不做**；Sheet `OrderLines` 留「已付款」勾選欄，自己在 Sheet 勾 |
| Random | 分類篩選 → 隨機 → 不滿意重抽 → 「確定」鈕；可開「排除最近 N 天吃過的店」 |
| 修改 | 送出後可改，以 `sessionId` 覆蓋重送 |
| 每日團數 | 可多團（午餐、飲料…各自獨立） |
| 統計 | Sheet 公式做（`Stats` tab），不進 App |

## Google Sheet 結構
實作：`apps-script/Code.gs`（`setup()` 自動建立），建立步驟見 `docs/SETUP_SHEET.md`。
- `餐廳`：id（自動）, 店名, 分類（逗號分隔多 tag）, 電話, 地址, 備註, 停用☐, 菜單照片, 照片檔ID, Google地圖（商家連結，選填；空白時 App 用店名＋地址搜尋）, 連結選單（線上菜單／點餐網址，選填）
- `品項`：id（自動）, 餐廳（店名下拉）, 品名, 價格, 客製選項（選項名稱，逗號分隔）, 停用☐
- `客製選項`：id（自動）, 餐廳（**留空＝通用**）, 選項名稱, 類型（單選/多選）, 必選☐, 選項內容（`正常|半糖|大杯+10|去冰-5`）, 可選數量（多選用：空白＝不限；必選＋N＝剛好 N 個；非必選＋N＝最多 N 個）
- `成員`：id, 名字, 停用☐, 更新時間（App 為主，Sheet 為備份；空名單拒絕覆蓋）
- `每日記錄`：團ID, 日期, 餐別, 餐廳ID, 店名, 總額（server 依明細加總）, 備註, 送出時間, 狀態（空白＝舊團、開放、截止）, 開團人
- `點餐明細`：團ID, 日期, 餐別, 店名, 點餐人, 品名, 客製, 數量, 單價, 小計, 備註, **已付款☐（手動勾）**, 來源（App／插件）
- `今日彙總`（公式）：依品項（打電話用）、依人應付、今日明細
- `統計`（公式）：本月各店次數、本月每人花費、各店最後一次吃、本月總花費
- 日期一律存 `yyyy-MM-dd` 純文字；時區 Asia/Taipei
- 用「停用」勾選取代刪除；品項以店名對應餐廳，bootstrap 時轉成 id

> 覆蓋重送：同 sessionId 中**來源 App（或空白）**的舊明細刪除後重寫，插件跟單的明細保留；「已付款」依點餐人保留。

## 開單跟單（App 開單／結單，Chrome 插件跟單）
- 點餐頁上方「開單」→ 輸入開單人 → `submitSession`（`status:'open'`）：送出手機上的單，`每日記錄.狀態=開放`。只有今天的團能開（插件只看今天）
- 開單中：其他人用 Chrome 插件（`/Users/raychang/Downloads/extension`，不在本 repo）`syncToday` 看到這團、`addMyLines` 覆寫自己的明細（來源＝插件）；手機也能繼續輪流點
- 「同步跟單」＝再送一次 `status:'open'`，回傳整團明細，插件的明細存回手機（唯讀，掛到同名成員）
- 確認送出頁按「結單並送出」→ `status:'closed'`，`狀態=截止`，插件不能再加；之後修改重送維持已結單
- 限制：同一人不要手機跟插件都點（兩邊各自保留，會重複）；插件改自己的單會覆蓋該人在手機點的明細

## 菜單照片
- **來源**：App 內兩種方式 — ① 現場拍照（`ActivityResultContracts.TakePicture`）② 從手機相簿挑（Android Photo Picker，免權限）
- **處理**：App 端縮小至長邊 1280px、JPEG 品質 75%（約 150–300KB）→ base64 上傳
- **存放**：Apps Script 存進 Drive `OrderEat 菜單照片` 資料夾，檔案設「知道連結者可檢視」，連結寫回 `Restaurants.menuPhotoUrl`
- **一店一張**：上傳新照片時舊檔移到垃圾桶，欄位覆蓋
- **App 顯示**：Coil 載入並快取到本機，離線也看得到；可放大
- 也可直接在電腦把照片丟進 Drive 再貼連結到 Sheet（備用方式）

## Random 選店
- 分類 chip 篩選（多選），篩選後無店時提示
- 開關「排除最近吃過」，**開關下方顯示說明**：「開啟後，最近 N 天內吃過的店不會被抽到（目前 N = 3 天）」；N 可調 1–14，存 DataStore
- 若排除後無店可抽 → 提示並詢問是否忽略排除
- 重抽不限次數；按「確定」才建立/綁定團

## Apps Script API
回應統一 `{ ok: true, data }` / `{ ok: false, error }`（HTTP 一律 200，需看 `ok`）。
- `GET ?action=ping&token=`
- `GET ?action=bootstrap&token=` → `{ restaurants, menuItems, optionGroups, members, recentSessions(30 天), serverTime }`
  - optionGroup：`{ id, restaurantId('' = 通用), name, type: single|multi, required, choices: [{name, price}], maxSelect(single=1, multi 0=不限) }`
- `POST { token, action:'submitSession', payload:{ session:{sessionId,date,mealType,restaurantId,restaurantName,note,status:open|closed|''(維持原狀態),openedBy}, lines:[{member,itemName,options,qty,unitPrice,subtotal,note}] } }` → `{ sessionId, lineCount, total, submittedAt, status, lines:[{…, source: app|extension}] }`
- `GET ?action=syncToday&token=`（插件）→ `{ date, sessions:[{sessionId,date,mealType,restaurantId,restaurantName,note,openedBy,status:'open',lines}], restaurants, menuItems, optionGroups, members(啟用), syncedAt }`
- `GET ?action=listOpenSessions` / `GET ?action=getSessionLines&sessionId=` / `POST openSession|closeSession|reopenSession`（插件端另寫的 API，App 目前改用 submitSession 帶 status）
- `POST { token, action:'addMyLines', payload:{ sessionId, member, lines:[{itemName,options,qty,unitPrice,note}] } }`（插件；只限開放中；小計由後端算）→ `{ sessionId, member, lineCount, total, sessionTotal, lines }`
- `POST { token, action:'syncMembers', payload:{ members:[{id,name,active,updatedAt}], allowEmpty? } }`
- `POST { token, action:'uploadPhoto', payload:{ restaurantId, base64, mimeType? } }` → `{ url, fileId }`
- 寫入類 action 用 `LockService` 鎖
- 部署「所有人」+ token 驗證（token 放 `local.properties` → BuildConfig：`ORDEREAT_SCRIPT_URL`、`ORDEREAT_TOKEN`）；Sheet 不共享
- Apps Script 回 302 轉址，OkHttp 預設 follow（POST→GET）即可

## Android 架構（MVI + Clean Architecture）
- 套件分層：`domain`（model、repository interface、usecase）/ `data`（Room、DataStore、remote API、repository impl、mapper）/ `presentation`（feature 畫面 + ViewModel）
- MVI：每畫面 `State`（immutable data class）/ `Intent`（sealed）/ `Effect`（一次性，Channel）；共用 `MviViewModel<S,I,E>` base + reducer
- 技術：Hilt、Room、DataStore、Retrofit/OkHttp + kotlinx.serialization、Coil、Navigation 3、WorkManager、Photo Picker / TakePicture
- 方向：鎖定直立（`screenOrientation="portrait"`）
- 離線：master data 快取 Room；送出/成員同步/照片上傳失敗進 pending queue，WorkManager 有網路自動重試
- UseCase：`SyncMasterDataUseCase`、`PickRandomRestaurantUseCase(filters, excludeDays)`、`ManageMembersUseCase`、`CreateSessionUseCase`、`AddOrderLineUseCase`、`SubmitSessionUseCase`、`UploadMenuPhotoUseCase`、`GetHistoryUseCase`

## 畫面流程
1. **首頁**：今日各團（狀態：點餐中/已送出）+「開新團」（選 mealType）；「同步」按鈕手動下載菜單（**開 App 不自動同步**，只有按同步才向 Sheet 抓資料）
2. **選餐廳**：分類篩選 + 排除開關（含說明）→ 隨機（動畫）→ 重抽 → 確定；亦可手動指定
3. **餐廳/菜單**：菜單照片（放大）、拍照/相簿更換照片；「Google 地圖」按鈕開外部地圖 App／瀏覽器看商家餐點照片（抽店結果卡、點餐頁上方）
4. **點餐 POS**：成員頭像格（標示已點/未點）→ 品項列表 → 客製選項 bottom sheet → 數量/備註；手輸品項模式
5. **確認送出**：按品項彙總 + 按人金額 + 總額 + 團備註；**有成員未點時提示「XX、YY 尚未點餐」**（可略過）→ 送出 → 「複製彙總文字」
6. **歷史**：依日期列出團、店家、總額、備註；可重開修改重送
7. **成員管理**：新增/改名/停用
8. **設定**：同步 master data、排除天數 N、Script URL/token

## Sheet 建立（實作時提供逐步教學 `docs/SETUP_SHEET.md`）
1. 建 Google Sheet、依上方建立各 tab 與欄位標題（我提供範本）
2. 擴充功能 → Apps Script，貼上我提供的程式碼
3. 設定 token（指令碼屬性）
4. 部署為網頁應用程式（執行身分：我；存取：任何人），授權
5. 複製 Web App URL 填入 `local.properties`
6. 用 curl / 瀏覽器測 bootstrap
7. 在 Sheet 輸入第一間餐廳與品項，App 同步驗證

## Verification（實作階段）
- domain/usecase、reducer 單元測試（Random 篩選與排除天數、金額計算、upsert 保留 paid）
- Room DAO instrumented test
- curl 打 bootstrap / submitSession / uploadPhoto 驗證 Sheet 與 Drive
- 實機流程：新增成員 → 開團 → 抽店（開排除）→ 3 人點餐含客製 → 未點提示 → 送出 → Sheet 看到明細/彙總 → Sheet 勾 paid → 修改重送 paid 仍在 → 歷史有記錄
- 拍照/相簿換菜單照片，Drive 只剩一張
- 斷網送出 → 恢復網路後自動補送
