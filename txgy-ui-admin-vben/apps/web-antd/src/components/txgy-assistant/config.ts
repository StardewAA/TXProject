/**
 * 「AI 助手」首页配置
 *
 * 前台首页（/portal/home）与后台「概览 → AI首页」（/dashboard/ai-home）使用同一套
 * 助手交互（居中欢迎态 + 快捷提问 + 豆包式会话列表 + 微信风格气泡），差异只在三处：
 *   1. 历史会话缓存的 key；
 *   2. 输入框下方的快捷入口；
 *   3. 命中关键词后跳转的目标页面（前台跳 /portal/*，后台跳各业务模块菜单）。
 * 所以把这三项连同话术一起抽到这里，组件本身保持完全共用。
 */

/** 助手回答时给出的去向卡片 */
export interface DispatchTarget {
  /** 需要额外解释的入口（如产品参数），用 answer 覆盖默认话术 */
  answer?: string;
  /** 卡片副标题 */
  description: string;
  /**
   * 后台入口专用：目标页由后端菜单下发，账号没有该菜单时路由并不存在，
   * 命中不到路由就退回这个页面，避免把用户直接丢到 404。
   */
  fallbackPath?: string;
  /** 卡片标题 */
  label: string;
  /** 点击进入的路由地址 */
  path: string;
}

/** 输入框下方的快捷入口：点一下按 prompt 发问，并直接给出 target 卡片 */
export interface QuickChip {
  label: string;
  prompt: string;
  target?: DispatchTarget;
}

export interface AssistantConfig {
  /**
   * 关键词 → 去向卡片
   *
   * 接入大模型后，本项不再用来「编造回答」，而是：模型给出真实回答后，
   * 若用户提问命中了某条规则，就在回答下方附一张「直达入口」卡片，
   * 让用户从对话直接跳到对应模块（保留原有的导航价值）。
   */
  dispatchRules: { pattern: RegExp; target: DispatchTarget }[];
  /** 没命中关键词、且模型未给出可跳转入口时展示的「猜你想找」入口 */
  fallbackTargets: DispatchTarget[];
  /** 历史会话缓存的 localStorage key */
  historyKey: string;
  /** 快捷入口按钮 */
  quickChips: QuickChip[];
  /**
   * 对话使用的 AI 聊天角色名称（后端 ai_chat_role.name）
   *
   * 角色是「模型 + 提示词 + 工具/知识库」的载体，由后端配置：优先匹配公开角色，
   * 再匹配当前用户私有角色；都找不到时退回公开角色列表第一个。
   * 默认使用各租户通用的「平台通用助手」，避免各环境角色 id 不同导致的硬编码问题。
   */
  roleName: string;
}

/**
 * 平台通用助手角色名（后端 ai_chat_role.name）
 *
 * 前台首页与后台 AI首页共用同一个角色：不带业务工具与知识库，只做通用问答与导航；
 * 各租户由 sql/ai-common-assistant-role.sql 初始化（幂等，按租户）。
 */
const PLATFORM_ASSISTANT_ROLE_NAME = '平台通用助手';

/** 产品参数类问题的统一话术：完整产品库还没上线，先引导到 CMS 的资料与标准库 */
const productAnswer =
  '产品参数（规格、材质、执行标准等）现收录在「CMS系统」的资料与标准库中，可按产品名称或标准号检索；完整产品库仍在建设中，我先带你去检索已收录的标准与资料。';

/* ========== 员工端入口（前台首页 /portal/home） ========== */

const portalContent: DispatchTarget = {
  description: '按关键词检索政策法规、技术标准与行业资料。',
  label: 'CMS系统 · 政策法规智查',
  path: '/portal/content',
};

const portalProduct: DispatchTarget = {
  answer: productAnswer,
  description: '在产品资料与标准库中按名称、规格或标准号检索技术参数。',
  label: '产品参数与标准检索',
  path: '/portal/content',
};

const portalTraining: DispatchTarget = {
  description: '进入课程中心、考试认证、直播大厅与学习任务。',
  label: '云培训学院',
  path: '/portal/training',
};

const portalBidding: DispatchTarget = {
  description: '查看外部公告、本平台发布的项目与投标流程。',
  label: '招标投标',
  path: '/portal/bidding',
};

const portalRecruit: DispatchTarget = {
  description: '浏览职位、查看企业信息并投递简历。',
  label: '我要找工作',
  path: '/portal/recruit',
};

const portalProfile: DispatchTarget = {
  description: '查看个人档案、成长记录与已获学时。',
  label: '个人档案与成长',
  path: '/portal/profile',
};

const portalQuickChips: QuickChip[] = [
  { label: '政策咨询', prompt: '我想咨询防腐保温相关的政策法规与执行标准。' },
  { label: '产品参数查询', prompt: '帮我查一下防腐保温产品的技术参数。' },
  { label: '考试认证查询', prompt: '我想查询培训考试与技能认证。' },
  { label: '招标投标查询', prompt: '我想查询最新的招标投标公告。' },
];

/* ========== 后台入口（概览 → AI首页 /dashboard/ai-home） ========== */

const adminCrm: DispatchTarget = {
  description: '管理客户线索、客户档案、商机、合同与回款。',
  fallbackPath: '/portal/home',
  label: '客户关系管理 · 客户线索管理',
  path: '/crm/clue',
};

const adminMes: DispatchTarget = {
  description: '进入生产工作台，查看工单、报工、质检、设备与排班。',
  fallbackPath: '/portal/home',
  label: 'MES · 生产制造管理',
  path: '/mes/mes/home/index',
};

const adminWms: DispatchTarget = {
  description: '进入智慧仓储首页，查看库存、出入库与库存流水。',
  fallbackPath: '/portal/home',
  label: 'WMS · 仓储信息管理',
  path: '/wms/ai-center',
};

const adminCms: DispatchTarget = {
  description:
    '检索政策法规、技术标准、产品参数与行业资料，并维护对外发布内容。',
  fallbackPath: '/portal/content',
  label: 'CMS · 政策行业内容',
  path: '/cms/search',
};

const adminErp: DispatchTarget = {
  description: '进入 ERP 首页，处理采购、销售、库存与财务单据。',
  fallbackPath: '/portal/home',
  label: 'ERP · 企业资源规划',
  path: '/erp/home',
};

const adminTraining: DispatchTarget = {
  description: '进入云培训学院工作台，管理课程、考试认证与学习数据。',
  fallbackPath: '/portal/training',
  label: '云学院管理',
  path: '/training/workbench',
};

/** 招投标：保留为关键词命中入口（不在快捷按钮里，但仍可直接问） */
const adminBidding: DispatchTarget = {
  description: '查看招投标项目台账、标段台账与外部公告审核。',
  fallbackPath: '/portal/bidding',
  label: '招投标 · 项目管理',
  // 菜单里「项目与标段」本身就是一级目录（bidding/project），台账在它下面
  path: '/bidding/project/project',
};

const adminQuickChips: QuickChip[] = [
  { label: '客户线索管理', prompt: '我要管理客户线索。', target: adminCrm },
  { label: '生产制造管理', prompt: '我要管理生产制造业务。', target: adminMes },
  { label: '仓储信息管理', prompt: '我要查看仓储库存信息。', target: adminWms },
  {
    label: '政策行业内容',
    prompt: '我要查询政策法规与行业内容。',
    target: adminCms,
  },
  {
    label: '企业资源规划',
    prompt: '我要进入企业资源规划（ERP）。',
    target: adminErp,
  },
  { label: '云学院管理', prompt: '我要管理云培训学院。', target: adminTraining },
];

/** 前台首页（/portal/home） */
export const portalAssistantConfig: AssistantConfig = {
  dispatchRules: [
    {
      pattern: /(产品|参数|规格|型号|材质|指标|配件|物资)/u,
      target: portalProduct,
    },
    {
      pattern: /(政策|法规|标准|规范|条例|法律|合规|审查|资料|文件)/u,
      target: portalContent,
    },
    {
      pattern: /(培训|课程|学习|考试|证书|认证|学时|上岗|训练)/u,
      target: portalTraining,
    },
    {
      pattern: /(招标|投标|标书|公告|开标|中标|采购|项目)/u,
      target: portalBidding,
    },
    {
      pattern: /(职位|招聘|求职|简历|应聘|找工作|岗位|企业)/u,
      target: portalRecruit,
    },
    {
      pattern: /(档案|成长|积分|记录|我的|个人)/u,
      target: portalProfile,
    },
  ],
  fallbackTargets: [portalContent, portalTraining, portalBidding, portalRecruit],
  historyKey: 'txgy-portal-home-chat-v1',
  quickChips: portalQuickChips,
  roleName: PLATFORM_ASSISTANT_ROLE_NAME,
};

/** 后台「概览 → AI首页」（/dashboard/ai-home） */
export const adminAssistantConfig: AssistantConfig = {
  dispatchRules: [
    {
      pattern: /(线索|客户|商机|合同|回款|售后|报价|CRM)/iu,
      target: adminCrm,
    },
    {
      pattern: /(生产|制造|工单|报工|质检|设备|排班|工艺|MES)/iu,
      target: adminMes,
    },
    {
      pattern: /(仓储|库存|出入库|入库|出库|盘点|调拨|WMS)/iu,
      target: adminWms,
    },
    {
      pattern: /(政策|法规|标准|行业|资料|内容|审查|发布|CMS)/iu,
      target: adminCms,
    },
    {
      pattern: /(企业资源|ERP|采购|销售|往来|财务|结算)/iu,
      target: adminErp,
    },
    {
      pattern: /(培训|课程|学习|考试|证书|认证|云学院|学时)/iu,
      target: adminTraining,
    },
    {
      pattern: /(招标|投标|标书|中标|开标)/iu,
      target: adminBidding,
    },
  ],
  fallbackTargets: [
    adminCrm,
    adminMes,
    adminWms,
    adminCms,
    adminErp,
    adminTraining,
  ],
  historyKey: 'txgy-admin-ai-home-chat-v1',
  quickChips: adminQuickChips,
  roleName: PLATFORM_ASSISTANT_ROLE_NAME,
};