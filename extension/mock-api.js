// 假資料版 API（config.useMock = true 時使用），介面與 api.js 相同。
import { MOCK } from './mock.js';

const delay = (ms = 300) => new Promise(r => setTimeout(r, ms));
const clone = v => JSON.parse(JSON.stringify(v));
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Taipei' });

function sessionLines(sessionId) {
  return Object.entries(MOCK.linesBySessionMember)
    .filter(([k]) => k.startsWith(sessionId + '|'))
    .flatMap(([k, ls]) => ls.map(l => ({ member: k.slice(sessionId.length + 1), ...l })));
}

export async function syncToday() {
  await delay(600);
  const date = today();
  return clone({
    date,
    sessions: MOCK.openSessions.map(s => ({ ...s, date, status: 'open', lines: sessionLines(s.sessionId) })),
    history: MOCK.history,
    restaurants: MOCK.restaurants,
    menuItems: MOCK.menuItems,
    optionGroups: MOCK.optionGroups,
    members: MOCK.members.filter(m => m.active),
    syncedAt: new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Taipei' }),
  });
}

export async function addMyLines(sessionId, member, lines) {
  await delay(500);
  const key = sessionId + '|' + member;
  const saved = lines.map(l => ({ ...l, subtotal: l.qty * l.unitPrice }));
  if (saved.length) MOCK.linesBySessionMember[key] = clone(saved);
  else delete MOCK.linesBySessionMember[key];
  const all = sessionLines(sessionId);
  return clone({
    sessionId, member,
    lineCount: saved.length,
    total: saved.reduce((s, l) => s + l.subtotal, 0),
    sessionTotal: all.reduce((s, l) => s + l.subtotal, 0),
    lines: all,
  });
}
