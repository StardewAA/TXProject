export type TrainingModuleKey =
  | 'analytics'
  | 'cooperation'
  | 'course'
  | 'exam'
  | 'learning'
  | 'live'
  | 'plan'
  | 'resource'
  | 'vr';

export interface TrainingCapability {
  description: string;
  icon: string;
  name: string;
}

export interface TrainingMetric {
  label: string;
  suffix?: string;
  value: number;
}

export interface TrainingModuleDefinition {
  accent: string;
  capabilities: TrainingCapability[];
  description: string;
  icon: string;
  key: TrainingModuleKey;
  metrics: TrainingMetric[];
  name: string;
  number: string;
  path: string;
  stages: string[];
}

export const trainingModules: TrainingModuleDefinition[] = [
  {
    key: 'resource',
    number: '01',
    name: '资源中心',
    description: '统一沉淀企业培训内容与知识资产，建立标准、可复用、可追溯的资源库。',
    icon: 'lucide:library-big',
    accent: '#2563eb',
    path: '/training/resource',
    metrics: [
      { label: '资源总量', value: 0, suffix: '份' },
      { label: '本月新增', value: 0, suffix: '份' },
      { label: '待审核', value: 0, suffix: '项' },
      { label: '资源分类', value: 0, suffix: '个' },
    ],
    capabilities: [
      { name: '多媒体资源', description: '统一管理视频、文档、课件、图片和案例素材。', icon: 'lucide:files' },
      { name: '分类与标签', description: '按岗位、专业、工序和知识领域灵活组织资源。', icon: 'lucide:tags' },
      { name: '审核与发布', description: '通过审核流程控制资源质量和可见范围。', icon: 'lucide:file-check-2' },
      { name: '版本与版权', description: '保留资源版本、来源、授权和使用记录。', icon: 'lucide:history' },
    ],
    stages: ['资源目录与上传', '审核发布流程', '智能检索与推荐'],
  },
  {
    key: 'course',
    number: '02',
    name: '课程管理',
    description: '围绕课程设计、内容编排、发布运营和质量评价建立完整的课程生命周期。',
    icon: 'lucide:notebook-tabs',
    accent: '#7c3aed',
    path: '/training/course',
    metrics: [
      { label: '课程总数', value: 0, suffix: '门' },
      { label: '已发布', value: 0, suffix: '门' },
      { label: '待上架', value: 0, suffix: '门' },
      { label: '平均评分', value: 0, suffix: '分' },
    ],
    capabilities: [
      { name: '课程设计', description: '配置课程简介、对象、目标、学时和考核要求。', icon: 'lucide:square-pen' },
      { name: '章节编排', description: '组合视频、文档、练习和考试形成学习路径。', icon: 'lucide:list-tree' },
      { name: '发布运营', description: '管理上架范围、学习期限和课程推荐位。', icon: 'lucide:rocket' },
      { name: '质量评价', description: '汇总完成率、评价、反馈与内容改进建议。', icon: 'lucide:star' },
    ],
    stages: ['课程模型与章节', '发布及选课规则', '课程评价与迭代'],
  },
  {
    key: 'plan',
    number: '03',
    name: '培训管理',
    description: '覆盖需求收集、计划制定、报名组织、过程执行和培训复盘的管理闭环。',
    icon: 'lucide:calendar-range',
    accent: '#0891b2',
    path: '/training/plan',
    metrics: [
      { label: '年度计划', value: 0, suffix: '项' },
      { label: '进行中', value: 0, suffix: '项' },
      { label: '待报名', value: 0, suffix: '项' },
      { label: '覆盖人数', value: 0, suffix: '人' },
    ],
    capabilities: [
      { name: '需求与计划', description: '收集部门需求并形成年度、季度培训计划。', icon: 'lucide:clipboard-list' },
      { name: '报名与分班', description: '支持自主报名、指派学习、资格审核和分班。', icon: 'lucide:users-round' },
      { name: '过程执行', description: '管理签到、请假、作业、考核与异常提醒。', icon: 'lucide:list-checks' },
      { name: '效果评估', description: '按培训项目汇总满意度和成果转化数据。', icon: 'lucide:chart-column-big' },
    ],
    stages: ['培训计划与报名', '过程执行与签到', '培训评估与复盘'],
  },
  {
    key: 'learning',
    number: '04',
    name: '学员学习中心',
    description: '为学员集中呈现学习任务、课程进度、个人成果和持续成长路径。',
    icon: 'lucide:book-open-check',
    accent: '#16a34a',
    path: '/training/learning',
    metrics: [
      { label: '待学任务', value: 0, suffix: '项' },
      { label: '学习中', value: 0, suffix: '门' },
      { label: '已完成', value: 0, suffix: '门' },
      { label: '累计学时', value: 0, suffix: '小时' },
    ],
    capabilities: [
      { name: '我的任务', description: '聚合必修、选修、培训和考试任务。', icon: 'lucide:list-todo' },
      { name: '在线学习', description: '记录章节进度、播放时长和学习行为。', icon: 'lucide:play-square' },
      { name: '互动笔记', description: '支持笔记、问答、收藏和学习讨论。', icon: 'lucide:notebook-pen' },
      { name: '成长地图', description: '展示岗位能力要求和个人学习成长轨迹。', icon: 'lucide:route' },
    ],
    stages: ['任务中心与学习记录', '互动学习工具', '岗位成长地图'],
  },
  {
    key: 'exam',
    number: '05',
    name: '考试与认证',
    description: '构建题库、考试、阅卷、证书发放和到期复审的一体化认证体系。',
    icon: 'lucide:badge-check',
    accent: '#ea580c',
    path: '/training/exam',
    metrics: [
      { label: '考试任务', value: 0, suffix: '场' },
      { label: '题库试题', value: 0, suffix: '道' },
      { label: '待阅卷', value: 0, suffix: '份' },
      { label: '有效证书', value: 0, suffix: '张' },
    ],
    capabilities: [
      { name: '题库管理', description: '按知识点维护题型、难度、答案和解析。', icon: 'lucide:library' },
      { name: '考试组织', description: '支持随机组卷、防作弊、补考和考试监控。', icon: 'lucide:file-clock' },
      { name: '阅卷分析', description: '自动与人工阅卷结合，分析薄弱知识点。', icon: 'lucide:scan-text' },
      { name: '证书认证', description: '管理证书模板、发放、验真和到期复审。', icon: 'lucide:award' },
    ],
    stages: ['题库与考试组织', '阅卷及成绩分析', '证书与复审管理'],
  },
  {
    key: 'live',
    number: '06',
    name: '直播与讲师体系',
    description: '建设直播课堂和内外部讲师资源池，持续提升授课组织与知识传播效率。',
    icon: 'lucide:radio-tower',
    accent: '#db2777',
    path: '/training/live',
    metrics: [
      { label: '近期直播', value: 0, suffix: '场' },
      { label: '认证讲师', value: 0, suffix: '人' },
      { label: '待排课程', value: 0, suffix: '节' },
      { label: '直播观看', value: 0, suffix: '人次' },
    ],
    capabilities: [
      { name: '直播课堂', description: '完成排期、预约、开播、互动和回放沉淀。', icon: 'lucide:video' },
      { name: '讲师档案', description: '维护讲师资质、专长、课程和授课履历。', icon: 'lucide:presentation' },
      { name: '讲师认证', description: '支持申请、试讲、评审、定级和续聘。', icon: 'lucide:user-check' },
      { name: '授课评价', description: '统计课时、满意度和讲师综合评价。', icon: 'lucide:message-square-heart' },
    ],
    stages: ['直播课堂与回放', '讲师档案及认证', '授课评价与激励'],
  },
  {
    key: 'vr',
    number: '07',
    name: 'VR 虚拟仿真实操训练',
    description: '通过沉浸式场景复现高风险作业和关键工序，让实操训练安全、可重复、可量化。',
    icon: 'lucide:glasses',
    accent: '#4f46e5',
    path: '/training/vr',
    metrics: [
      { label: '训练场景', value: 0, suffix: '个' },
      { label: '实训任务', value: 0, suffix: '项' },
      { label: '设备在线', value: 0, suffix: '台' },
      { label: '实训人次', value: 0, suffix: '次' },
    ],
    capabilities: [
      { name: '场景资源库', description: '管理工艺、安全、设备和应急处置仿真场景。', icon: 'lucide:boxes' },
      { name: '实训任务', description: '按岗位配置训练步骤、目标和通过条件。', icon: 'lucide:gamepad-2' },
      { name: '设备接入', description: '统一管理 VR 终端、版本、状态和使用排期。', icon: 'lucide:monitor-cog' },
      { name: '过程评分', description: '记录操作轨迹、违规动作和综合实训成绩。', icon: 'lucide:gauge' },
    ],
    stages: ['VR 场景与设备接入', '实训任务和过程记录', '智能评分与复盘'],
  },
  {
    key: 'cooperation',
    number: '08',
    name: '校企合作与创新大赛',
    description: '连接院校、企业、专家和赛事项目，促进人才共育、技术创新与成果转化。',
    icon: 'lucide:handshake',
    accent: '#ca8a04',
    path: '/training/cooperation',
    metrics: [
      { label: '合作院校', value: 0, suffix: '所' },
      { label: '合作项目', value: 0, suffix: '项' },
      { label: '进行中赛事', value: 0, suffix: '场' },
      { label: '创新成果', value: 0, suffix: '项' },
    ],
    capabilities: [
      { name: '合作伙伴', description: '维护院校、企业、专家及合作协议档案。', icon: 'lucide:school' },
      { name: '合作项目', description: '管理联合培养、实习实训和产学研项目。', icon: 'lucide:folder-kanban' },
      { name: '创新大赛', description: '覆盖赛事发布、报名、作品、评审和排名。', icon: 'lucide:trophy' },
      { name: '成果转化', description: '沉淀优秀案例、专利、课题和转化进展。', icon: 'lucide:lightbulb' },
    ],
    stages: ['合作单位与项目', '赛事全流程管理', '成果库与转化跟踪'],
  },
  {
    key: 'analytics',
    number: '09',
    name: '数据统计与档案归档',
    description: '汇总培训投入、过程和成效数据，形成按人、岗、组织可追溯的培训档案。',
    icon: 'lucide:chart-no-axes-combined',
    accent: '#475569',
    path: '/training/analytics',
    metrics: [
      { label: '参训人次', value: 0, suffix: '次' },
      { label: '完成率', value: 0, suffix: '%' },
      { label: '累计学时', value: 0, suffix: '小时' },
      { label: '归档记录', value: 0, suffix: '份' },
    ],
    capabilities: [
      { name: '数据看板', description: '多维分析培训投入、覆盖、进度和考试成效。', icon: 'lucide:chart-spline' },
      { name: '个人档案', description: '汇聚课程、培训、考试、证书和实训记录。', icon: 'lucide:contact-round' },
      { name: '组织报表', description: '按公司、部门、岗位生成周期性统计报表。', icon: 'lucide:file-chart-column' },
      { name: '合规归档', description: '支持关键档案锁定、留痕、导出和保存期限。', icon: 'lucide:archive' },
    ],
    stages: ['核心指标与看板', '个人及组织档案', '合规报表与归档'],
  },
];

export function getTrainingModule(key: TrainingModuleKey) {
  const current = trainingModules.find((item) => item.key === key);
  if (!current) {
    throw new Error(`Unknown training module: ${key}`);
  }
  return current;
}
