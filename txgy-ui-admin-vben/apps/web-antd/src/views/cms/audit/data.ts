export const pendingColumns = [
  { dataIndex: 'contentType', title: '内容类型', width: 120 },
  { dataIndex: 'title', title: '内容标题', width: 320 },
  { dataIndex: 'submitter', title: '提交人', width: 120 },
  { dataIndex: 'submitTime', title: '提交时间', width: 150 },
  { dataIndex: 'waitDays', title: '等待天数', width: 100 },
];

export const pendingRows = [
  {
    id: 1,
    contentType: '标准规范',
    title: 'GB/T 8923.1-2011 表面清洁度目视评定',
    submitter: '运营-刘敏',
    submitTime: '2026-09-01 14:20',
    waitDays: 2,
    status: '待审核',
  },
  {
    id: 2,
    contentType: '材料参数',
    title: '环氧玻璃鳞片防腐涂料',
    submitter: '运营-陈晨',
    submitTime: '2026-09-01 10:05',
    waitDays: 2,
    status: '待审核',
  },
  {
    id: 3,
    contentType: '工艺案例',
    title: '深冷保冷层冷桥部位通病防治',
    submitter: '运营-王工',
    submitTime: '2026-08-31 17:42',
    waitDays: 3,
    status: '待审核',
  },
  {
    id: 4,
    contentType: '定额资料',
    title: '安全文明施工措施费计取规则',
    submitter: '运营-刘敏',
    submitTime: '2026-08-31 15:18',
    waitDays: 3,
    status: '待审核',
  },
  {
    id: 5,
    contentType: '政策资讯',
    title: '绿色低碳建材推广应用政策解读',
    submitter: '采集机器人',
    submitTime: '2026-09-01 02:00',
    waitDays: 2,
    status: '待审核',
  },
];

export const recordColumns = [
  { dataIndex: 'contentType', title: '内容类型', width: 120 },
  { dataIndex: 'title', title: '内容标题', width: 320 },
  { dataIndex: 'auditor', title: '审核人', width: 120 },
  { dataIndex: 'auditTime', title: '审核时间', width: 150 },
  { dataIndex: 'opinion', title: '审核意见' },
];

export const recordRows = [
  {
    id: 1,
    contentType: '标准规范',
    title: 'SH/T 3010-2013 涂料防腐蚀设计规范',
    auditor: '李审核',
    auditTime: '2026-08-12 16:30',
    opinion: '内容与公开标准一致，同意发布。',
    status: '通过',
  },
  {
    id: 2,
    contentType: '政策资讯',
    title: '工业设备及管道绝热节能标准宣贯工作安排',
    auditor: '李审核',
    auditTime: '2026-08-18 11:20',
    opinion: '政策来源有效，建议保留原文链接。',
    status: '通过',
  },
  {
    id: 3,
    contentType: '标准规范',
    title: 'SY/T 0407-2012 涂装前钢材表面处理规范',
    auditor: '赵审核',
    auditTime: '2026-08-28 09:40',
    opinion: '摘要引用条款不完整，请补充实施日期后重新提交。',
    status: '驳回',
  },
  {
    id: 4,
    contentType: '材料参数',
    title: '酚醛树脂衬里材料',
    auditor: '李审核',
    auditTime: '2026-08-27 15:00',
    opinion: '参数单位需要进一步核对。',
    status: '驳回',
  },
];

export const publishColumns = [
  { dataIndex: 'contentType', title: '内容类型', width: 120 },
  { dataIndex: 'title', title: '内容标题', width: 320 },
  { dataIndex: 'auditStatus', title: '审核状态', width: 110 },
  { dataIndex: 'operator', title: '最近操作人', width: 130 },
  { dataIndex: 'operateTime', title: '操作时间', width: 150 },
];

export const publishRows = [
  {
    id: 1,
    contentType: '标准规范',
    title: 'GB/T 4272-2008 设备及管道绝热技术通则',
    auditStatus: '审核通过',
    operator: '运营-王倩',
    operateTime: '2026-08-20 09:30',
    publishStatus: '已发布',
  },
  {
    id: 2,
    contentType: '材料参数',
    title: '复合硅酸盐保温材料',
    auditStatus: '审核通过',
    operator: '运营-陈晨',
    operateTime: '2026-08-19 14:10',
    publishStatus: '已发布',
  },
  {
    id: 3,
    contentType: '工艺案例',
    title: '管道聚氨酯发泡保温施工工法',
    auditStatus: '审核通过',
    operator: '运营-王工',
    operateTime: '2026-08-18 17:20',
    publishStatus: '已下架',
  },
  {
    id: 4,
    contentType: '标准规范',
    title: 'GB/T 8923.1-2011 表面清洁度目视评定',
    auditStatus: '审核通过',
    operator: '运营-刘敏',
    operateTime: '2026-09-02 08:50',
    publishStatus: '未发布',
  },
];
