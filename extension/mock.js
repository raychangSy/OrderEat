// 假資料：結構對齊 GAS bootstrap_() 的回傳，另加新團用的 openSessions / myLines
export const MOCK = {
  restaurants: [
    { id: 'r_08fff4f0', name: '成美私廚料理', phone: '0929-805911', note: '六日公休', address: '高雄市前金區中正四路 100 號', menuPhotoUrl: 'icons/icon128.png' },
    { id: 'r_d6f7699b', name: '金大富', phone: '07-3388999', note: '週日公休' },
  ],
  optionGroups: [
    { id: 'o_f6a022d1', restaurantId: 'r_08fff4f0', name: '飯量', type: 'single', required: false, maxSelect: 1,
      choices: [{ name: '正常', price: 0 }, { name: '少飯', price: 0 }, { name: '不要飯', price: 0 }] },
    { id: 'o_d847cd38', restaurantId: 'r_08fff4f0', name: '加點', type: 'multi', required: false, maxSelect: 0,
      choices: [{ name: '加飯', price: 10 }, { name: '加荷包蛋', price: 15 }, { name: '加辣', price: 0 }, { name: '加酸豆', price: 20 }] },
    { id: 'o_1273bee4', restaurantId: 'r_d6f7699b', name: '飯量', type: 'single', required: true, maxSelect: 1,
      choices: [{ name: '正常', price: 0 }, { name: '半飯', price: 0 }, { name: '不要飯', price: 0 }] },
  ],
  menuItems: [
    ['m_6b1aaa97', 'r_08fff4f0', '麻婆豆腐燴飯', 90],
    ['m_fc491997', 'r_08fff4f0', '蠔油雞肉燴飯', 100],
    ['m_11ad11ec', 'r_08fff4f0', '蠔油豬肉燴飯', 100],
    ['m_25f156d6', 'r_08fff4f0', '黑胡椒雞燴飯', 100],
    ['m_3821b9ec', 'r_08fff4f0', '招牌三杯雞柳飯', 100],
    ['m_10e8e47c', 'r_08fff4f0', '厚切豬排飯', 120],
    ['m_eb11e429', 'r_08fff4f0', '三杯無骨雞腿飯', 120],
    ['m_3ca253ab', 'r_08fff4f0', '香煎大雞腿飯', 150],
    ['m_dfd76a8d', 'r_08fff4f0', '鱈魚排飯', 170],
    ['m_4e8faff1', 'r_08fff4f0', '現切鮭魚菲力飯', 210],
    ['m_bc919ea7', 'r_d6f7699b', '脆皮燒腩飯', 120],
    ['m_d734522d', 'r_d6f7699b', '脆皮烤鴨飯', 120],
    ['m_c5ce39bb', 'r_d6f7699b', '蜜汁叉燒飯', 120],
    ['m_67202456', 'r_d6f7699b', '鴨腿飯', 150],
    ['m_71448b77', 'r_d6f7699b', '燒鵝飯', 200],
    ['m_9d12ef66', 'r_d6f7699b', '招牌雙寶飯 五擇', 120],
  ].map(([id, restaurantId, name, price]) => ({
    id, restaurantId, name, price,
    optionGroupIds: restaurantId === 'r_08fff4f0' ? ['o_f6a022d1', 'o_d847cd38'] : ['o_1273bee4'],
  })),
  members: [
    'Ann', 'Celine Wang', 'Ivy', 'Mark', 'Meng', 'Mobius Wu', 'Niina_nn (恩)', 'ZN',
    '張嘉芳', 'Joanna', '丁丁', '李羅 (leelo)', '張睿平', '盧彥辰',
  ].map((name, i) => ({ id: 'u_' + i, name, active: true })),
  openSessions: [
    { sessionId: 's_open_1', date: '2026-10-08', mealType: '午餐', restaurantId: 'r_08fff4f0',
      restaurantName: '成美私廚料理', note: '11:30 前點完', openedBy: '盧彥辰' },
    { sessionId: 's_open_2', date: '2026-10-08', mealType: '晚餐', restaurantId: 'r_d6f7699b',
      restaurantName: '金大富', note: '', openedBy: 'Ann' },
  ],
  // 近 7 天已結單（已截止的新團 + 手機送出的舊團）
  history: [
    { sessionId: 's_h1', date: '2026-10-07', mealType: '晚餐', restaurantId: 'r_08fff4f0', restaurantName: '成美私廚料理',
      note: '要用跳的上樓', openedBy: '', status: 'submitted',
      lines: [{ member: '盧彥辰', itemName: '麻婆豆腐燴飯', options: '少飯/加荷包蛋', qty: 10, unitPrice: 105, subtotal: 1050, note: '不要豆腐' }] },
    { sessionId: 's_h2', date: '2026-10-06', mealType: '午餐', restaurantId: 'r_d6f7699b', restaurantName: '金大富',
      note: '', openedBy: 'Ann', status: 'closed',
      lines: [
        { member: 'Ann', itemName: '燒鵝飯', options: '半飯', qty: 1, unitPrice: 200, subtotal: 200, note: '' },
        { member: 'Mark', itemName: '蜜汁叉燒飯', options: '正常', qty: 1, unitPrice: 120, subtotal: 120, note: '' },
      ] },
  ],
  // 「這團 + 這個人」已存在的明細（選身份後帶入購物車）
  linesBySessionMember: {
    's_open_1|盧彥辰': [
      { itemName: '麻婆豆腐燴飯', options: '少飯/加荷包蛋', qty: 1, unitPrice: 105, subtotal: 105, note: '不要豆腐' },
    ],
    's_open_1|Ann': [
      { itemName: '蠔油雞肉燴飯', options: '正常', qty: 1, unitPrice: 100, subtotal: 100, note: '' },
    ],
    's_open_1|Mark': [
      { itemName: '厚切豬排飯', options: '少飯/加飯', qty: 1, unitPrice: 130, subtotal: 130, note: '' },
      { itemName: '蠔油雞肉燴飯', options: '正常', qty: 1, unitPrice: 100, subtotal: 100, note: '帶走' },
    ],
  },
};
