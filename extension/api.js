// 呼叫 GAS 後端。畫面只依賴這裡匯出的函式，config.useMock 時改用 mock-api.js。
import { CONFIG } from './config.js';
import * as mock from './mock-api.js';

async function call(method, action, params = {}) {
  if (!CONFIG.apiUrl || !CONFIG.token) throw new Error('尚未設定 config.js 的 apiUrl／token');
  let res;
  if (method === 'GET') {
    const qs = new URLSearchParams({ action, token: CONFIG.token, ...params });
    res = await fetch(`${CONFIG.apiUrl}?${qs}`);
  } else {
    // text/plain 避免 CORS 預檢；GAS 端照樣從 postData.contents 讀 JSON
    res = await fetch(CONFIG.apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: JSON.stringify({ token: CONFIG.token, action, payload: params }),
    });
  }
  if (!res.ok) throw new Error(`連線失敗（HTTP ${res.status}）`);
  const body = await res.json();
  if (!body.ok) throw new Error(body.error || '未知錯誤');
  return body.data;
}

const real = {
  /** 今天開放中的團（含所有明細）＋ 菜單 ＋ 成員，一次取回 */
  syncToday: () => call('GET', 'syncToday'),

  /** 覆寫這團 + 這個人的明細；lines 為空 = 取消自己的訂單。回傳含整團最新明細 lines */
  addMyLines: (sessionId, member, lines) => call('POST', 'addMyLines', { sessionId, member, lines }),
};

const impl = CONFIG.useMock ? mock : real;
export const { syncToday, addMyLines } = impl;
