export const knowledgeColumns = [
  { dataIndex: 'name', title: '知识源名称', width: 260 },
  { dataIndex: 'sourceType', title: '来源类型', width: 110 },
  { dataIndex: 'chunks', title: '切片数', width: 90 },
  { dataIndex: 'updatedAt', title: '更新时间', width: 150 },
  { dataIndex: 'owner', title: '维护人', width: 120 },
];

export const knowledgeRows = [
  {
    id: 1,
    name: 'GB/T 4272-2008 标准全文',
    sourceType: '文档',
    chunks: 42,
    updatedAt: '2026-08-20 09:30',
    owner: '运营-刘敏',
    status: '已就绪',
  },
  {
    id: 2,
    name: '常用行业政策问答 FAQ',
    sourceType: 'FAQ',
    chunks: 128,
    updatedAt: '2026-08-26 16:10',
    owner: '知识运营组',
    status: '已就绪',
  },
  {
    id: 3,
    name: '住建部政策采集来源',
    sourceType: '爬虫',
    chunks: 65,
    updatedAt: '2026-09-02 02:00',
    owner: '采集机器人',
    status: '同步中',
  },
  {
    id: 4,
    name: '工艺施工工法手册扫描件',
    sourceType: '文档',
    chunks: 0,
    updatedAt: '2026-09-01 11:00',
    owner: '运营-王工',
    status: '解析失败',
  },
  {
    id: 5,
    name: '材料参数公开目录',
    sourceType: '文档',
    chunks: 86,
    updatedAt: '2026-08-28 15:20',
    owner: '运营-陈晨',
    status: '已停用',
  },
];

export const chatColumns = [
  { dataIndex: 'userName', title: '用户', width: 140 },
  { dataIndex: 'sessionTitle', title: '会话标题', width: 320 },
  { dataIndex: 'messageCount', title: '消息数', width: 90 },
  { dataIndex: 'startTime', title: '开始时间', width: 150 },
  { dataIndex: 'lastTime', title: '最后提问', width: 150 },
];

export const chatRows = [
  {
    id: 1,
    userName: '防腐保温-周经理',
    sessionTitle: '聚氨酯保温管道施工温度要求',
    messageCount: 12,
    startTime: '2026-09-02 09:12',
    lastTime: '2026-09-02 09:20',
    status: '正常',
  },
  {
    id: 2,
    userName: '石化工程-林工',
    sessionTitle: 'SH/T 3010 标准适用场景',
    messageCount: 8,
    startTime: '2026-09-01 16:40',
    lastTime: '2026-09-01 16:55',
    status: '标记劣质',
  },
  {
    id: 3,
    userName: '建筑总包-吴工',
    sessionTitle: '外墙保温燃烧等级怎么选',
    messageCount: 6,
    startTime: '2026-09-01 14:05',
    lastTime: '2026-09-01 14:20',
    status: '正常',
  },
  {
    id: 4,
    userName: '新材料公司-郑总',
    sessionTitle: '深冷保冷材料推荐',
    messageCount: 15,
    startTime: '2026-08-31 10:10',
    lastTime: '2026-08-31 10:35',
    status: '待优化',
  },
];

export const projectColumns = [
  { dataIndex: 'projectNo', title: '项目编号', width: 170 },
  { dataIndex: 'customerName', title: '客户名称', width: 180 },
  { dataIndex: 'projectType', title: '项目类型' },
  { dataIndex: 'expertName', title: '承接专家', width: 130 },
  { dataIndex: 'amount', title: '项目金额', width: 120 },
  { dataIndex: 'createTime', title: '创建时间', width: 150 },
];

export const projectRows = [
  {
    id: 1,
    projectNo: 'CUSTOM-20260901-01',
    customerName: '某石化集团',
    projectType: '保温方案定制',
    expertName: '张工',
    amount: '¥ 6,000',
    createTime: '2026-09-01 10:20',
    status: '服务中',
  },
  {
    id: 2,
    projectNo: 'CUSTOM-20260830-06',
    customerName: '某海洋工程公司',
    projectType: '深冷保冷方案',
    expertName: '王教授',
    amount: '¥ 8,000',
    createTime: '2026-08-30 15:30',
    status: '待报价',
  },
  {
    id: 3,
    projectNo: 'CUSTOM-20260826-02',
    customerName: '某涂装企业',
    projectType: '涂装质量整改方案',
    expertName: '李高工',
    amount: '¥ 4,500',
    createTime: '2026-08-26 11:00',
    status: '已完成',
  },
];
