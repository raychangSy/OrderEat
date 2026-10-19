/**
 * 測試用假資料（在 Apps Script 新增一個檔案貼上，從編輯器選函式執行）。
 * 團ID 一律以 test_ 開頭，clearTestSessions() 只會刪這些。
 */

/** 開一個今天的測試團，並幫幾個人點好餐。 */
function seedTestSession() {
  const today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  const session = openSession_({
    session: {
      sessionId: 'test_' + Utilities.getUuid().slice(0, 8),
      date: today,
      mealType: '午餐',
      restaurantId: 'r_08fff4f0',
      restaurantName: '成美私廚料理',
      note: '測試團，可刪',
      openedBy: '李羅 (leelo)',
    },
  });

  const orders = {
    'Ann': [{ itemName: '蠔油雞肉燴飯', options: '正常', qty: 1, unitPrice: 100, note: '' }],
    'Mark': [
      { itemName: '厚切豬排飯', options: '少飯/加飯', qty: 1, unitPrice: 130, note: '' },
      { itemName: '蠔油雞肉燴飯', options: '正常', qty: 1, unitPrice: 100, note: '帶走' },
    ],
    '盧彥辰': [{ itemName: '麻婆豆腐燴飯', options: '少飯/加荷包蛋', qty: 1, unitPrice: 105, note: '不要豆腐' }],
  };
  Object.keys(orders).forEach(member => addMyLines_({ sessionId: session.sessionId, member, lines: orders[member] }));

  Logger.log('已建立測試團：' + session.sessionId);
}

/** 刪掉所有 test_ 開頭的團與明細。 */
function clearTestSessions() {
  const ss = SpreadsheetApp.getActive();
  [SHEET.LINES, SHEET.SESSIONS].forEach(name => {
    const sh = sheet_(ss, name);
    const last = sh.getLastRow();
    if (last < 2) return;
    const rows = sh.getRange(2, 1, last - 1, 1).getValues()
      .map((r, i) => (str_(r[0]).indexOf('test_') === 0 ? i + 2 : 0))
      .filter(Boolean)
      .sort((a, b) => b - a);
    deleteRows_(sh, rows);
    Logger.log(name + '：刪除 ' + rows.length + ' 列');
  });
}
