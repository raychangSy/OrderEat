// 背景自動同步：週一～五 09:00–11:00（台北時間）每 5 分鐘打一次 syncToday，
// 發現新的開放中的團就跳桌面通知；同一團當天只通知一次。
import * as api from './api.js';

const ALARM = 'autoSync';
const PERIOD_MIN = 5;
const WINDOW = { from: 9 * 60, to: 11 * 60 }; // 分鐘，含頭含尾

// 沒有鬧鐘或間隔跟 PERIOD_MIN 不同（舊版裝的）就重建
function ensureAlarm() {
  chrome.alarms.get(ALARM, a => {
    if (a?.periodInMinutes !== PERIOD_MIN) chrome.alarms.create(ALARM, { delayInMinutes: 1, periodInMinutes: PERIOD_MIN });
  });
}

chrome.runtime.onInstalled.addListener(ensureAlarm);
chrome.runtime.onStartup.addListener(ensureAlarm);

/** 台北時間是否落在週一～五 09:00–11:00 */
function inWindow(now = new Date()) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Taipei', weekday: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .formatToParts(now).map(p => [p.type, p.value]),
  );
  if (parts.weekday === 'Sat' || parts.weekday === 'Sun') return false;
  const min = Number(parts.hour) * 60 + Number(parts.minute);
  return min >= WINDOW.from && min <= WINDOW.to;
}

chrome.alarms.onAlarm.addListener(async alarm => {
  if (alarm.name !== ALARM || !inWindow()) return;
  try {
    const data = await api.syncToday();
    await chrome.storage.local.set({ cache: data }); // popup 打開就是最新資料
    await notifyNew(data);
  } catch (e) {
    console.warn('[OrderEat] 自動同步失敗', e);
  }
});

/** 比對當天已通知過的團，只對新的開放中的團跳通知 */
async function notifyNew(data) {
  const { notified } = await chrome.storage.local.get('notified');
  const ids = notified?.date === data.date ? notified.ids : [];
  const fresh = data.sessions.filter(s => s.status === 'open' && !ids.includes(s.sessionId));
  if (!fresh.length) return;

  await chrome.storage.local.set({ notified: { date: data.date, ids: [...ids, ...fresh.map(s => s.sessionId)] } });
  const label = s => `${s.restaurantName}・${s.mealType}${s.openedBy ? `（${s.openedBy} 開團）` : ''}`;
  chrome.notifications.create(`open-${Date.now()}`, {
    type: 'basic',
    iconUrl: 'icons/icon128.png',
    title: fresh.length > 1 ? `有 ${fresh.length} 個新的團開放點餐` : '有新的團開放點餐',
    message: fresh.map(label).join('\n'),
  });
}

// 點通知 → 打開 popup（Chrome 127+ 才支援 openPopup，失敗就算了）
chrome.notifications.onClicked.addListener(id => {
  chrome.notifications.clear(id);
  chrome.action.openPopup?.().catch(() => {});
});
