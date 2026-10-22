# Chrome 擴充功能：個人點餐（新團）

## 決策

1. **Sheet 是唯一資料來源**。新團的明細由每個人各自寫入，不由手機整團覆寫。
2. **認證**：共用現有的 `API_TOKEN`（已知風險：拿到擴充功能就能呼叫所有 action）。
3. **重複點餐**：選完身份後帶入自己在這團的舊明細，可以改；送出時覆寫「這團 + 這個人」的明細。購物車可放多品項，記住上次選的身份。
4. **新舊流程共存**：
   - 舊團：手機照原流程用 `submitSession`，程式碼完全不動。
   - 新團：手機用 `openSession` 開團、`closeSession` 截止；擴充功能**只處理新團**。
   - 手機不可以對新團呼叫 `submitSession`（會整團覆寫）。
   - GAS 只新增 function 跟 switch case；「每日記錄」在第 I 欄加 `狀態`，留空 = 舊團。

## API（`apps-script/Code.gs`）

「每日記錄」新增兩欄：I `狀態`（空 = 舊團／開放／截止）、J `開團人`。

| action | 方法 | 用途 |
|---|---|---|
| `syncToday` | GET | 擴充功能唯一的讀取 API：今天開放中的團 `sessions` ＋ 近 7 天非開放中的團 `history`（已截止新團＋手機送出舊團，皆含明細）＋ 菜單 ＋ 成員 |
| `listOpenSessions` | GET | 狀態=開放的團，附 `memberCount`、`total` |
| `getSessionLines` | GET `sessionId` | 團資訊 + 所有人的明細（擴充功能自己過濾出自己的） |
| `addMyLines` | POST `{ sessionId, member, lines }` | 覆寫這團 + 這個人的明細，空陣列 = 取消；回傳整團最新 `lines`；小計伺服器重算、保留已付款、更新總額；團已截止或舊團會拒絕 |
| `openSession` | POST `{ session }` | 手機開團 |
| `closeSession` / `reopenSession` | POST `{ sessionId }` | 手機截止／重開 |

`bootstrap` 的 `recentSessions` 多了 `status`、`openedBy` 兩個欄位，手機據此對新團隱藏「送出」按鈕（不可呼叫 `submitSession`）。

擴充功能：`extension/config.js`（不進版控）填 `apiUrl`、`token`；`useMock: true` 改回假資料。

## 擴充功能的快取

- `syncToday` 的結果存在 `chrome.storage.local` 的 `cache` 鍵。
- 開 popup 時直接讀快取；沒有快取或日期不是今天才自動同步。其他時候按右上角 ↻ 手動同步，只在團列表和總覽頁顯示。
- 送出後用 `addMyLines` 回傳的整團明細更新快取，不另外同步。
- 背景自動同步（`background.js`）：週一～五 09:00–11:00（台北時間）每 5 分鐘打一次 `syncToday` 並寫回 `cache`；有新的開放中的團就跳桌面通知。已通知過的團 id 存在 `notified` 鍵（`{ date, ids }`，跨日重置），同一團只通知一次。點通知會嘗試開 popup（Chrome 127+）。
- 別人新點的單要按 ↻ 才看得到。因為 `addMyLines` 只覆寫自己的明細，就算畫面舊了也不會蓋到別人的單。
