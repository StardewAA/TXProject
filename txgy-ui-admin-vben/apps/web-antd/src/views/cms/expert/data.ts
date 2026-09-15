export const reviewColumns = [
  { dataIndex: 'name', title: '专家姓名', width: 130 },
  { dataIndex: 'company', title: '所属单位', width: 220 },
  { dataIndex: 'serviceFields', title: '擅长方向' },
  { dataIndex: 'applyTime', title: '申请时间', width: 150 },
];

export const reviewRows = [
  {
    id: 1,
    name: '张工',
    company: '某石化设计院',
    serviceFields: '工业绝热设计、深冷保冷、标准解读',
    applyTime: '2026-09-01 10:20',
    status: '待审核',
  },
  {
    id: 2,
    name: '李高工',
    company: '某防腐工程公司',
    serviceFields: '涂装防腐、阴极保护、腐蚀失效分析',
    applyTime: '2026-08-31 16:10',
    status: '待审核',
  },
  {
    id: 3,
    name: '王教授',
    company: '某高校材料学院',
    serviceFields: '保温材料研发、材料选型、检测评价',
    applyTime: '2026-08-30 09:40',
    status: '通过',
  },
  {
    id: 4,
    name: '赵经理',
    company: '某施工企业',
    serviceFields: '外墙保温施工、防水节点处理',
    applyTime: '2026-08-28 14:30',
    status: '驳回',
  },
];

export const expertColumns = [
  { dataIndex: 'name', title: '专家姓名', width: 120 },
  { dataIndex: 'title', title: '职称', width: 100 },
  { dataIndex: 'company', title: '所属单位', width: 220 },
  { dataIndex: 'serviceFields', title: '服务方向' },
  { dataIndex: 'rating', title: '综合评分', width: 90 },
  { dataIndex: 'orderCount', title: '累计接单', width: 100 },
];

export const expertRows = [
  {
    id: 1,
    name: '张工',
    title: '高级工程师',
    company: '某石化设计院',
    serviceFields: '工业绝热设计、深冷保冷',
    rating: 4.9,
    orderCount: 86,
    status: '启用',
  },
  {
    id: 2,
    name: '李高工',
    title: '防腐专家',
    company: '某防腐工程公司',
    serviceFields: '涂装防腐、腐蚀失效分析',
    rating: 4.8,
    orderCount: 64,
    status: '启用',
  },
  {
    id: 3,
    name: '王教授',
    title: '教授',
    company: '某高校材料学院',
    serviceFields: '保温材料研发、选型评价',
    rating: 4.7,
    orderCount: 32,
    status: '停用',
  },
];

export const orderColumns = [
  { dataIndex: 'orderNo', title: '订单编号', width: 180 },
  { dataIndex: 'expertName', title: '服务专家', width: 120 },
  { dataIndex: 'userName', title: '咨询用户', width: 130 },
  { dataIndex: 'serviceType', title: '服务类型' },
  { dataIndex: 'amount', title: '订单金额', width: 110 },
  { dataIndex: 'createTime', title: '下单时间', width: 150 },
];

export const orderRows = [
  {
    id: 1,
    orderNo: 'ZX20260901001',
    expertName: '张工',
    userName: '防腐保温-周经理',
    serviceType: '方案审核',
    amount: '¥ 600',
    createTime: '2026-09-01 09:30',
    status: '服务中',
  },
  {
    id: 2,
    orderNo: 'ZX20260831018',
    expertName: '李高工',
    userName: '石化工程-林工',
    serviceType: '现场指导',
    amount: '¥ 1,200',
    createTime: '2026-08-31 15:10',
    status: '已完成',
  },
  {
    id: 3,
    orderNo: 'ZX20260828006',
    expertName: '王教授',
    userName: '新材料公司-郑总',
    serviceType: '材料选型',
    amount: '¥ 800',
    createTime: '2026-08-28 11:20',
    status: '待结算',
  },
  {
    id: 4,
    orderNo: 'ZX20260825003',
    expertName: '赵经理',
    userName: '建筑总包-吴工',
    serviceType: '施工咨询',
    amount: '¥ 500',
    createTime: '2026-08-25 14:50',
    status: '已关闭',
  },
];

export const settlementColumns = [
  { dataIndex: 'period', title: '结算周期', width: 120 },
  { dataIndex: 'expertName', title: '专家姓名', width: 120 },
  { dataIndex: 'orderCount', title: '订单数', width: 90 },
  { dataIndex: 'amount', title: '订单金额', width: 120 },
  { dataIndex: 'commission', title: '平台服务费', width: 120 },
  { dataIndex: 'settleAmount', title: '结算金额', width: 120 },
  { dataIndex: 'settleTime', title: '结算时间', width: 150 },
];

export const settlementRows = [
  {
    id: 1,
    period: '2026-08',
    expertName: '张工',
    orderCount: 12,
    amount: '¥ 8,600',
    commission: '¥ 860',
    settleAmount: '¥ 7,740',
    settleTime: '2026-09-02 10:00',
    status: '已结算',
  },
  {
    id: 2,
    period: '2026-08',
    expertName: '李高工',
    orderCount: 8,
    amount: '¥ 5,800',
    commission: '¥ 580',
    settleAmount: '¥ 5,220',
    settleTime: '2026-09-02 10:30',
    status: '已结算',
  },
  {
    id: 3,
    period: '2026-07',
    expertName: '王教授',
    orderCount: 5,
    amount: '¥ 4,000',
    commission: '¥ 400',
    settleAmount: '¥ 3,600',
    settleTime: '2026-08-05 09:00',
    status: '已驳回',
  },
];
