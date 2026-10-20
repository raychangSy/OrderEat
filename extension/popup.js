import * as api from './api.js';

// 流程：團列表 → 訂單總覽 →（你是誰）→ 菜單 → 品項（客製）→ 購物車 → 送出後回總覽
const state = {
  screen: 'sessions',
  data: null, // 本地快取：syncToday 的回傳 { date, sessions[{...lines}], menuItems, optionGroups, members, syncedAt }
  syncing: false,
  session: null,
  members: [],
  member: '',
  menu: { items: [], groups: [] },
  cart: [],
  draft: null, // 正在編輯的品項 { item, picks: {groupId: [choiceIndex]}, qty, note, editIndex }
  memberQuery: '',
  sending: false,
};

const $view = document.getElementById('view');
const $footer = document.getElementById('footer');
const $title = document.getElementById('title');
const $step = document.getElementById('step');
const $back = document.getElementById('back');
const $sync = document.getElementById('sync');

const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = n => '$' + Number(n).toLocaleString();
const cartTotal = () => state.cart.reduce((s, l) => s + l.subtotal, 0);

// ───────── 本地儲存（擴充功能用 chrome.storage.local，直接開 html 預覽時退回 localStorage） ─────────
const store = {
  async get(key) {
    if (globalThis.chrome?.storage?.local) return (await chrome.storage.local.get(key))[key];
    try { return JSON.parse(localStorage.getItem(key)); } catch { return null; }
  },
  async set(key, val) {
    if (globalThis.chrome?.storage?.local) return chrome.storage.local.set({ [key]: val });
    try { localStorage.setItem(key, JSON.stringify(val)); } catch { /* 預覽模式忽略 */ }
  },
};

const todayStr = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Taipei' });

// ───────── 導覽 ─────────
const BACK = { overview: 'sessions', who: 'overview', menu: 'overview', item: 'menu', cart: 'menu' };
// 點餐中（菜單／客製／購物車）不給同步，避免購物車跟著被換掉
const SYNCABLE = { sessions: true, overview: true };

function go(screen) {
  state.screen = screen;
  render();
}

$back.addEventListener('click', () => {
  if (state.screen === 'item') state.draft = null;
  go(BACK[state.screen]);
});

$sync.addEventListener('click', () => sync());

function frame(title, step, footerHtml) {
  $title.textContent = title;
  $step.textContent = step || '';
  $back.hidden = !BACK[state.screen];
  $sync.hidden = !SYNCABLE[state.screen];
  $sync.disabled = state.syncing;
  $sync.classList.toggle('spin', state.syncing);
  $footer.hidden = !footerHtml;
  $footer.innerHTML = footerHtml || '';
}

function render() {
  ({ sessions: renderSessions, overview: renderOverview, who: renderWho, menu: renderMenu, item: renderItem, cart: renderCart })[state.screen]();
}

// ───────── 同步 ─────────
async function init() {
  state.data = await store.get('cache');
  // 沒有快取或快取不是今天的 → 自動同步一次；否則直接用快取，按 ↻ 才打 API
  if (!state.data || state.data.date !== todayStr() || !state.data.history) return sync();
  render();
}

async function sync() {
  if (state.syncing) return;
  state.syncing = true;
  if (!state.data) {
    frame('OrderEat', '');
    $view.innerHTML = '<div class="loading">同步中…</div>';
  } else {
    render();
  }
  try {
    state.data = await api.syncToday();
    await store.set('cache', state.data);
    state.flash = '';
    if (state.screen === 'overview') {
      const fresh = [...state.data.sessions, ...state.data.history].find(x => x.sessionId === state.session.sessionId);
      if (fresh) enterSession(fresh);
      else { state.screen = 'sessions'; state.listFlash = '這團已截止或被刪除'; }
    }
  } catch (e) {
    state.listFlash = '';
    state.syncError = e.message;
  } finally {
    state.syncing = false;
  }
  render();
  state.syncError = '';
}

const sessionsOf = () => (state.listTab === 'history' ? state.data?.history : state.data?.sessions) || [];
const STATUS_LABEL = { open: '開放中', closed: '已截止', submitted: '已送出' };
const shortDate = d => d.slice(5).replace('-', '/');
const sessionTotal = s => s.lines.reduce((t, l) => t + l.subtotal, 0);
const sessionMembers = s => new Set(s.lines.map(l => l.member)).size;
const syncedLabel = () => (state.data?.syncedAt ? `${state.data.syncedAt.slice(11, 16)} 同步` : '');

// ───────── 1. 團列表（開放中／近 7 天） ─────────
function renderSessions() {
  const history = state.listTab === 'history';
  frame(history ? '近 7 天的訂單' : '今天開放中的團', syncedLabel());
  const msg = state.syncError
    ? `<p class="error">同步失敗：${esc(state.syncError)}</p>`
    : state.listFlash ? `<p class="hint">${esc(state.listFlash)}</p>` : '';
  state.listFlash = '';
  const tabs = `
    <div class="tabs">
      <button class="${history ? '' : 'on'}" data-list="open">開放中 ${state.data ? state.data.sessions.length : ''}</button>
      <button class="${history ? 'on' : ''}" data-list="history">近 7 天</button>
    </div>`;
  let body;
  const sessions = sessionsOf();
  if (!state.data) {
    body = msg ? '' : '<div class="empty">尚未同步，按右上角 ↻</div>';
  } else if (!sessions.length) {
    body = history
      ? '<div class="empty">近 7 天沒有已結單的訂單</div>'
      : '<div class="empty">今天沒有開放中的團<br><small>有人開團後按右上角 ↻ 同步</small></div>';
  } else {
    body = `<div class="list">${sessions.map((s, i) => `
      <button class="card" data-i="${i}">
        <div class="row"><span class="name">${esc(s.restaurantName)}</span><span class="tag">${esc(s.mealType)}</span></div>
        <div class="row sub">
          <span>${history ? `${shortDate(s.date)}・${STATUS_LABEL[s.status] || ''}` : ''}${s.openedBy ? `${history ? '・' : ''}${esc(s.openedBy)} 開團` : ''}</span>
          <span>${sessionMembers(s)} 人・${money(sessionTotal(s))}</span>
        </div>
        ${s.note ? `<div class="sub">📌 ${esc(s.note)}</div>` : ''}
      </button>`).join('')}</div>`;
  }
  $view.innerHTML = msg + tabs + body;

  $view.querySelectorAll('[data-list]').forEach(el => el.addEventListener('click', () => {
    state.listTab = el.dataset.list;
    renderSessions();
  }));
  $view.querySelectorAll('.card').forEach(el => el.addEventListener('click', async () => {
    state.flash = '';
    state.cart = [];
    state.memberQuery = '';
    enterSession(sessions[el.dataset.i]);
    const last = await store.get('lastMember');
    state.member = last && state.members.some(m => m.name === last) ? last : '';
    go('overview');
  }));
}

/** 從快取帶入這團的菜單、成員、明細（不打 API） */
function enterSession(session) {
  const d = state.data;
  state.session = session;
  state.lines = session.lines;
  state.members = d.members;
  state.menu = {
    items: d.menuItems.filter(m => m.restaurantId === session.restaurantId),
    groups: d.optionGroups.filter(g => !g.restaurantId || g.restaurantId === session.restaurantId),
  };
}

/** 這團餐廳的資訊（Sheet「餐廳」：電話／地址／備註／地圖／菜單照片），可收合；點圖開新分頁看原圖 */
function restaurantInfoHtml() {
  // 舊團可能沒存餐廳 id，退回用店名找
  const list = state.data.restaurants || [];
  const s = state.session;
  const r = list.find(x => s.restaurantId && x.id === s.restaurantId) || list.find(x => x.name === s.restaurantName);
  if (!r) return '';
  // 沒填地圖連結但有地址時，用地址搜尋 Google 地圖
  const mapUrl = r.mapUrl || (r.address ? 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(r.address) : '');
  const rows = [
    r.phone && `<div>📞 <a href="tel:${esc(r.phone.replace(/[^\d+]/g, ''))}">${esc(r.phone)}</a></div>`,
    r.address && `<div>📍 ${esc(r.address)}</div>`,
    r.note && `<div>📝 ${esc(r.note)}</div>`,
    mapUrl && `<div>🗺️ <a href="${esc(mapUrl)}" target="_blank" rel="noopener">Google 地圖</a></div>`,
  ].filter(Boolean).join('');
  const photo = r.menuPhotoUrl
    ? `<a href="${esc(r.menuPhotoUrl)}" target="_blank" rel="noopener"><img src="${esc(r.menuPhotoUrl)}" alt="${esc(r.name)} 菜單" loading="lazy"></a>`
    : '';
  if (!rows && !photo) return '';
  return `<details class="info" ${state.infoOpen ? 'open' : ''}>
    <summary>🏪 店家資訊${photo ? '・菜單照片' : ''}</summary>
    ${rows ? `<div class="rows">${rows}</div>` : ''}
    ${photo}
  </details>`;
}

function bindRestaurantInfo() {
  $view.querySelector('.info')?.addEventListener('toggle', e => { state.infoOpen = e.currentTarget.open; });
}

// ───────── 2. 訂單總覽（所有人的單，只有自己的能改） ─────────
function renderOverview() {
  const lines = state.lines;
  const total = lines.reduce((s, l) => s + l.subtotal, 0);
  const byMember = {};
  lines.forEach(l => { (byMember[l.member] = byMember[l.member] || []).push(l); });
  // 自己排第一
  const names = Object.keys(byMember).sort((a, b) => (b === state.member) - (a === state.member) || a.localeCompare(b));
  const mine = byMember[state.member];
  const readonly = state.session.status !== 'open';

  frame(state.session.restaurantName, readonly
    ? `${shortDate(state.session.date)} ${state.session.mealType}・${STATUS_LABEL[state.session.status] || ''}`
    : `${state.session.mealType}・${syncedLabel()}`, readonly ? '' : `
    ${state.member
      ? `<span class="sub" style="flex:1">我是 <b>${esc(state.member)}</b> <button class="link" id="switch">切換</button></span>`
      : '<span style="flex:1"></span>'}
    <button class="btn primary" id="order" style="flex:0 0 auto">${mine ? '修改我的訂單' : '我要點餐'}</button>`);

  const tab = state.overviewTab || 'member';
  let body;
  if (!lines.length) {
    body = `<div class="empty">${readonly ? '這團沒有明細' : '還沒有人點餐，當第一個吧'}</div>`;
  } else if (tab === 'member') {
    body = names.map(name => {
      const ls = byMember[name];
      const isMe = name === state.member;
      return `
      <div class="person ${isMe ? 'me' : ''}">
        <div class="row"><b>${esc(name)}${isMe ? '（我）' : ''}</b>
          <span class="price">${money(ls.reduce((s, l) => s + l.subtotal, 0))}</span></div>
        ${ls.map(l => `
          <div class="row sub"><span>${esc(l.itemName)}${l.qty > 1 ? ` × ${l.qty}` : ''}${l.options ? `・${esc(l.options)}` : ''}${l.note ? `・📝${esc(l.note)}` : ''}</span></div>`).join('')}
        ${isMe && !readonly ? '<button class="link" id="editMine" style="font-size:12px">修改</button>' : ''}
      </div>`;
    }).join('');
  } else {
    body = aggregateByItem(lines).map(a => `
      <div class="line"><div class="body"><div>${esc(a.itemName)} × ${a.qty}</div>
        ${a.options ? `<div class="sub">${esc(a.options)}</div>` : ''}
        ${a.note ? `<div class="sub">📝 ${esc(a.note)}</div>` : ''}</div>
        <div class="price">${money(a.subtotal)}</div></div>`).join('');
  }

  $view.innerHTML = `
    ${state.syncError ? `<p class="error">同步失敗：${esc(state.syncError)}</p>` : ''}
    ${state.flash ? `<p class="flash">${esc(state.flash)}</p>` : ''}
    ${state.session.note ? `<p class="hint">📌 ${esc(state.session.note)}</p>` : ''}
    ${restaurantInfoHtml()}
    <div class="summary">
      <div><span>${Object.keys(byMember).length}</span>人</div>
      <div><span>${lines.reduce((s, l) => s + l.qty, 0)}</span>份</div>
      <div><span>${money(total)}</span>總額</div>
    </div>
    <div class="tabbar">
      <div class="tabs">
        <button class="${tab === 'member' ? 'on' : ''}" data-tab="member">依人</button>
        <button class="${tab === 'item' ? 'on' : ''}" data-tab="item">依品項</button>
      </div>
      ${lines.length ? '<button class="btn ghost copy" id="copy" title="複製文字，可貼到 LINE">📋 複製</button>' : ''}
    </div>
    ${body}`;

  $view.querySelector('#copy')?.addEventListener('click', async e => {
    const btn = e.currentTarget;
    try {
      await navigator.clipboard.writeText(tab === 'member' ? textByMember(names, byMember, total) : textByItem(lines, total));
      btn.textContent = '✓ 已複製';
    } catch {
      btn.textContent = '複製失敗';
    }
    setTimeout(() => { btn.textContent = '📋 複製'; }, 1500);
  });

  bindRestaurantInfo();
  $view.querySelectorAll('[data-tab]').forEach(el => el.addEventListener('click', () => { state.overviewTab = el.dataset.tab; renderOverview(); }));
  $footer.querySelector('#switch')?.addEventListener('click', () => { state.whoNext = 'overview'; go('who'); });
  const start = () => { state.flash = ''; state.member ? startOrder() : (state.whoNext = 'menu', go('who')); };
  $footer.querySelector('#order')?.addEventListener('click', start);
  $view.querySelector('#editMine')?.addEventListener('click', start);
}

/** 依品項：品名 + 客製 + 備註都相同的合併數量（打電話給店家用，不帶人名） */
function aggregateByItem(lines) {
  const agg = {};
  lines.forEach(l => {
    const k = l.itemName + '|' + l.options + '|' + l.note;
    const a = agg[k] = agg[k] || { itemName: l.itemName, options: l.options, note: l.note, qty: 0, subtotal: 0 };
    a.qty += l.qty;
    a.subtotal += l.subtotal;
  });
  return Object.values(agg).sort((a, b) => a.itemName.localeCompare(b.itemName) || a.options.localeCompare(b.options) || a.note.localeCompare(b.note));
}

// ───────── 複製成純文字（貼 LINE 用） ─────────
function copyHeader() {
  const s = state.session;
  return `【${s.restaurantName}・${s.mealType}】${s.date.slice(5).replace('-', '/')}`;
}

const itemText = (name, options, qty) => `${name}${options ? `（${options}）` : ''} ×${qty}`;

function textByItem(lines, total) {
  return [
    copyHeader(),
    ...aggregateByItem(lines).map(a => itemText(a.itemName, a.options, a.qty) + (a.note ? `［${a.note}］` : '')),
    '',
    `共 ${lines.reduce((s, l) => s + l.qty, 0)} 份 ${money(total)}`,
  ].join('\n');
}

function textByMember(names, byMember, total) {
  return [
    copyHeader(),
    ...names.map(name => {
      const ls = byMember[name];
      const sum = ls.reduce((s, l) => s + l.subtotal, 0);
      const items = ls.map(l => itemText(l.itemName, l.options, l.qty) + (l.note ? `［${l.note}］` : ''));
      return `${name} ${money(sum)}\n  ${items.join('\n  ')}`;
    }),
    '',
    `共 ${names.length} 人 ${money(total)}`,
  ].join('\n');
}

// ───────── 3. 你是誰 ─────────
function renderWho() {
  frame(state.session.restaurantName, '你是誰');
  const q = state.memberQuery.trim().toLowerCase();
  const list = state.members.filter(m => !q || m.name.toLowerCase().includes(q));
  $view.innerHTML = `
    <input class="search" placeholder="搜尋名字" value="${esc(state.memberQuery)}">
    <div class="grid">${list.map(m => `<button class="chip ${m.name === state.member ? 'on' : ''}" data-name="${esc(m.name)}">${esc(m.name)}</button>`).join('')}</div>
    ${list.length ? '' : '<div class="empty">找不到</div>'}`;
  const input = $view.querySelector('.search');
  input.focus();
  input.setSelectionRange(input.value.length, input.value.length);
  input.addEventListener('input', () => { state.memberQuery = input.value; renderWho(); });
  $view.querySelectorAll('.chip').forEach(el => el.addEventListener('click', () => pickMember(el.dataset.name)));
}

async function pickMember(name) {
  state.member = name;
  state.memberQuery = '';
  await store.set('lastMember', name);
  if (state.whoNext === 'menu') startOrder();
  else go('overview');
}

/** 把自己在這團的明細帶進購物車。從 Sheet 帶回的舊明細沒有 itemId，只能刪除、不能修改。 */
function startOrder() {
  state.cart = state.lines
    .filter(l => l.member === state.member)
    .map(({ member, ...l }) => ({ ...l }));
  state.hadOrder = state.cart.length > 0;
  go('menu');
}

// ───────── 3. 菜單 ─────────
function cartFooter() {
  const n = state.cart.reduce((s, l) => s + l.qty, 0);
  return `<button class="btn ghost" id="toCart">購物車 ${n}</button>
    <span class="total" style="flex:1;text-align:right">${money(cartTotal())}</span>
    <button class="btn primary" id="toCart2" ${state.cart.length ? '' : 'disabled'} style="flex:0 0 auto">確認</button>`;
}

function bindCartFooter() {
  $footer.querySelectorAll('#toCart,#toCart2').forEach(b => b.addEventListener('click', () => go('cart')));
}

function renderMenu() {
  frame(state.session.restaurantName, '選餐點', cartFooter());
  bindCartFooter();
  $view.innerHTML = `
    <div class="who"><span>點餐人：<b>${esc(state.member)}</b></span></div>
    ${restaurantInfoHtml()}
    <div class="list">${state.menu.items.map((m, i) => `
      <button class="card" data-i="${i}">
        <div class="row"><span>${esc(m.name)}</span><span class="price">${money(m.price)}</span></div>
      </button>`).join('')}</div>`;
  bindRestaurantInfo();
  $view.querySelectorAll('.card').forEach(el => el.addEventListener('click', () => {
    state.draft = { item: state.menu.items[el.dataset.i], picks: {}, qty: 1, note: '', editIndex: -1 };
    go('item');
  }));
}

// ───────── 4. 品項客製 ─────────
const groupsOf = item => item.optionGroupIds.map(id => state.menu.groups.find(g => g.id === id)).filter(Boolean);

/** 回傳錯誤訊息，沒問題回傳空字串。規則對齊後端 maxSelect 註解。 */
function groupError(g, picked) {
  const n = picked.length;
  if (g.type === 'single') return g.required && n !== 1 ? '必選' : '';
  if (g.maxSelect > 0) {
    if (g.required && n !== g.maxSelect) return `需選 ${g.maxSelect} 個`;
    if (!g.required && n > g.maxSelect) return `最多 ${g.maxSelect} 個`;
  } else if (g.required && n < 1) return '至少選 1 個';
  return '';
}

function groupHint(g) {
  if (g.type === 'single') return g.required ? '必選・單選' : '單選';
  if (g.maxSelect > 0) return g.required ? `必選 ${g.maxSelect} 個` : `最多 ${g.maxSelect} 個`;
  return g.required ? '必選・可多選' : '可多選';
}

function draftUnitPrice(d) {
  let p = d.item.price;
  groupsOf(d.item).forEach(g => (d.picks[g.id] || []).forEach(ci => { p += g.choices[ci].price; }));
  return p;
}

function draftOptions(d) {
  return groupsOf(d.item).flatMap(g => (d.picks[g.id] || []).map(ci => g.choices[ci].name)).join('/');
}

function renderItem() {
  const d = state.draft;
  const groups = groupsOf(d.item);
  const errors = groups.map(g => groupError(g, d.picks[g.id] || []));
  const ok = errors.every(e => !e);
  const unit = draftUnitPrice(d);
  frame(d.item.name, '客製', `
    <span class="total" style="flex:1">${money(unit * d.qty)}</span>
    <button class="btn primary" id="add" ${ok ? '' : 'disabled'} style="flex:0 0 auto">${d.editIndex >= 0 ? '更新' : '加入購物車'}</button>`);

  $view.innerHTML = `
    ${groups.map((g, gi) => `
      <div class="group ${errors[gi] ? 'err' : ''}">
        <h3>${esc(g.name)} <small>${errors[gi] || groupHint(g)}</small></h3>
        <div class="choices">${g.choices.map((c, ci) => `
          <button class="chip ${(d.picks[g.id] || []).includes(ci) ? 'on' : ''}" data-g="${esc(g.id)}" data-c="${ci}">
            ${esc(c.name)}${c.price ? ` ${c.price > 0 ? '+' : ''}${c.price}` : ''}
          </button>`).join('')}</div>
      </div>`).join('')}
    <div class="field">
      <label>數量</label>
      <div class="qty"><button id="minus">−</button><span>${d.qty}</span><button id="plus">＋</button></div>
    </div>
    <div class="field">
      <label>備註</label>
      <textarea id="note" placeholder="例如：不要香菜">${esc(d.note)}</textarea>
    </div>`;

  $view.querySelectorAll('.chip').forEach(el => el.addEventListener('click', () => {
    const g = groups.find(x => x.id === el.dataset.g);
    const ci = Number(el.dataset.c);
    const cur = d.picks[g.id] || [];
    if (g.type === 'single') {
      d.picks[g.id] = cur[0] === ci && !g.required ? [] : [ci];
    } else if (cur.includes(ci)) {
      d.picks[g.id] = cur.filter(x => x !== ci);
    } else if (g.maxSelect > 0 && cur.length >= g.maxSelect) {
      return; // 已達上限
    } else {
      d.picks[g.id] = [...cur, ci].sort((a, b) => a - b);
    }
    renderItem();
  }));
  $view.querySelector('#minus').addEventListener('click', () => { if (d.qty > 1) { d.qty--; renderItem(); } });
  $view.querySelector('#plus').addEventListener('click', () => { d.qty++; renderItem(); });
  $view.querySelector('#note').addEventListener('input', e => { d.note = e.target.value; });
  $footer.querySelector('#add').addEventListener('click', () => {
    const line = {
      itemId: d.item.id,
      itemName: d.item.name,
      options: draftOptions(d),
      picks: d.picks,
      qty: d.qty,
      unitPrice: unit,
      subtotal: unit * d.qty,
      note: d.note.trim(),
    };
    if (d.editIndex >= 0) state.cart[d.editIndex] = line;
    else state.cart.push(line);
    state.draft = null;
    go(d.editIndex >= 0 ? 'cart' : 'menu');
  });
}

// ───────── 5. 購物車 ─────────
function renderCart() {
  frame('確認訂單', state.member, state.cart.length || hadOrder()
    ? `<span class="total" style="flex:1">合計 ${money(cartTotal())}</span>
       <button class="btn primary" id="send" style="flex:0 0 auto" ${state.sending ? 'disabled' : ''}>
         ${state.sending ? '送出中…' : state.cart.length ? '送出' : '清空我的訂單'}</button>`
    : '');

  $view.innerHTML = state.cart.length
    ? `<p class="hint">${esc(state.session.restaurantName)}・${esc(state.session.mealType)}・送出後會覆蓋你在這團的舊訂單</p>
       ${state.cart.map((l, i) => `
        <div class="line">
          <div class="body">
            <div>${esc(l.itemName)} × ${l.qty}</div>
            ${l.options ? `<div class="sub">${esc(l.options)}</div>` : ''}
            ${l.note ? `<div class="sub">📝 ${esc(l.note)}</div>` : ''}
          </div>
          <div style="text-align:right">
            <div class="price">${money(l.subtotal)}</div>
            ${l.itemId ? `<button class="link" data-edit="${i}" style="font-size:12px">修改</button>` : ''}
            <button class="del" data-del="${i}">刪除</button>
          </div>
        </div>`).join('')}`
    : '<div class="empty">購物車是空的</div>';

  $view.querySelectorAll('[data-del]').forEach(el => el.addEventListener('click', () => {
    state.cart.splice(Number(el.dataset.del), 1);
    renderCart();
  }));
  $view.querySelectorAll('[data-edit]').forEach(el => el.addEventListener('click', () => {
    const i = Number(el.dataset.edit);
    const l = state.cart[i];
    const item = state.menu.items.find(m => m.id === l.itemId);
    if (!item) return;
    state.draft = { item, picks: JSON.parse(JSON.stringify(l.picks || {})), qty: l.qty, note: l.note, editIndex: i };
    go('item');
  }));
  $footer.querySelector('#send')?.addEventListener('click', send);
}

// 原本在這團有單 → 購物車清空後仍可送出（= 取消自己的訂單）
const hadOrder = () => state.hadOrder;

async function send() {
  state.sending = true;
  renderCart();
  try {
    const lines = state.cart.map(({ itemName, options, qty, unitPrice, subtotal, note }) => ({ itemName, options, qty, unitPrice, subtotal, note }));
    const r = await api.addMyLines(state.session.sessionId, state.member, lines);
    state.session.lines = r.lines;
    state.lines = r.lines;
    await store.set('cache', state.data);
    state.flash = r.lineCount ? `已送出 ${r.lineCount} 筆，共 ${money(r.total)}` : '已取消你的訂單';
    go('overview');
  } catch (e) {
    state.sending = false;
    renderCart(); // 讓送出按鈕恢復可按
    alertInline(e.message);
  } finally {
    state.sending = false;
  }
}

function alertInline(msg) {
  $view.insertAdjacentHTML('afterbegin', `<p class="error">送出失敗：${esc(msg)}</p>`);
}

init();
