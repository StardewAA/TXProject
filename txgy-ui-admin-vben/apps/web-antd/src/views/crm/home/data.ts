/**
 * CRM 首页（菜单名：首页）静态配置
 *
 * 只放「不依赖运行时状态」的配置：顶部快捷新建入口、AI 助手示例问题。
 * 图表数据由 index.vue 直接调用 CRM 统计接口获取。
 */

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
 * 点击后跳转到「各自模块」的列表页并由该页打开新建弹窗，
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

/** AI 助手示例问题（点击示例即在内嵌对话中自动提问） */
export const AI_EXAMPLES = [
  '今日待办要联系的客户有哪些',
  '列出待处理的售后工单',
  '查询待回访任务',
  '本月哪些合同即将到期',
] as const;
