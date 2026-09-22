/**
 * CRM 首页（工作台）静态配置
 *
 * 说明：本文件只放「不依赖运行时状态」的配置（分组、文案、跳转目标、紧急级别），
 * 计数用的 ref 与请求函数放在 index.vue 组装，避免把响应式状态混进配置。
 */

/** 待办紧急级别：urgent 红（需立即处理）/ warning 橙（尽快处理）/ normal 蓝（知晓即可） */
export type TodoLevel = 'normal' | 'urgent' | 'warning';

/** 单条待办 */
export interface TodoItemDef {
  /** 唯一键：同时作为计数 ref 的 key 与取数 loader 的 key */
  key: string;
  /** 待办名称 */
  name: string;
  /** 补充说明（鼠标悬浮提示，说明判定口径） */
  desc: string;
  /** 紧急级别 */
  level: TodoLevel;
  /** 点击跳转的页面路径 */
  path: string;
}

/** 待办分组 */
export interface TodoGroupDef {
  key: string;
  /** 分组标题 */
  title: string;
  /** 分组图标（iconify 名称） */
  icon: string;
  items: TodoItemDef[];
}

/** 级别样式与文案 */
export const TODO_LEVEL_META: Record<
  TodoLevel,
  { label: string; badge: string; dot: string }
> = {
  urgent: {
    label: '紧急',
    badge: 'bg-red-50 text-red-600 border-red-200',
    dot: 'bg-red-500',
  },
  warning: {
    label: '待处理',
    badge: 'bg-amber-50 text-amber-600 border-amber-200',
    dot: 'bg-amber-500',
  },
  normal: {
    label: '关注',
    badge: 'bg-blue-50 text-blue-600 border-blue-200',
    dot: 'bg-blue-500',
  },
};

/**
 * 全局待办汇总分组
 *
 * 覆盖需求指定的 6 类：线索 / 客户公海 / 商机 / 合同 / 工单 / 回款。
 * 各条目的计数口径与「待办事项」页面保持完全一致（同一批后端接口），
 * 只是把分散在 11 个分类里的关键项聚合到首页展示。
 */
export const TODO_GROUPS: TodoGroupDef[] = [
  {
    key: 'clue',
    title: '线索',
    icon: 'lucide:user-search',
    items: [
      {
        key: 'clueFollow',
        name: '待跟进线索',
        desc: '分配给我（含下属）且尚未跟进的线索',
        level: 'warning',
        path: '/crm/clue',
      },
    ],
  },
  {
    key: 'customerPool',
    title: '客户公海',
    icon: 'lucide:waves',
    items: [
      {
        key: 'customerPutPoolRemind',
        name: '待进入公海客户',
        desc: '达到公海规则、即将被回收进公海的客户',
        level: 'warning',
        path: '/crm/customer/pool',
      },
    ],
  },
  {
    key: 'business',
    title: '商机',
    icon: 'lucide:target',
    items: [
      {
        key: 'businessFollow',
        name: '我负责的商机',
        desc: '由我负责、尚未关闭的商机总数',
        level: 'normal',
        path: '/crm/business',
      },
    ],
  },
  {
    key: 'contract',
    title: '合同',
    icon: 'lucide:file-text',
    items: [
      {
        key: 'contractAudit',
        name: '待审核合同',
        desc: '提交后等待我审批的合同',
        level: 'urgent',
        path: '/crm/contract',
      },
      {
        key: 'contractRemind',
        name: '即将到期合同',
        desc: '临近到期日、需要续签或收尾的合同',
        level: 'warning',
        path: '/crm/contract',
      },
    ],
  },
  {
    key: 'ticket',
    title: '工单',
    icon: 'lucide:wrench',
    items: [
      {
        key: 'ticketOverdue',
        name: '工单超时预警',
        desc: '超过约定处理时限仍未闭环的售后工单',
        level: 'urgent',
        path: '/crm/after-sale/ticket',
      },
    ],
  },
  {
    key: 'receivable',
    title: '回款',
    icon: 'lucide:banknote',
    items: [
      {
        key: 'receivableAudit',
        name: '待审核回款',
        desc: '提交后等待我审批的回款单',
        level: 'urgent',
        path: '/crm/receivable',
      },
      {
        key: 'receivablePlanRemind',
        name: '待回款提醒',
        desc: '已到计划回款日、尚未回款或已逾期的计划',
        level: 'warning',
        path: '/crm/receivable-plan',
      },
    ],
  },
];

/** 顶部快捷操作 */
export interface QuickActionDef {
  key: string;
  label: string;
  /** 按钮图标（iconify 名称） */
  icon: string;
  /** 跳转目标：对应业务模块的列表页（携带 ?action=create，由列表页自动打开新建弹窗） */
  path: string;
  /** 按钮主色（tailwind 类名片段） */
  theme: string;
}

/**
 * 快捷「新建」入口 → 对应业务模块
 *
 * 说明：点击后跳转到「各自模块」的列表页并由该页打开新建弹窗，
 * 而不是在首页弹出一个与模块无关的统一表单 ——
 * 这样用户能同时获得该模块的列表上下文与完整功能（筛选、导入、一键识别等）。
 */
export const QUICK_ACTIONS: QuickActionDef[] = [
  {
    key: 'clue',
    label: '新增线索',
    icon: 'lucide:user-plus',
    path: '/crm/clue',
    theme: 'text-blue-600 bg-blue-50 hover:bg-blue-100',
  },
  {
    key: 'customer',
    label: '新增客户',
    icon: 'lucide:building-2',
    path: '/crm/customer',
    theme: 'text-emerald-600 bg-emerald-50 hover:bg-emerald-100',
  },
  {
    key: 'business',
    label: '新建商机',
    icon: 'lucide:trending-up',
    path: '/crm/business',
    theme: 'text-violet-600 bg-violet-50 hover:bg-violet-100',
  },
  {
    key: 'ticket',
    label: '新建工单',
    icon: 'lucide:wrench',
    path: '/crm/after-sale/ticket',
    theme: 'text-orange-600 bg-orange-50 hover:bg-orange-100',
  },
];

/** 今日需联系客户表格默认条数（首页卡片内不展示分页器，超出用「查看全部」进入待办页） */
export const TODAY_CONTACT_PAGE_SIZE = 5;

/** AI 助手示例问题（点击示例即唤起助手并自动提问） */
export const AI_EXAMPLES = [
  '今日待办要联系的客户有哪些',
  '列出待处理的售后工单',
  '查询待回访任务',
  '本月哪些合同即将到期',
] as const;
