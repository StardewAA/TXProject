export const sourceColumns = [
  { dataIndex: 'sourceName', title: '采集源名称', width: 220 },
  { dataIndex: 'targetSite', title: '目标网站', width: 260 },
  { dataIndex: 'frequency', title: '采集频率', width: 120 },
  { dataIndex: 'lastRunTime', title: '最近执行', width: 160 },
  { dataIndex: 'successRate', title: '成功率', width: 90 },
];

export const sourceRows = [
  {
    id: 1,
    sourceName: '住房和城乡建设部-政策法规',
    targetSite: 'https://www.mohurd.gov.cn',
    frequency: '每日 02:00',
    lastRunTime: '2026-09-02 02:00',
    successRate: '98.6%',
    status: '启用',
  },
  {
    id: 2,
    sourceName: '应急管理部-安全生产通知',
    targetSite: 'https://www.mem.gov.cn',
    frequency: '每日 02:30',
    lastRunTime: '2026-09-02 02:30',
    successRate: '97.2%',
    status: '启用',
  },
  {
    id: 3,
    sourceName: '省住建厅-质量安全文件',
    targetSite: 'https://zjt.xxx.gov.cn',
    frequency: '每 6 小时',
    lastRunTime: '2026-09-01 23:00',
    successRate: '93.5%',
    status: '启用',
  },
  {
    id: 4,
    sourceName: '标准全文公开系统',
    targetSite: 'https://openstd.samr.gov.cn',
    frequency: '每周一 03:00',
    lastRunTime: '2026-08-31 03:00',
    successRate: '96.8%',
    status: '停用',
  },
];

export const taskColumns = [
  { dataIndex: 'sourceName', title: '采集源', width: 230 },
  { dataIndex: 'startTime', title: '开始时间', width: 160 },
  { dataIndex: 'endTime', title: '结束时间', width: 160 },
  { dataIndex: 'successCount', title: '成功条数', width: 100 },
  { dataIndex: 'failCount', title: '失败条数', width: 100 },
  { dataIndex: 'errorMsg', title: '异常信息' },
];

export const taskRows = [
  {
    id: 1,
    sourceName: '住房和城乡建设部-政策法规',
    startTime: '2026-09-02 02:00',
    endTime: '2026-09-02 02:12',
    successCount: 3,
    failCount: 0,
    errorMsg: '-',
    status: '成功',
  },
  {
    id: 2,
    sourceName: '应急管理部-安全生产通知',
    startTime: '2026-09-02 02:30',
    endTime: '2026-09-02 02:38',
    successCount: 1,
    failCount: 0,
    errorMsg: '-',
    status: '成功',
  },
  {
    id: 3,
    sourceName: '省住建厅-质量安全文件',
    startTime: '2026-09-02 05:00',
    endTime: '-',
    successCount: 2,
    failCount: 1,
    errorMsg: '目标页面出现验证码，已暂停',
    status: '运行中',
  },
  {
    id: 4,
    sourceName: '标准全文公开系统',
    startTime: '2026-08-31 03:00',
    endTime: '2026-08-31 03:15',
    successCount: 0,
    failCount: 3,
    errorMsg: '连接超时',
    status: '失败',
  },
];

export const resultColumns = [
  { dataIndex: 'title', title: '采集标题', width: 340 },
  { dataIndex: 'sourceName', title: '来源', width: 180 },
  { dataIndex: 'publishDate', title: '发布日期', width: 120 },
  { dataIndex: 'crawlTime', title: '抓取时间', width: 150 },
  { dataIndex: 'dedupeResult', title: '去重结果', width: 100 },
];

export const resultRows = [
  {
    id: 1,
    title: '关于做好建筑节能保温工程质量管理工作的通知',
    sourceName: '省住建厅-质量安全文件',
    publishDate: '2026-09-01',
    crawlTime: '2026-09-02 05:02',
    dedupeResult: '新增',
    status: '待审核',
  },
  {
    id: 2,
    title: '住建部关于加快推进既有建筑节能改造的通知',
    sourceName: '住房和城乡建设部-政策法规',
    publishDate: '2026-09-01',
    crawlTime: '2026-09-02 02:03',
    dedupeResult: '新增',
    status: '待审核',
  },
  {
    id: 3,
    title: '工贸企业粉尘防爆安全规定宣贯材料',
    sourceName: '应急管理部-安全生产通知',
    publishDate: '2026-08-28',
    crawlTime: '2026-09-02 02:31',
    dedupeResult: '重复',
    status: '重复',
  },
  {
    id: 4,
    title: '绿色低碳建材推广应用政策解读',
    sourceName: '住房和城乡建设部-政策法规',
    publishDate: '2026-08-25',
    crawlTime: '2026-09-01 02:02',
    dedupeResult: '新增',
    status: '通过',
  },
];

export const pushColumns = [
  { dataIndex: 'title', title: '政策标题', width: 340 },
  { dataIndex: 'subscriptionType', title: '订阅类型', width: 120 },
  { dataIndex: 'targetCount', title: '目标用户', width: 100 },
  { dataIndex: 'successCount', title: '成功推送', width: 100 },
  { dataIndex: 'pushTime', title: '推送时间', width: 150 },
];

export const pushRows = [
  {
    id: 1,
    title: '关于做好建筑节能保温工程质量管理工作的通知',
    subscriptionType: '建筑领域',
    targetCount: 68,
    successCount: 65,
    pushTime: '2026-09-02 06:00',
    status: '成功',
  },
  {
    id: 2,
    title: '住建部关于加快推进既有建筑节能改造的通知',
    subscriptionType: '标准更新',
    targetCount: 126,
    successCount: 121,
    pushTime: '2026-09-02 03:30',
    status: '部分成功',
  },
  {
    id: 3,
    title: '绿色低碳建材推广应用政策解读',
    subscriptionType: '企业订阅',
    targetCount: 88,
    successCount: 0,
    pushTime: '2026-09-01 08:00',
    status: '失败',
  },
];
