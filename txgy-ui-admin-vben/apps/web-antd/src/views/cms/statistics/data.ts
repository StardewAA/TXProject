export const contentStatColumns = [
  { dataIndex: 'contentType', title: '内容类型', width: 140 },
  { dataIndex: 'publishCount', title: '已发布数', width: 110 },
  { dataIndex: 'viewCount', title: '总浏览量', width: 120 },
  { dataIndex: 'downloadCount', title: '总下载量', width: 120 },
  { dataIndex: 'subscribeCount', title: '订阅量', width: 110 },
  { dataIndex: 'trend', title: '本周变化' },
];

export const contentStatRows = [
  {
    id: 1,
    contentType: '标准规范库',
    publishCount: 286,
    viewCount: '86,320',
    downloadCount: '12,860',
    subscribeCount: 982,
    trend: '+12.4%',
  },
  {
    id: 2,
    contentType: '材料参数库',
    publishCount: 324,
    viewCount: '52,180',
    downloadCount: '6,320',
    subscribeCount: 536,
    trend: '+8.1%',
  },
  {
    id: 3,
    contentType: '工艺案例库',
    publishCount: 156,
    viewCount: '38,460',
    downloadCount: '3,986',
    subscribeCount: 428,
    trend: '+15.7%',
  },
  {
    id: 4,
    contentType: '定额资料库',
    publishCount: 98,
    viewCount: '21,760',
    downloadCount: '8,126',
    subscribeCount: 612,
    trend: '+6.9%',
  },
  {
    id: 5,
    contentType: '政策资讯库',
    publishCount: 212,
    viewCount: '45,210',
    downloadCount: '1,286',
    subscribeCount: 1320,
    trend: '+21.3%',
  },
];

export const hotwordColumns = [
  { dataIndex: 'rank', title: '排名', width: 80 },
  { dataIndex: 'keyword', title: '检索热词', width: 220 },
  { dataIndex: 'searchCount', title: '检索次数', width: 120 },
  { dataIndex: 'rate', title: '占比', width: 110 },
  { dataIndex: 'rankChange', title: '较昨日', width: 110 },
];

export const hotwordRows = [
  { id: 1, rank: 1, keyword: '绝热工程', searchCount: 1862, rate: '8.2%', rankChange: '持平' },
  { id: 2, rank: 2, keyword: 'GB 50264', searchCount: 1580, rate: '6.9%', rankChange: '↑ 2' },
  { id: 3, rank: 3, keyword: '聚氨酯保温', searchCount: 1426, rate: '6.3%', rankChange: '↓ 1' },
  { id: 4, rank: 4, keyword: '深冷保冷', searchCount: 1188, rate: '5.2%', rankChange: '↑ 1' },
  { id: 5, rank: 5, keyword: '防火等级', searchCount: 1024, rate: '4.5%', rankChange: '持平' },
  { id: 6, rank: 6, keyword: '玻璃鳞片防腐', searchCount: 896, rate: '3.9%', rankChange: '↑ 3' },
  { id: 7, rank: 7, keyword: '外墙外保温', searchCount: 820, rate: '3.6%', rankChange: '↓ 2' },
  { id: 8, rank: 8, keyword: '人工单价', searchCount: 742, rate: '3.3%', rankChange: '持平' },
  { id: 9, rank: 9, keyword: '岩棉板', searchCount: 610, rate: '2.7%', rankChange: '↑ 1' },
  { id: 10, rank: 10, keyword: '涂装前处理', searchCount: 532, rate: '2.3%', rankChange: '↓ 3' },
];

export const consultStatColumns = [
  { dataIndex: 'date', title: '统计日期', width: 130 },
  { dataIndex: 'questionCount', title: '提问量', width: 110 },
  { dataIndex: 'accurateRate', title: '准确率', width: 110 },
  { dataIndex: 'unanswerRate', title: '无法回答率', width: 130 },
  { dataIndex: 'avgAnswerSeconds', title: '平均响应', width: 120 },
  { dataIndex: 'expertReferCount', title: '转专家次数', width: 110 },
];

export const consultStatRows = [
  {
    id: 1,
    date: '2026-09-02',
    questionCount: 186,
    accurateRate: '93.1%',
    unanswerRate: '3.8%',
    avgAnswerSeconds: 6.2,
    expertReferCount: 8,
  },
  {
    id: 2,
    date: '2026-09-01',
    questionCount: 174,
    accurateRate: '92.5%',
    unanswerRate: '4.2%',
    avgAnswerSeconds: 6.8,
    expertReferCount: 9,
  },
  {
    id: 3,
    date: '2026-08-31',
    questionCount: 161,
    accurateRate: '91.8%',
    unanswerRate: '4.6%',
    avgAnswerSeconds: 7.1,
    expertReferCount: 11,
  },
  {
    id: 4,
    date: '2026-08-30',
    questionCount: 142,
    accurateRate: '90.9%',
    unanswerRate: '5.1%',
    avgAnswerSeconds: 7.6,
    expertReferCount: 12,
  },
];
