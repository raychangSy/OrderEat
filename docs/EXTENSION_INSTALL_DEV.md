# OrderEat 擴充功能：本地安裝教學

Chrome 擴充功能（Manifest V3）不上架商店，用「載入未封裝項目」從本機資料夾安裝。適用 Chrome、Edge、Brave 等 Chromium 瀏覽器。

## 1. 取得檔案

二擇一：

- **從 repo**：clone 專案，擴充功能在 `extension/` 資料夾。
- **從 zip**：解壓縮 `extension.zip`，得到 `extension/` 資料夾。

> 解壓縮後的資料夾請放在固定位置（例如 `~/Apps/ordereat-extension/`），之後**不要移動或刪除**，Chrome 是直接讀這個資料夾，移走就會失效。

確認資料夾裡直接看得到 `manifest.json`（不是再包一層資料夾）。

## 2. 設定 `config.js`

`config.js` 含 token，不進版控，要自己建立：

```bash
cp extension/config.example.js extension/config.js
```

編輯 `extension/config.js`：

```js
export const CONFIG = {
  apiUrl: 'https://script.google.com/macros/s/XXXXXXXX/exec',
  token: '你的 API Token',
  useMock: false,
};
```

| 欄位 | 從哪裡拿 |
|---|---|
| `apiUrl` | GAS 編輯器 → 部署 → 管理部署作業 → 網頁應用程式 URL（結尾是 `/exec`） |
| `token` | 試算表上方選單「OrderEat → 顯示 API Token」 |
| `useMock` | `true` = 用 `mock.js` 假資料、不打 API（開發或試看畫面用） |

> 拿到 zip 的話 `config.js` 可能已經填好，確認內容正確即可。
> ⚠️ token 可呼叫所有 API，不要把 `config.js` 或含它的 zip 傳到公開地方。

## 3. 載入到 Chrome

1. 網址列輸入 `chrome://extensions`（Edge 是 `edge://extensions`）。
2. 打開右上角 **開發人員模式**。
3. 點 **載入未封裝項目**。
4. 選 `extension/` 資料夾（含 `manifest.json` 那層）。
5. 清單出現「OrderEat 個人點餐」即安裝完成。

建議點工具列的拼圖圖示 🧩 → 把 OrderEat **釘選**到工具列。

## 4. 第一次使用

1. 點工具列的 OrderEat 圖示開啟 popup。
2. 第一次開會自動同步今天的團（之後按右上角 ↻ 手動同步）。
3. 選團 → 選自己的身份（會記住）→ 點餐 → 送出。

## 5. 更新版本

1. 用新版檔案覆蓋原本的資料夾（**保留你的 `config.js`**）。
   - 從 repo：`git pull` 即可。
2. 到 `chrome://extensions`，在 OrderEat 卡片上按 **重新載入** ↻。

只改 `config.js` 也要按重新載入。

## 6. 常見問題

| 狀況 | 原因／解法 |
|---|---|
| 載入時說「找不到資訊清單檔案」 | 選錯層，要選含 `manifest.json` 的那個資料夾 |
| popup 顯示「尚未設定 config.js 的 apiUrl／token」 | 沒建 `config.js` 或欄位空白，見步驟 2 |
| popup 一片空白 | 通常是 `config.js` 不存在（`api.js` import 失敗）。在 popup 上按右鍵 → 檢查，看 Console 錯誤 |
| 「連線失敗（HTTP 4xx/5xx）」 | `apiUrl` 錯誤、GAS 部署沒設成「任何人」可存取、或部署版本已失效 |
| 回傳 token 錯誤 | 試算表那邊按過「重新產生 Token」，重新複製填入後重新載入 |
| 看不到別人剛點的單 | 快取機制，按 ↻ 同步 |
| 看不到某個團 | 擴充功能只顯示手機用「開團」建立的新團，舊流程的團不會出現 |
| 重開 Chrome 後跳出「停用開發人員模式擴充功能」提示 | 正常現象，按關閉或保留即可，不影響使用 |
| 擴充功能不見／失效 | 資料夾被移動或刪除，重新做步驟 3 |

## 移除

`chrome://extensions` → OrderEat 卡片 → **移除**。本地快取（`chrome.storage.local`）會一起清掉。
