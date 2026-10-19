/**
 * OrderEat — Google Apps Script 後端
 *
 * 使用方式見 docs/SETUP_SHEET.md：
 * 1. 貼到試算表的「擴充功能 → Apps Script」
 * 2. 執行一次 setup()
 * 3. 部署為網頁應用程式（執行身分：我／存取權：所有人）
 *
 * API（所有回應格式：{ ok: true, data } 或 { ok: false, error }）
 *   GET  ?action=ping&token=...
 *   GET  ?action=bootstrap&token=...
 *   POST { token, action: 'submitSession', payload: { session, lines } }
 *   POST { token, action: 'syncMembers',   payload: { members, allowEmpty? } }
 *   POST { token, action: 'uploadPhoto',   payload: { restaurantId, base64, mimeType? } }
 *
 * 個人點餐（新團：App 開團／截止，Chrome 擴充功能各自跟單）：
 *   GET  ?action=syncToday&token=...            （擴充功能用：今天開放中的團 + 近 7 天已結單的團（皆含明細）+ 菜單 + 成員，一次取回）
 *   GET  ?action=listOpenSessions&token=...
 *   GET  ?action=getSessionLines&token=...&sessionId=...
 *   POST { token, action: 'openSession',   payload: { session: { sessionId?, date, mealType, restaurantId, restaurantName, note?, openedBy } } }
 *   POST { token, action: 'closeSession',  payload: { sessionId } }
 *   POST { token, action: 'reopenSession', payload: { sessionId } }
 *   POST { token, action: 'addMyLines',    payload: { sessionId, member, lines } }
 *   POST { token, action: 'deleteSession', payload: { sessionId } }      （App：整團刪除，含明細）
 *
 * 手機與擴充功能可以同時點同一團：點餐明細的「來源」分辨 App／插件，
 * submitSession 只覆寫來源 App（或空白）的明細，插件跟單的明細保留。
 * submitSession 的 session.status：'open' = 開放、'closed' = 截止、省略 = 維持原狀態（舊團留空）。
 */

const TZ = 'Asia/Taipei';
const RECENT_SESSION_DAYS = 30;
const MAX_PHOTO_BASE64_LENGTH = 5 * 1024 * 1024;
const PHOTO_FOLDER_NAME = 'OrderEat 菜單照片';
const PROP_TOKEN = 'API_TOKEN';
const PROP_PHOTO_FOLDER = 'PHOTO_FOLDER_ID';

// 「每日記錄」的狀態欄：留空 = 舊團（手機輪流點、從沒開團），以下兩個值 = 新團
const STATUS_OPEN = '開放';
const STATUS_CLOSED = '截止';
// 「點餐明細」的來源欄：留空視同 App
const SOURCE_APP = 'App';
const SOURCE_EXTENSION = '插件';
// 擴充功能「近 N 天」歷史訂單（含今天）
const HISTORY_DAYS = 7;

const SHEET = {
  RESTAURANTS: '餐廳',
  ITEMS: '品項',
  OPTIONS: '客製選項',
  MEMBERS: '成員',
  SESSIONS: '每日記錄',
  LINES: '點餐明細',
  TODAY: '今日彙總',
  STATS: '統計',
};

const COLS = {
  [SHEET.RESTAURANTS]: ['id', '店名', '分類', '電話', '地址', '備註', '停用', '菜單照片', '照片檔ID', 'Google地圖', '連結選單'],
  [SHEET.ITEMS]: ['id', '餐廳', '品名', '價格', '客製選項', '停用'],
  [SHEET.OPTIONS]: ['id', '餐廳', '選項名稱', '類型', '必選', '選項內容', '可選數量'],
  [SHEET.MEMBERS]: ['id', '名字', '停用', '更新時間'],
  [SHEET.SESSIONS]: ['團ID', '日期', '餐別', '餐廳ID', '店名', '總額', '備註', '送出時間', '狀態', '開團人'],
  [SHEET.LINES]: ['團ID', '日期', '餐別', '店名', '點餐人', '品名', '客製', '數量', '單價', '小計', '備註', '已付款', '來源'],
};

// 欄位索引（0-based）
const R = { ID: 0, NAME: 1, CATEGORY: 2, PHONE: 3, ADDRESS: 4, NOTE: 5, DISABLED: 6, PHOTO_URL: 7, PHOTO_ID: 8, MAP_URL: 9, MENU_URL: 10 };
const I = { ID: 0, RESTAURANT: 1, NAME: 2, PRICE: 3, OPTIONS: 4, DISABLED: 5 };
const O = { ID: 0, RESTAURANT: 1, NAME: 2, TYPE: 3, REQUIRED: 4, CHOICES: 5, MAX_SELECT: 6 };
const M = { ID: 0, NAME: 1, DISABLED: 2, UPDATED_AT: 3 };
const S = { ID: 0, DATE: 1, MEAL: 2, RESTAURANT_ID: 3, RESTAURANT_NAME: 4, TOTAL: 5, NOTE: 6, SUBMITTED_AT: 7, STATUS: 8, OPENED_BY: 9 };
const L = { SESSION_ID: 0, DATE: 1, MEAL: 2, RESTAURANT_NAME: 3, MEMBER: 4, ITEM: 5, OPTIONS: 6, QTY: 7, UNIT_PRICE: 8, SUBTOTAL: 9, NOTE: 10, PAID: 11, SOURCE: 12 };

// 需要自動產生 id 的工作表
const AUTO_ID = {
  [SHEET.RESTAURANTS]: { nameCol: R.NAME, prefix: 'r_' },
  [SHEET.ITEMS]: { nameCol: I.NAME, prefix: 'm_' },
  [SHEET.OPTIONS]: { nameCol: O.NAME, prefix: 'o_' },
};

// ───────────────────────── 初始化 ─────────────────────────

/** 建立所有工作表、欄位、下拉選單、勾選框、公式、照片資料夾與 token。可重複執行，不會清掉資料。 */
function setup() {
  const ss = SpreadsheetApp.getActive();
  ss.setSpreadsheetTimeZone(TZ);

  Object.keys(COLS).forEach(name => {
    const sh = getOrCreateSheet_(ss, name);
    const headers = COLS[name];
    sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold').setBackground('#f1f3f4');
    sh.setFrozenRows(1);
  });

  const rows = 500;
  const checkbox = checkboxRule_();
  const restaurantList = SpreadsheetApp.newDataValidation()
    .requireValueInRange(sheet_(ss, SHEET.RESTAURANTS).getRange('B2:B'), true)
    .setAllowInvalid(false)
    .build();
  const typeList = SpreadsheetApp.newDataValidation()
    .requireValueInList(['單選', '多選'], true)
    .setAllowInvalid(false)
    .build();

  const restaurants = sheet_(ss, SHEET.RESTAURANTS);
  restaurants.getRange(2, R.PHONE + 1, rows, 1).setNumberFormat('@'); // 避免 09xx 開頭的 0 被吃掉
  restaurants.getRange(2, R.DISABLED + 1, rows, 1).setDataValidation(checkbox);
  restaurants.getRange(2, R.PHOTO_ID + 1, rows, 1).setFontColor('#9aa0a6');
  restaurants.getRange(2, R.ID + 1, rows, 1).setFontColor('#9aa0a6');

  const items = sheet_(ss, SHEET.ITEMS);
  items.getRange(2, I.ID + 1, rows, 1).setFontColor('#9aa0a6');
  items.getRange(2, I.RESTAURANT + 1, rows, 1).setDataValidation(restaurantList);
  items.getRange(2, I.PRICE + 1, rows, 1).setNumberFormat('0');
  items.getRange(2, I.DISABLED + 1, rows, 1).setDataValidation(checkbox);

  const options = sheet_(ss, SHEET.OPTIONS);
  options.getRange(2, O.ID + 1, rows, 1).setFontColor('#9aa0a6');
  options.getRange(2, O.RESTAURANT + 1, rows, 1).setDataValidation(restaurantList);
  options.getRange(2, O.TYPE + 1, rows, 1).setDataValidation(typeList);
  options.getRange(2, O.REQUIRED + 1, rows, 1).setDataValidation(checkbox);
  options.getRange(2, O.MAX_SELECT + 1, rows, 1).setNumberFormat('0');

  sheet_(ss, SHEET.MEMBERS).getRange(2, M.DISABLED + 1, rows, 1).setDataValidation(checkbox);

  // 日期與團ID 存純文字，避免被自動轉成日期格式
  const sessions = sheet_(ss, SHEET.SESSIONS);
  sessions.getRange(2, 1, sessions.getMaxRows() - 1, 2).setNumberFormat('@');
  const lines = sheet_(ss, SHEET.LINES);
  lines.getRange(2, 1, lines.getMaxRows() - 1, 2).setNumberFormat('@');
  // 「已付款」勾選框只在寫入明細時才加：整欄預先放勾選框會讓 getLastRow() 以為資料到最底

  buildTodaySheet_(ss);
  buildStatsSheet_(ss);
  photoFolder_();

  const token = getToken_() || regenerateToken_();
  // 不用 getUi().alert：從編輯器執行時會卡住等試算表分頁按確定。之後可從選單「OrderEat → 顯示 API Token」查看。
  Logger.log('設定完成。API Token：' + token);
}

function buildTodaySheet_(ss) {
  const sh = getOrCreateSheet_(ss, SHEET.TODAY);
  sh.clear();
  const src = `'${SHEET.LINES}'!A:L`;
  const where = `where B = '"&TEXT(TODAY(),"yyyy-mm-dd")&"'`;
  setBlock_(sh, 'A', '依品項（打電話給店家用）',
    `=IFERROR(QUERY(${src},"select C, D, F, G, sum(H), sum(J) ${where} group by C, D, F, G order by C, D label sum(H) '數量', sum(J) '金額'",1),"今天還沒有訂單")`);
  setBlock_(sh, 'H', '依人（應付金額）',
    `=IFERROR(QUERY(${src},"select C, D, E, sum(J) ${where} group by C, D, E order by C, E label sum(J) '應付'",1),"")`);
  setBlock_(sh, 'M', '今日明細',
    `=IFERROR(QUERY(${src},"select C, E, F, G, H, J, K ${where} order by C, E",1),"")`);
}

function buildStatsSheet_(ss) {
  const sh = getOrCreateSheet_(ss, SHEET.STATS);
  sh.clear();
  const sessions = `'${SHEET.SESSIONS}'!A:H`;
  const lines = `'${SHEET.LINES}'!A:L`;
  const thisMonth = `where B starts with '"&TEXT(TODAY(),"yyyy-mm")&"'`;
  setBlock_(sh, 'A', '本月各店次數',
    `=IFERROR(QUERY(${sessions},"select E, count(A) ${thisMonth} group by E order by count(A) desc label count(A) '次數'",1),"本月尚無記錄")`);
  setBlock_(sh, 'D', '本月每人花費',
    `=IFERROR(QUERY(${lines},"select E, sum(J) ${thisMonth} group by E order by sum(J) desc label sum(J) '本月花費'",1),"")`);
  setBlock_(sh, 'G', '各店最後一次吃',
    `=IFERROR(QUERY(${sessions},"select E, max(B) where E is not null group by E order by max(B) desc label max(B) '最後一次'",1),"")`);
  setBlock_(sh, 'J', '本月總花費',
    `=IFERROR(QUERY(${sessions},"select sum(F) ${thisMonth} label sum(F) ''",1),0)`);
}

function setBlock_(sh, col, title, formula) {
  sh.getRange(col + '1').setValue(title).setFontWeight('bold');
  sh.getRange(col + '2').setFormula(formula);
}

// ───────────────────────── 選單與觸發 ─────────────────────────

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('OrderEat')
    .addItem('顯示 API Token', 'showToken')
    .addItem('重新產生 Token', 'regenerateToken')
    .addSeparator()
    .addItem('重新建立彙總／統計公式', 'rebuildFormulas')
    .addItem('整理空白列（每日記錄、點餐明細）', 'compactSheets')
    .addToUi();
}

/** 在餐廳／品項／客製選項輸入名稱時自動補 id。 */
function onEdit(e) {
  const sh = e && e.range && e.range.getSheet();
  if (sh && AUTO_ID[sh.getName()]) fillMissingIds_(sh);
}

function showToken() {
  const ui = SpreadsheetApp.getUi();
  ui.alert('OrderEat API Token', getToken_() || '尚未執行 setup()', ui.ButtonSet.OK);
}

function regenerateToken() {
  const ui = SpreadsheetApp.getUi();
  const answer = ui.alert('重新產生 Token', '舊 Token 會立即失效，App 必須更新 local.properties 後重新安裝。確定？', ui.ButtonSet.OK_CANCEL);
  if (answer !== ui.Button.OK) return;
  ui.alert('新的 API Token', regenerateToken_(), ui.ButtonSet.OK);
}

function rebuildFormulas() {
  const ss = SpreadsheetApp.getActive();
  buildTodaySheet_(ss);
  buildStatsSheet_(ss);
}

/** 把「每日記錄」「點餐明細」的資料往上靠，移除中間的空白列（保留已付款勾選）。 */
function compactSheets() {
  const ss = SpreadsheetApp.getActive();
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    [SHEET.SESSIONS, SHEET.LINES].forEach(name => {
      const sh = sheet_(ss, name);
      const width = COLS[name].length;
      const data = rows_(ss, name).filter(r => str_(r[0]));
      const last = sh.getLastRow();
      if (last > 1) {
        const range = sh.getRange(2, 1, last - 1, width);
        range.clearContent();
        range.clearDataValidations();
      }
      if (!data.length) return;
      sh.getRange(2, 1, data.length, 2).setNumberFormat('@');
      if (name === SHEET.LINES) {
        data.forEach(r => { r[L.PAID] = isTrue_(r[L.PAID]); });
        sh.getRange(2, L.PAID + 1, data.length, 1).setDataValidation(checkboxRule_());
      }
      sh.getRange(2, 1, data.length, width).setValues(data);
    });
  } finally {
    lock.releaseLock();
  }
}

// ───────────────────────── Web App 入口 ─────────────────────────

function doGet(e) {
  return respond_(() => {
    const p = (e && e.parameter) || {};
    checkToken_(p.token);
    switch (p.action) {
      case 'ping': return { pong: true, serverTime: nowString_() };
      case 'bootstrap': return bootstrap_();
      case 'syncToday': return syncToday_();
      case 'listOpenSessions': return listOpenSessions_();
      case 'getSessionLines': return getSessionLines_(p.sessionId);
      default: throw new Error('未知的 action：' + p.action);
    }
  });
}

function doPost(e) {
  return respond_(() => {
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    checkToken_(body.token);
    const lock = LockService.getScriptLock();
    lock.waitLock(20000);
    try {
      switch (body.action) {
        case 'submitSession': return submitSession_(body.payload);
        case 'syncMembers': return syncMembers_(body.payload);
        case 'uploadPhoto': return uploadPhoto_(body.payload);
        case 'openSession': return openSession_(body.payload);
        case 'closeSession': return setSessionStatus_(body.payload, STATUS_CLOSED);
        case 'reopenSession': return setSessionStatus_(body.payload, STATUS_OPEN);
        case 'addMyLines': return addMyLines_(body.payload);
        case 'deleteSession': return deleteSession_(body.payload);
        default: throw new Error('未知的 action：' + body.action);
      }
    } finally {
      lock.releaseLock();
    }
  });
}

function respond_(fn) {
  let out;
  try {
    out = { ok: true, data: fn() };
  } catch (err) {
    out = { ok: false, error: String((err && err.message) || err) };
  }
  return ContentService.createTextOutput(JSON.stringify(out)).setMimeType(ContentService.MimeType.JSON);
}

// ───────────────────────── Actions ─────────────────────────

function bootstrap_() {
  const ss = SpreadsheetApp.getActive();
  // 平常只讀不寫；只有發現「有名稱沒 id」的列（例如整批貼上沒觸發 onEdit）才補 id 再重讀
  const master = {};
  Object.keys(AUTO_ID).forEach(name => {
    const cfg = AUTO_ID[name];
    let data = rows_(ss, name);
    if (data.some(r => !str_(r[0]) && str_(r[cfg.nameCol]))) {
      fillMissingIds_(sheet_(ss, name));
      data = rows_(ss, name);
    }
    master[name] = data;
  });

  const restaurants = master[SHEET.RESTAURANTS]
    .filter(r => str_(r[R.NAME]) && !isTrue_(r[R.DISABLED]))
    .map(r => ({
      id: str_(r[R.ID]),
      name: str_(r[R.NAME]),
      categories: splitList_(r[R.CATEGORY]),
      phone: str_(r[R.PHONE]),
      address: str_(r[R.ADDRESS]),
      note: str_(r[R.NOTE]),
      menuPhotoUrl: photoUrlFrom_(r[R.PHOTO_ID], r[R.PHOTO_URL]),
      mapUrl: str_(r[R.MAP_URL]),
      menuUrl: str_(r[R.MENU_URL]),
    }));
  const restaurantIdByName = {};
  restaurants.forEach(r => { restaurantIdByName[r.name] = r.id; });
  const hasRestaurant = name => Object.prototype.hasOwnProperty.call(restaurantIdByName, name);

  // 客製選項的「餐廳」留空 = 通用（所有店都能用）
  const groupIdByKey = {};
  const optionGroups = master[SHEET.OPTIONS]
    .filter(r => str_(r[O.NAME]) && (!str_(r[O.RESTAURANT]) || hasRestaurant(str_(r[O.RESTAURANT]))))
    .map(r => {
      const restName = str_(r[O.RESTAURANT]);
      const group = {
        id: str_(r[O.ID]),
        restaurantId: restName ? restaurantIdByName[restName] : '',
        name: str_(r[O.NAME]),
        type: str_(r[O.TYPE]) === '多選' ? 'multi' : 'single',
        required: isTrue_(r[O.REQUIRED]),
        choices: parseChoices_(r[O.CHOICES]),
        // 多選才有意義：0 = 不限；必選 + N = 剛好選 N 個；非必選 + N = 最多 N 個
        maxSelect: str_(r[O.TYPE]) === '多選' ? Math.max(0, Math.floor(num_(r[O.MAX_SELECT]))) : 1,
      };
      groupIdByKey[group.restaurantId + '|' + group.name] = group.id;
      return group;
    });

  const menuItems = master[SHEET.ITEMS]
    .filter(r => str_(r[I.NAME]) && !isTrue_(r[I.DISABLED]) && hasRestaurant(str_(r[I.RESTAURANT])))
    .map(r => {
      const restaurantId = restaurantIdByName[str_(r[I.RESTAURANT])];
      return {
        id: str_(r[I.ID]),
        restaurantId: restaurantId,
        name: str_(r[I.NAME]),
        price: num_(r[I.PRICE]),
        optionGroupIds: splitList_(r[I.OPTIONS])
          .map(n => groupIdByKey[restaurantId + '|' + n] || groupIdByKey['|' + n])
          .filter(Boolean),
      };
    });

  const members = rows_(ss, SHEET.MEMBERS)
    .filter(r => str_(r[M.NAME]))
    .map(r => ({
      id: str_(r[M.ID]),
      name: str_(r[M.NAME]),
      active: !isTrue_(r[M.DISABLED]),
      updatedAt: str_(r[M.UPDATED_AT]),
    }));

  const cutoff = Utilities.formatDate(new Date(Date.now() - RECENT_SESSION_DAYS * 86400000), TZ, 'yyyy-MM-dd');
  const sessionRows = rows_(ss, SHEET.SESSIONS);
  // 全部團ID：App 用來刪掉「Sheet 上已被刪除」的已送出團
  const sessionIds = sessionRows.map(r => str_(r[S.ID])).filter(Boolean);
  const recentSessions = sessionRows
    .filter(r => str_(r[S.ID]) && str_(r[S.DATE]) >= cutoff)
    .map(r => ({
      sessionId: str_(r[S.ID]),
      date: str_(r[S.DATE]),
      mealType: str_(r[S.MEAL]),
      restaurantId: str_(r[S.RESTAURANT_ID]),
      restaurantName: str_(r[S.RESTAURANT_NAME]),
      total: num_(r[S.TOTAL]),
      note: str_(r[S.NOTE]),
      submittedAt: str_(r[S.SUBMITTED_AT]),
      status: str_(r[S.STATUS]), // '' = 舊團；'開放'／'截止' = 新團
      openedBy: str_(r[S.OPENED_BY]),
    }));

  return { restaurants, menuItems, optionGroups, members, recentSessions, sessionIds, serverTime: nowString_() };
}

/**
 * 送出（或覆蓋重送）一團訂單。
 * 同一個 sessionId 的舊明細會先刪掉再寫入，但「已付款」勾選會依點餐人保留。
 */
function submitSession_(p) {
  const s = (p && p.session) || {};
  const sessionId = str_(s.sessionId);
  const date = str_(s.date);
  if (!sessionId) throw new Error('缺少 sessionId');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('日期格式需為 yyyy-MM-dd');
  const lines = (p && p.lines) || [];

  const ss = SpreadsheetApp.getActive();
  const now = nowString_();
  const mealType = str_(s.mealType);
  const restaurantName = str_(s.restaurantName);

  // App 只覆寫自己送的明細（來源 App／舊資料空白），插件跟單的明細保留
  const linesSheet = sheet_(ss, SHEET.LINES);
  const existingLines = sessionLineRows_(linesSheet, sessionId);
  const toDelete = existingLines.filter(x => str_(x.values[L.SOURCE]) !== SOURCE_EXTENSION);
  const paidByMember = {};
  toDelete.forEach(x => { if (isTrue_(x.values[L.PAID])) paidByMember[str_(x.values[L.MEMBER])] = true; });
  deleteRows_(linesSheet, toDelete.map(x => x.row).sort((a, b) => b - a));

  const newRows = lines.map(l => [
    sessionId,
    date,
    mealType,
    restaurantName,
    str_(l.member),
    str_(l.itemName),
    str_(l.options),
    num_(l.qty),
    num_(l.unitPrice),
    num_(l.subtotal),
    str_(l.note),
    paidByMember[str_(l.member)] === true,
    SOURCE_APP,
  ]);
  appendLineRows_(linesSheet, newRows);

  const allLines = sessionLines_(linesSheet, sessionId);
  const total = allLines.reduce((sum, l) => sum + l.subtotal, 0);

  const sessionsSheet = sheet_(ss, SHEET.SESSIONS);
  const existing = findRow_(sessionsSheet, S.ID, sessionId);
  const previous = existing > 0
    ? sessionsSheet.getRange(existing, S.STATUS + 1, 1, 2).getValues()[0]
    : ['', ''];
  // 沒帶 status = 維持原狀態（舊團留空，不會變成新團）
  const status = s.status === 'open' ? STATUS_OPEN : s.status === 'closed' ? STATUS_CLOSED : str_(previous[0]);
  const sessionRow = [
    sessionId, date, mealType, str_(s.restaurantId), restaurantName, total, str_(s.note), now,
    status, str_(s.openedBy) || str_(previous[1]),
  ];
  if (existing > 0) {
    sessionsSheet.getRange(existing, 1, 1, sessionRow.length).setValues([sessionRow]);
  } else {
    appendRows_(sessionsSheet, [sessionRow], 2);
  }

  return {
    sessionId,
    lineCount: newRows.length,
    total,
    submittedAt: now,
    status: status === STATUS_OPEN ? 'open' : status === STATUS_CLOSED ? 'closed' : '',
    lines: allLines,
  };
}

/**
 * 覆寫「這團 + 這個人」的明細；lines 為空 = 取消自己的訂單。
 * 只能寫開放中的新團。日期／餐別／店名以 Sheet 上的團為準，小計由伺服器重算，「已付款」保留。
 * 會覆蓋這個人在這團的所有明細（包含手機點的）。
 */
function addMyLines_(p) {
  const sessionId = str_(p && p.sessionId);
  const member = str_(p && p.member);
  const lines = (p && p.lines) || [];
  if (!member) throw new Error('缺少點餐人');
  if (!Array.isArray(lines)) throw new Error('lines 格式錯誤');

  const ss = SpreadsheetApp.getActive();
  const sessionsSheet = sheet_(ss, SHEET.SESSIONS);
  const found = findNewSession_(sessionsSheet, sessionId);
  if (str_(found.values[S.STATUS]) !== STATUS_OPEN) throw new Error('這團已截止，不能再點餐');

  const newRows = lines.map((l, i) => {
    const itemName = str_(l.itemName);
    const qty = Math.floor(num_(l.qty));
    const unitPrice = num_(l.unitPrice);
    if (!itemName) throw new Error(`第 ${i + 1} 筆缺少品名`);
    if (qty < 1) throw new Error(`第 ${i + 1} 筆數量需大於 0`);
    return [
      sessionId,
      str_(found.values[S.DATE]),
      str_(found.values[S.MEAL]),
      str_(found.values[S.RESTAURANT_NAME]),
      member,
      itemName,
      str_(l.options),
      qty,
      unitPrice,
      qty * unitPrice,
      str_(l.note),
      false,
      SOURCE_EXTENSION,
    ];
  });

  const linesSheet = sheet_(ss, SHEET.LINES);
  const mine = sessionLineRows_(linesSheet, sessionId).filter(x => str_(x.values[L.MEMBER]) === member);
  const paid = mine.some(x => isTrue_(x.values[L.PAID]));
  deleteRows_(linesSheet, mine.map(x => x.row).sort((a, b) => b - a));
  newRows.forEach(r => { r[L.PAID] = paid; });
  appendLineRows_(linesSheet, newRows);

  // 回傳整團最新明細，擴充功能直接更新本地快取
  const sessionLines = sessionLines_(linesSheet, sessionId);
  const total = sessionLines.reduce((sum, l) => sum + l.subtotal, 0);
  const now = nowString_();
  sessionsSheet.getRange(found.row, S.TOTAL + 1).setValue(total);
  sessionsSheet.getRange(found.row, S.SUBMITTED_AT + 1).setValue(now);

  const myTotal = newRows.reduce((sum, r) => sum + r[L.SUBTOTAL], 0);
  return { sessionId, member, lineCount: newRows.length, total: myTotal, sessionTotal: total, submittedAt: now, lines: sessionLines };
}

/** 整團刪除：每日記錄＋點餐明細。找不到也算成功（重送不會出錯）。 */
function deleteSession_(p) {
  const sessionId = str_(p && p.sessionId);
  if (!sessionId) throw new Error('缺少 sessionId');
  const ss = SpreadsheetApp.getActive();
  const linesSheet = sheet_(ss, SHEET.LINES);
  const lineRows = findRows_(linesSheet, L.SESSION_ID, sessionId).sort((a, b) => b - a);
  deleteRows_(linesSheet, lineRows);
  const sessionsSheet = sheet_(ss, SHEET.SESSIONS);
  const sessionRows = findRows_(sessionsSheet, S.ID, sessionId).sort((a, b) => b - a);
  deleteRows_(sessionsSheet, sessionRows);
  return { sessionId, deletedLines: lineRows.length, deletedSessions: sessionRows.length };
}

/** 手機開團：在「每日記錄」寫一列，狀態 = 開放、總額 = 0。 */
function openSession_(p) {
  const s = (p && p.session) || {};
  const sessionId = str_(s.sessionId) || Utilities.getUuid();
  const date = str_(s.date);
  const restaurantId = str_(s.restaurantId);
  const restaurantName = str_(s.restaurantName);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('日期格式需為 yyyy-MM-dd');
  if (!restaurantId || !restaurantName) throw new Error('缺少餐廳');

  const sh = sheet_(SpreadsheetApp.getActive(), SHEET.SESSIONS);
  if (findRow_(sh, S.ID, sessionId) > 0) throw new Error('團ID 已存在：' + sessionId);

  const now = nowString_();
  const row = [sessionId, date, str_(s.mealType), restaurantId, restaurantName, 0, str_(s.note), now, STATUS_OPEN, str_(s.openedBy)];
  appendRows_(sh, [row], 2);
  return sessionFromRow_(row);
}

/** 截止／重新開放。只能用在新團。 */
function setSessionStatus_(p, status) {
  const sessionId = str_(p && p.sessionId);
  const sh = sheet_(SpreadsheetApp.getActive(), SHEET.SESSIONS);
  const found = findNewSession_(sh, sessionId);
  sh.getRange(found.row, S.STATUS + 1).setValue(status);
  found.values[S.STATUS] = status;
  return sessionFromRow_(found.values);
}

/** 開放中的新團，附上點餐人數與目前總額。 */
function listOpenSessions_() {
  const ss = SpreadsheetApp.getActive();
  const linesSheet = sheet_(ss, SHEET.LINES);
  return rows_(ss, SHEET.SESSIONS)
    .filter(r => str_(r[S.ID]) && str_(r[S.STATUS]) === STATUS_OPEN)
    .map(r => {
      const lines = sessionLineRows_(linesSheet, str_(r[S.ID])).map(x => x.values);
      const session = sessionFromRow_(r);
      session.memberCount = Object.keys(lines.reduce((m, l) => { m[str_(l[L.MEMBER])] = true; return m; }, {})).length;
      session.total = lines.reduce((sum, l) => sum + num_(l[L.SUBTOTAL]), 0);
      return session;
    })
    .sort((a, b) => (a.date + a.openedAt < b.date + b.openedAt ? 1 : -1));
}

/** 某個新團的團資訊與所有人的明細。 */
function getSessionLines_(sessionId) {
  const ss = SpreadsheetApp.getActive();
  const found = findNewSession_(sheet_(ss, SHEET.SESSIONS), str_(sessionId));
  return { session: sessionFromRow_(found.values), lines: sessionLines_(sheet_(ss, SHEET.LINES), str_(sessionId)) };
}

/** 找新團（狀態非空），回傳 { row, values }；找不到或是舊團就丟錯。 */
function findNewSession_(sh, sessionId) {
  if (!sessionId) throw new Error('缺少 sessionId');
  const row = findRow_(sh, S.ID, sessionId);
  if (row < 0) throw new Error('找不到這團（可能已被刪除）');
  const values = sh.getRange(row, 1, 1, COLS[SHEET.SESSIONS].length).getValues()[0];
  if (!str_(values[S.STATUS])) throw new Error('這不是開放個人點餐的團');
  return { row, values };
}

function sessionFromRow_(r) {
  return {
    sessionId: str_(r[S.ID]),
    date: str_(r[S.DATE]),
    mealType: str_(r[S.MEAL]),
    restaurantId: str_(r[S.RESTAURANT_ID]),
    restaurantName: str_(r[S.RESTAURANT_NAME]),
    total: num_(r[S.TOTAL]),
    note: str_(r[S.NOTE]),
    openedAt: str_(r[S.SUBMITTED_AT]),
    // open = 開放中新團；closed = 已截止新團；submitted = 手機送出的舊團
    status: str_(r[S.STATUS]) === STATUS_OPEN ? 'open' : str_(r[S.STATUS]) === STATUS_CLOSED ? 'closed' : 'submitted',
    openedBy: str_(r[S.OPENED_BY]),
  };
}

function lineFromRow_(r) {
  return {
    member: str_(r[L.MEMBER]),
    itemName: str_(r[L.ITEM]),
    options: str_(r[L.OPTIONS]),
    qty: num_(r[L.QTY]),
    unitPrice: num_(r[L.UNIT_PRICE]),
    subtotal: num_(r[L.SUBTOTAL]),
    note: str_(r[L.NOTE]),
    paid: isTrue_(r[L.PAID]),
    source: str_(r[L.SOURCE]) === SOURCE_EXTENSION ? 'extension' : 'app',
  };
}

/**
 * 擴充功能同步用，一次回傳：
 *   sessions：今天開放中的新團
 *   history ：近 HISTORY_DAYS 天（含今天）非開放中的團 = 已截止的新團 + 手機送出的舊團，新到舊
 * 兩者都附所有明細，另附菜單與成員。
 */
function syncToday_() {
  const ss = SpreadsheetApp.getActive();
  const today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const cutoff = Utilities.formatDate(new Date(Date.now() - (HISTORY_DAYS - 1) * 86400000), TZ, 'yyyy-MM-dd');
  const b = bootstrap_();
  const linesSheet = sheet_(ss, SHEET.LINES);
  const withLines = r => {
    const session = sessionFromRow_(r);
    session.lines = sessionLines_(linesSheet, session.sessionId);
    return session;
  };
  const sessionRows = rows_(ss, SHEET.SESSIONS).filter(r => str_(r[S.ID]));
  const sessions = sessionRows
    .filter(r => str_(r[S.STATUS]) === STATUS_OPEN && str_(r[S.DATE]) === today)
    .map(withLines);
  const history = sessionRows
    .filter(r => str_(r[S.STATUS]) !== STATUS_OPEN && str_(r[S.DATE]) >= cutoff && str_(r[S.DATE]) <= today)
    .map(withLines)
    .sort((x, y) => (x.date + x.openedAt < y.date + y.openedAt ? 1 : -1));
  return {
    date: today,
    sessions,
    history,
    restaurants: b.restaurants,
    menuItems: b.menuItems,
    optionGroups: b.optionGroups,
    members: b.members.filter(m => m.active),
    syncedAt: nowString_(),
  };
}

/** 這團在點餐明細的列：[{ row, values }]，只讀這團所在的範圍 */
function sessionLineRows_(sh, sessionId) {
  const rows = findRows_(sh, L.SESSION_ID, sessionId);
  if (!rows.length) return [];
  const top = Math.min.apply(null, rows);
  const bottom = Math.max.apply(null, rows);
  const block = sh.getRange(top, 1, bottom - top + 1, COLS[SHEET.LINES].length).getValues();
  return rows.map(row => ({ row, values: block[row - top] }));
}

function sessionLines_(sh, sessionId) {
  return sessionLineRows_(sh, sessionId)
    .sort((a, b) => a.row - b.row)
    .map(x => lineFromRow_(x.values));
}

function appendLineRows_(sh, rows) {
  if (!rows.length) return;
  const start = appendRows_(sh, rows, 2);
  sh.getRange(start, L.PAID + 1, rows.length, 1).setDataValidation(checkboxRule_());
}

/** App 的成員名單整份覆寫到「成員」（Sheet 當備份）。 */
function syncMembers_(p) {
  const members = (p && p.members) || [];
  if (!members.length && !(p && p.allowEmpty)) {
    throw new Error('成員名單是空的，拒絕覆蓋（避免誤刪備份）');
  }
  const ss = SpreadsheetApp.getActive();
  const sh = sheet_(ss, SHEET.MEMBERS);
  const last = sh.getLastRow();
  if (last > 1) sh.getRange(2, 1, last - 1, COLS[SHEET.MEMBERS].length).clearContent();

  const now = nowString_();
  const rows = members
    .filter(m => str_(m.name))
    .map(m => [str_(m.id) || newId_('u_'), str_(m.name), m.active === false, str_(m.updatedAt) || now]);
  if (rows.length) {
    const start = appendRows_(sh, rows);
    sh.getRange(start, M.DISABLED + 1, rows.length, 1).setDataValidation(checkboxRule_());
  }
  return { count: rows.length };
}

/** 上傳菜單照片：一店一張，新照片會取代舊照片（舊檔移到垃圾桶）。 */
function uploadPhoto_(p) {
  const restaurantId = str_(p && p.restaurantId);
  const base64 = (p && p.base64) || '';
  if (!restaurantId) throw new Error('缺少 restaurantId');
  if (!base64) throw new Error('缺少照片資料');
  if (base64.length > MAX_PHOTO_BASE64_LENGTH) throw new Error('照片太大，請先壓縮');

  const ss = SpreadsheetApp.getActive();
  const sh = sheet_(ss, SHEET.RESTAURANTS);
  const row = findRow_(sh, R.ID, restaurantId);
  if (row < 0) throw new Error('找不到餐廳：' + restaurantId);

  const name = str_(sh.getRange(row, R.NAME + 1).getValue()) || restaurantId;
  const stamp = Utilities.formatDate(new Date(), TZ, 'yyyyMMdd-HHmmss');
  const blob = Utilities.newBlob(Utilities.base64Decode(base64), str_(p.mimeType) || 'image/jpeg', name + '_' + stamp + '.jpg');
  const file = photoFolder_().createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);

  const oldId = str_(sh.getRange(row, R.PHOTO_ID + 1).getValue());
  if (oldId && oldId !== file.getId()) {
    try {
      DriveApp.getFileById(oldId).setTrashed(true);
    } catch (e) {
      // 舊檔已被手動刪除，忽略
    }
  }

  const url = photoUrl_(file.getId());
  sh.getRange(row, R.PHOTO_URL + 1, 1, 2).setValues([[url, file.getId()]]);
  return { url, fileId: file.getId() };
}

// ───────────────────────── Helpers ─────────────────────────

function getOrCreateSheet_(ss, name) {
  return ss.getSheetByName(name) || ss.insertSheet(name);
}

function sheet_(ss, name) {
  const sh = ss.getSheetByName(name);
  if (!sh) throw new Error('找不到工作表「' + name + '」，請先執行 setup()');
  return sh;
}

/** 回傳資料列（不含標題），欄數固定為 COLS 定義的欄數。 */
function rows_(ss, name) {
  const sh = sheet_(ss, name);
  const last = sh.getLastRow();
  if (last < 2) return [];
  return sh.getRange(2, 1, last - 1, COLS[name].length).getValues();
}

function findRow_(sh, col, value) {
  const rows = findRows_(sh, col, value);
  return rows.length ? rows[0] : -1;
}

/** 某欄完全等於 value 的所有列號（1-based）。用 TextFinder 搜尋，不把整欄讀回來。 */
function findRows_(sh, col, value) {
  const last = sh.getLastRow();
  if (last < 2 || !value) return [];
  return sh.getRange(2, col + 1, last - 1, 1)
    .createTextFinder(value)
    .matchEntireCell(true)
    .matchCase(true)
    .findAll()
    .map(cell => cell.getRow());
}

/** 從最後一筆資料後面寫入，必要時擴充列數；textCols = 前幾欄設為純文字。回傳起始列（1-based）。 */
function appendRows_(sh, rows, textCols) {
  const start = lastDataRow_(sh) + 1;
  const end = start + rows.length - 1;
  if (end > sh.getMaxRows()) sh.insertRowsAfter(sh.getMaxRows(), end - sh.getMaxRows());
  if (textCols) sh.getRange(start, 1, rows.length, textCols).setNumberFormat('@');
  sh.getRange(start, 1, rows.length, rows[0].length).setValues(rows);
  return start;
}

/** 以 A 欄（id／團ID）判斷最後一筆資料的列號；勾選框等空白格不算。 */
function lastDataRow_(sh) {
  const last = sh.getLastRow();
  if (last < 2) return 1;
  // 常見情況：最後一列就是資料，不用掃整欄
  if (str_(sh.getRange(last, 1).getValue())) return last;
  const values = sh.getRange(2, 1, last - 1, 1).getValues();
  for (let i = values.length - 1; i >= 0; i--) {
    if (str_(values[i][0])) return i + 2;
  }
  return 1;
}

/** 刪除指定列（1-based，需由大到小排序），連續的列一次刪。 */
function deleteRows_(sh, rowNums) {
  if (!rowNums.length) return;
  // 試算表不允許刪光所有非凍結列，先補一列空白
  if (sh.getMaxRows() - sh.getFrozenRows() <= rowNums.length) sh.insertRowsAfter(sh.getMaxRows(), 1);
  let i = 0;
  while (i < rowNums.length) {
    let start = rowNums[i];
    let count = 1;
    while (i + count < rowNums.length && rowNums[i + count] === start - 1) {
      start--;
      count++;
    }
    sh.deleteRows(start, count);
    i += count;
  }
}

function fillMissingIds_(sh) {
  const cfg = AUTO_ID[sh.getName()];
  if (!cfg) return;
  const last = sh.getLastRow();
  if (last < 2) return;
  const values = sh.getRange(2, 1, last - 1, cfg.nameCol + 1).getValues();
  let changed = false;
  values.forEach(row => {
    if (!str_(row[0]) && str_(row[cfg.nameCol])) {
      row[0] = newId_(cfg.prefix);
      changed = true;
    }
  });
  if (changed) sh.getRange(2, 1, last - 1, 1).setValues(values.map(r => [r[0]]));
}

function checkboxRule_() {
  return SpreadsheetApp.newDataValidation().requireCheckbox().build();
}

/** 「正常|半糖|大杯+10|去冰-5」→ [{name, price}] */
function parseChoices_(v) {
  return str_(v)
    .split(/[|｜\n]/)
    .map(s => s.trim())
    .filter(Boolean)
    .map(s => {
      const m = s.match(/^(.*?)\s*([+＋\-－])\s*(\d+)$/);
      if (!m || !m[1]) return { name: s, price: 0 };
      const sign = m[2] === '-' || m[2] === '－' ? -1 : 1;
      return { name: m[1], price: sign * Number(m[3]) };
    });
}

function splitList_(v) {
  return str_(v)
    .split(/[,，、\n]/)
    .map(s => s.trim())
    .filter(Boolean);
}

/** 優先用照片檔ID；手動貼的 Drive 分享連結也會轉成可直接顯示的圖片網址。 */
function photoUrlFrom_(fileId, url) {
  let id = str_(fileId);
  if (!id) {
    const m = str_(url).match(/\/d\/([\w-]{10,})|[?&]id=([\w-]{10,})/);
    id = m ? m[1] || m[2] : '';
  }
  return id ? photoUrl_(id) : str_(url);
}

function photoUrl_(fileId) {
  return 'https://drive.google.com/thumbnail?id=' + fileId + '&sz=w1600';
}

function photoFolder_() {
  const props = PropertiesService.getScriptProperties();
  const id = props.getProperty(PROP_PHOTO_FOLDER);
  if (id) {
    try {
      const folder = DriveApp.getFolderById(id);
      if (!folder.isTrashed()) return folder;
    } catch (e) {
      // 資料夾被刪除，重建
    }
  }
  const folder = DriveApp.createFolder(PHOTO_FOLDER_NAME);
  props.setProperty(PROP_PHOTO_FOLDER, folder.getId());
  return folder;
}

function getToken_() {
  return PropertiesService.getScriptProperties().getProperty(PROP_TOKEN);
}

function regenerateToken_() {
  const token = Utilities.getUuid().replace(/-/g, '');
  PropertiesService.getScriptProperties().setProperty(PROP_TOKEN, token);
  return token;
}

function checkToken_(token) {
  const expected = getToken_();
  if (!expected) throw new Error('尚未執行 setup()');
  if (str_(token) !== expected) throw new Error('token 錯誤');
}

function newId_(prefix) {
  return prefix + Utilities.getUuid().replace(/-/g, '').slice(0, 8);
}

function nowString_() {
  return Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd HH:mm:ss');
}

function str_(v) {
  if (v instanceof Date) return Utilities.formatDate(v, TZ, 'yyyy-MM-dd');
  return v == null ? '' : String(v).trim();
}

function num_(v) {
  const n = Number(v);
  return isFinite(n) ? n : 0;
}

function isTrue_(v) {
  return v === true || v === 'TRUE' || v === 'true';
}
