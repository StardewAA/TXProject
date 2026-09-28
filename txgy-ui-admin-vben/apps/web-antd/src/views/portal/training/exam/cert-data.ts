export const PANORAMA_DESC =
  '本平台职业认证覆盖工程建设与工业技术全领域，致力于提供领先的人才培养体系和认证标准，培养数字化时代的新型产业技术人才，构建良性的人才生态。';

/** 推荐认证进阶路线 */
export const LEVELS = [
  { code: '初级工', name: '初级工', desc: '掌握岗位基础理论与安全规范，可在指导下完成基础作业。' },
  { code: '中级工', name: '中级工', desc: '具备独立作业能力，能处理常见现场问题，作业质量稳定。' },
  { code: '高级工', name: '高级工', desc: '熟练掌握核心技能，能应对复杂工况并指导初中级人员。' },
  { code: '技师', name: '技师', desc: '精通工艺技术，能组织关键工序施工、质量把控与班组带教。' },
  { code: '高级技师', name: '高级技师', desc: '主导工艺改进与创新攻关，编制标准教材，培养人才梯队。' },
];

/** 岗位工种五大类（与进阶路径页数据同源） */
export const CERT_FIELDS = [
  { key: 'op', name: '操作人员', icon: '操', color: '#1257c9', desc: '防腐保温、焊接吊装等一线施工操作岗位' },
  { key: 'tc', name: '技术人员', icon: '技', color: '#26a65b', desc: '工艺方案、材料与技术管理岗位' },
  { key: 'qc', name: '质检检测人员', icon: '检', color: '#0096a0', desc: '无损检测、理化检验与工程验收岗位' },
  { key: 'mg', name: '管理人员', icon: '管', color: '#e6911e', desc: '班组、项目与培训管理岗位' },
  { key: 'sp', name: '支持人员', icon: '支', color: '#8250c8', desc: '安全、设备、材料与数字化支持岗位' },
] as const;

export type CertFieldKey = (typeof CERT_FIELDS)[number]['key'];

export const EXAM_STEPS = [
  { title: '预约与参加考试', text: '认证考试由在线考试系统与线下考点共同承接，不同类别与等级的考试要求不同。', action: '考试预约' },
  { title: '获取与验证证书', text: '通过认证考试后一个工作日即可下载电子证书，并可通过证书编号在线验真。', action: '获取与验证证书' },
  { title: '重认证', text: '证书内容不定期更新，重认证可确保专业能力实时匹配行业最新规范。', action: '重认证规则' },
];

export const RESOURCES = [
  { title: '查找培训', sub: '授权培训机构查询', action: '了解更多', link: '/portal/training/exam/training' },
  { title: '推荐自学', sub: '在线课程与题库', action: '了解更多', link: '/portal/training/exam/doc?title=推荐自学&sub=在线课程与题库&id=102' },
  { title: '参考书籍', sub: '《平台认证系列丛书》', action: '了解更多', link: '/portal/training/exam/books' },
];

export const NEWS = [
  { id: '001', title: '关于无损检测 III 级考试大纲升级的通知', date: '2026-09-20', sub: '考试大纲通知' },
  { id: '002', title: '在线监考考试服务上线公告', date: '2026-09-09', sub: '考试服务公告' },
  { id: '003', title: '职业认证架构刷新与重认证规则调整', date: '2026-06-06', sub: '认证规则调整' },
  { id: '004', title: '校企联合认证赋能人才培养方案发布', date: '2026-03-18', sub: '人才培养方案' },
];
