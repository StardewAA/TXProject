import type {
  AppRouteRecordRaw,
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
} from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';
import { convertServerMenuToRouteRecordStringComponent } from '@vben/utils';

import { BasicLayout, IFrameView } from '#/layouts';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

/**
 * The mall backend still returns the complete, general-purpose promotion menu.
 *
 * This project is an industrial procurement mall, so the consumer-oriented
 * promotion entries are intentionally kept out of the admin navigation for
 * now.  We filter the menu at the edge instead of deleting the backend menu
 * records: the APIs and permission records remain available for a later phase,
 * and existing order/pricing code is not affected.
 */
const HIDDEN_MALL_PROMOTION_PATHS = new Set([
  'bargain',
  'brokerage',
  'brokerage-record',
  'brokerage-user',
  'brokerage-withdraw',
  'combination',
  'diy-page',
  'diy-template',
  'point-activity',
  'seckill',
]);

const HIDDEN_MALL_PROMOTION_NAMES = new Set([
  '砍价活动',
  '砍价商品',
  '砍价记录',
  '分销管理',
  '分销用户',
  '积分商城',
  '佣金记录',
  '佣金提现',
  '拼团活动',
  '拼团商品',
  '拼团记录',
  '秒杀活动',
  '秒杀商品',
  '秒杀时段',
  '商城装修',
  '装修模板',
  '装修页面',
]);

const SIMPLE_MALL_PROMOTION_TITLES: Record<string, string> = {
  优惠劵: '采购优惠券',
  优惠券: '采购优惠券',
  满减送: '采购满额优惠',
  限时折扣: '采购优惠',
};

/**
 * Menu responses are not completely uniform between deployed versions. Some
 * versions return a relative path (for example `seckill`), while others
 * return the already-expanded path (`/mall/promotion/seckill/activity`).
 * Keep the matching logic path-segment based so both forms are handled.
 */
const HIDDEN_MALL_PROMOTION_NAME_PARTS = [
  '秒杀',
  '砍价',
  '拼团',
  '分销',
  '积分商城',
  '佣金',
  '商城装修',
  '装修模板',
  '装修页面',
];

const HIDDEN_MALL_PROMOTION_COMPONENT_PARTS = [
  'mall/promotion/diy/',
  'mall/promotion/point/',
  'mall/trade/brokerage/',
];

const HIDDEN_HRM_EMPLOYEE_PORTAL_RECRUIT_MENU_ID = 1645;

function normalizeMenuPath(value: unknown): string {
  const withoutQuery = String(value ?? '').split(/[?#]/, 1)[0] ?? '';
  return withoutQuery
    .replaceAll('\\', '/')
    .trim()
    .replaceAll(/^\/+|\/+$/g, '')
    .toLowerCase();
}

function getMenuPathSegments(value: unknown): string[] {
  const normalized = normalizeMenuPath(value);
  return normalized ? normalized.split('/').filter(Boolean) : [];
}

function hasHiddenPromotionPath(value: unknown): boolean {
  return getMenuPathSegments(value).some((segment) =>
    HIDDEN_MALL_PROMOTION_PATHS.has(segment),
  );
}

function hasHiddenPromotionComponent(value: unknown): boolean {
  const normalized = normalizeMenuPath(value);
  return (
    hasHiddenPromotionPath(normalized) ||
    HIDDEN_MALL_PROMOTION_COMPONENT_PARTS.some((part) =>
      normalized.includes(part),
    )
  );
}

function hasMallPromotionContext(value: unknown): boolean {
  return getMenuPathSegments(value).some(
    (segment) =>
      segment === 'mall' ||
      segment === 'promotion' ||
      segment === 'trade' ||
      segment === 'brokerage',
  );
}

function hasHiddenPromotionName(value: unknown): boolean {
  const text = String(value ?? '').trim();
  return (
    HIDDEN_MALL_PROMOTION_NAMES.has(text) ||
    HIDDEN_MALL_PROMOTION_NAME_PARTS.some((part) => text.includes(part))
  );
}

function isHiddenMallPromotionMenu(
  menu: AppRouteRecordRaw,
  parentPaths: string[],
): boolean {
  const normalizedPath = normalizeMenuPath(menu.path);
  const component = normalizeMenuPath(menu.component);
  const extraMenuFields = menu as AppRouteRecordRaw & {
    label?: unknown;
    title?: unknown;
  };
  const labels = [
    menu.name,
    menu.componentName,
    menu.meta?.title,
    extraMenuFields.label,
    extraMenuFields.title,
  ];
  const hiddenPath = hasHiddenPromotionPath(normalizedPath);
  const hiddenComponent =
    hasHiddenPromotionComponent(component) ||
    hasHiddenPromotionComponent(normalizedPath);
  const hiddenName = labels.some((label) => hasHiddenPromotionName(label));
  const inMallPromotion =
    parentPaths.some((parentPath) => hasMallPromotionContext(parentPath)) ||
    hasMallPromotionContext(normalizedPath) ||
    hasMallPromotionContext(component);

  // Path/component aliases are unambiguous activity identifiers. Names are
  // scoped to the mall context to avoid hiding an unrelated module's page.
  return hiddenPath || hiddenComponent || (hiddenName && inMallPromotion);
}

function isHiddenHrmEmployeePortalRecruitMenu(
  menu: AppRouteRecordRaw,
  parentPaths: string[],
): boolean {
  const menuWithId = menu as AppRouteRecordRaw & { id?: number };
  const normalizedPath = normalizeMenuPath(menu.path);
  const isEmployeePortal = parentPaths.some(
    (parentPath) =>
      parentPath === 'portal' || parentPath.endsWith('/portal'),
  );
  return (
    menuWithId.id === HIDDEN_HRM_EMPLOYEE_PORTAL_RECRUIT_MENU_ID ||
    (isEmployeePortal &&
      normalizedPath === 'recruit' &&
      String(menu.name ?? '').trim() === '招聘应聘')
  );
}

function filterHrmEmployeePortalRecruitMenus(
  menus: AppRouteRecordRaw[],
  parentPaths: string[] = [],
): AppRouteRecordRaw[] {
  return menus.reduce<AppRouteRecordRaw[]>((result, menu) => {
    if (isHiddenHrmEmployeePortalRecruitMenu(menu, parentPaths)) {
      return result;
    }

    const nextMenu = { ...menu };
    if (Array.isArray(nextMenu.children)) {
      nextMenu.children = filterHrmEmployeePortalRecruitMenus(
        nextMenu.children,
        [...parentPaths, normalizeMenuPath(nextMenu.path)],
      );
    }
    result.push(nextMenu);
    return result;
  }, []);
}

/** Remove deferred promotion entries while preserving the rest of the menu. */
function filterMallPromotionMenus(
  menus: AppRouteRecordRaw[],
  parentPaths: string[] = [],
): AppRouteRecordRaw[] {
  const result: AppRouteRecordRaw[] = [];
  for (const menu of menus) {
    if (isHiddenMallPromotionMenu(menu, parentPaths)) {
      continue;
    }

    const nextMenu = { ...menu };
    const title = SIMPLE_MALL_PROMOTION_TITLES[nextMenu.name];
    if (title) {
      // `name` is also used as the route name when the backend does not
      // provide componentName. Preserve that stable route name while
      // changing only the label shown in the menu.
      if (!nextMenu.componentName) {
        nextMenu.componentName = nextMenu.name;
      }
      nextMenu.name = title;
    }

    const hadChildren =
      Array.isArray(nextMenu.children) && nextMenu.children.length > 0;
    if (Array.isArray(nextMenu.children)) {
      const nextParentPaths = [
        ...parentPaths,
        normalizeMenuPath(nextMenu.path),
      ];
      nextMenu.children = filterMallPromotionMenus(
        nextMenu.children,
        nextParentPaths,
      );
    }

    // Do not leave an empty “营销中心/分销管理” container after its
    // deferred children have been removed. Leaf pages without children are
    // always preserved.
    const normalizedPath = normalizeMenuPath(nextMenu.path);
    const isMallContainer =
      normalizedPath === 'promotion' ||
      normalizedPath.endsWith('/promotion') ||
      normalizedPath === 'trade' ||
      normalizedPath.endsWith('/trade') ||
      normalizedPath === 'mall' ||
      normalizedPath.endsWith('/mall');
    if (
      hadChildren &&
      nextMenu.children?.length === 0 &&
      !nextMenu.component &&
      isMallContainer
    ) {
      continue;
    }
    result.push(nextMenu);
  }
  return result;
}

async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = import.meta.glob('../views/**/*.vue');
  const accessStore = useAccessStore();

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      // 由于 txgy 通过 accessStore 读取，所以不在进行 message.loading 提示
      // 补充说明：accessStore.accessMenus 一开始是 AppRouteRecordRaw 类型（后端加载），后面被赋值成 MenuRecordRaw 类型（前端转换）
      const accessMenus = accessStore.accessMenus as AppRouteRecordRaw[];
      // 克隆一份，避免污染 store 中的原始菜单数据
      const menus = JSON.parse(
        JSON.stringify(accessMenus),
      ) as AppRouteRecordRaw[];
      // 1) 注入 CRM 首页菜单项（无需后端配置）
      injectCrmMenus(menus, accessStore.accessCodes);
      // 先完成「后端菜单 → 路由记录」转换，再把 CRM 的扁平子菜单重组为分组：
      // 分组必须发生在转换之后，否则子菜单路径会被拼接成 /crm/<分组>/xxx，导致地址变化。
      // 注入「AI对话助手」菜单项（前端演示用，无需后端配置）
      injectAiChatMenu(menus, accessStore.accessCodes);

      // 2) 转换与过滤：MES / WMS 变换 → 菜单过滤 → 路由记录转换 → CRM 分组
      //    注意分组必须在 convert 之后：否则子菜单路径会被拼成 /crm/<分组>/xxx，地址就变了
      return renameTopMenus(
        groupCrmSideMenus(
          convertServerMenuToRouteRecordStringComponent(
            filterMallPromotionMenus(
              filterHrmEmployeePortalRecruitMenus(
                transformMesMenus(transformWmsMenus(menus)),
              ),
            ),
          ),
        ),
      );
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
}

/**
 * WMS 菜单改造：
 * 将历史菜单中的 WMS 首页入口统一指向 AI 首页，避免旧数据库菜单导致入口消失。
 */
function transformWmsMenus(
  menus: AppRouteRecordRaw[],
  inWms = false,
): AppRouteRecordRaw[] {
  const classify = (menu: AppRouteRecordRaw) => {
    const path = normalizeMenuPath(menu.path);
    const component = normalizeMenuPath(menu.component);
    const root = path === 'wms';
    const scoped = inWms || root || path.startsWith('wms/') || component.startsWith('views/wms/');
    const labels = [menu.name, menu.meta?.title];
    const ai = scoped && (path === 'wms/ai-center' || (inWms && path === 'ai-center') || component === 'views/wms/ai/index.vue' || labels.includes('AI 智能助手'));
    const old = scoped && !root && (path === 'wms/home' || (inWms && path === 'home') || component === 'views/wms/home/index.vue' || labels.some((label) => ['WMS 首页', '仓储首页', '分析页'].includes(String(label))));
    return { root, scoped, home: ai || old, ai };
  };
  // Prefer an existing AI entry so its id, permission metadata and componentName survive.
  const preferredHome = menus.find((menu) => classify(menu).ai) ?? menus.find((menu) => classify(menu).home);
  return menus.flatMap((original) => {
    const kind = classify(original);
    if (kind.home && original !== preferredHome) return [];
    let menu = { ...original };
    if (kind.root || kind.home) {
      menu.componentName = menu.componentName || menu.name;
      menu.name = kind.root ? '智慧仓储系统' : '仓储首页';
      menu.meta = { ...menu.meta, title: menu.name };
    }
    if (kind.home) {
      menu.path = inWms ? 'ai-center' : '/wms/ai-center';
      menu.component = 'views/wms/ai/index.vue';
    }
    if (Array.isArray(menu.children)) {
      menu.children = transformWmsMenus(menu.children, kind.scoped);
    }
    return [menu];
  });
}

/**
 * MES 菜单改造：
 * 与 WMS（transformWmsMenus → 「智慧仓储系统 / 仓储首页」）保持同一套处理逻辑，
 * 在路由层为 MES 的根菜单与首页改对外显示名：
 *   - 根菜单 → 「智慧生产执行系统」；首页工作台 → 「生产工作台」；
 *   - 仅改显示名（menu.name / meta.title），route.name 与权限标识保持不变；
 *   - 不落库，避免改 system_menu 后还需清理 Redis 菜单缓存。
 * 识别信号：根菜单看 path / id / 名称，首页看 path / component / 名称。
 */
function transformMesMenus(menus: AppRouteRecordRaw[]): AppRouteRecordRaw[] {
  const MES_ROOT_NAME = '智慧生产执行系统';
  const MES_HOME_NAME = '生产工作台';
  const MES_ROOT_MENU_ID = 5100;
  const MES_ROOT_NAMES = ['MES系统', 'MES 系统'];
  const MES_HOME_NAMES = ['MES 工作台', 'MES工作台', 'MES 首页', 'MES首页'];
  return menus.map((original) => {
    const path = normalizeMenuPath(original.path);
    const component = normalizeMenuPath(original.component);
    const id = (original as AppRouteRecordRaw & { id?: number }).id;
    const name = String(original.name ?? '').trim();
    const isMesRoot =
      path === 'mes' ||
      path === 'mes-system' ||
      id === MES_ROOT_MENU_ID ||
      MES_ROOT_NAMES.includes(name);
    const isMesHome =
      !isMesRoot &&
      (path === 'mes/home' ||
        component.includes('mes/home') ||
        MES_HOME_NAMES.includes(name));

    const menu = { ...original };
    if (isMesRoot || isMesHome) {
      menu.componentName = menu.componentName || menu.name;
      menu.name = isMesRoot ? MES_ROOT_NAME : MES_HOME_NAME;
      menu.meta = { ...menu.meta, title: menu.name };
    }
    if (Array.isArray(menu.children)) {
      menu.children = transformMesMenus(menu.children);
    }
    return menu;
  });
}

/**
 * 在后端菜单中注入 CRM 首页菜单项。
 *
 * 说明：AI 对话助手**不再**作为侧边栏菜单项注入（已改为 CRM 首页内的弹窗唤起，
 * 侧边栏不再展示该入口）。整页版助手组件仍保留在 views/crm/ai-chat，
 * 供首页的 AI 弹窗复用，其 AI 接口、权限与提示词逻辑均未改动。
 *
 * 找到 CRM 中心（path 为 /crm 的节点），作为其子项挂载；
 * 若未找到则挂载为顶级菜单，保证功能可见。
 */
function injectCrmMenus(
  menus: AppRouteRecordRaw[],
  permissions: unknown[] | null | undefined,
) {
  const crmParent = findMenu(menus, (m) => m?.path === '/crm');
  const hasCrmPermission = (permissions ?? []).some((permission) =>
    String(permission ?? '').startsWith('crm:'),
  );
  if (!hasCrmPermission) {
    return;
  }
  const homeItem = {
    id: 990_002,
    name: 'CRM 首页',
    componentName: 'CrmHome',
    path: 'home',
    component: 'views/crm/home/index.vue',
    icon: 'lucide:home',
    meta: {},
    visible: true,
    // 排序值设为 -2：CRM 首页固定排在最前（「待办事项」sort=0）
    sort: -2,
    keepAlive: true,
    parentId: crmParent ? crmParent.id : 0,
  };
  if (crmParent) {
    crmParent.children = crmParent.children ?? [];
    if (
      !crmParent.children.some(
        (c) => (c.componentName || c.name) === homeItem.componentName,
      )
    ) {
      // 同时插入数组首位，兼容不按 sort 排序的渲染场景
      crmParent.children.unshift(homeItem);
    }
  } else {
    // 兜底：作为顶级菜单，并包裹 BasicLayout
    menus.push({ ...homeItem, component: 'BasicLayout', children: [{ ...homeItem }] });
  }
}

/**
 * CRM 侧边栏二级分组配置
 *
 * 只描述「哪些菜单归到哪个分组、组内按什么顺序展示」，不涉及任何路由数据。
 * 分组的落地放在路由转换之后（见 {@link groupCrmSideMenus}），
 * 因此子菜单的 path 保持绝对路径原值，页面地址与改造前完全一致。
 *
 * 说明：「售后管理」自身是目录（下含质保档案 / 回访计划 / 任务 / 记录 / 问题工单 / 售后设置），
 * 按需求作为独立一级项展示，不并入分组。
 */
const CRM_SIDE_GROUPS: Array<{
  icon: string;
  key: string;
  members: Array<{ order: number; path: string }>;
  title: string;
}> = [
  {
    key: 'customer-resource',
    title: '客户资源管理',
    icon: 'lucide:users',
    members: [
      { path: '/crm/clue', order: 10 },
      { path: '/crm/customer', order: 20 },
      { path: '/crm/contact', order: 30 },
      { path: '/crm/customer/pool', order: 40 },
    ],
  },
  {
    key: 'sales-business',
    title: '销售业务管理',
    icon: 'lucide:briefcase',
    members: [
      { path: '/crm/product', order: 10 },
      { path: '/crm/business', order: 20 },
      { path: '/crm/quotation', order: 30 },
      { path: '/crm/contract', order: 40 },
      { path: '/crm/receivable', order: 50 },
      { path: '/crm/receivable-plan', order: 60 },
    ],
  },
];

/**
 * CRM 一级菜单重命名（只改菜单展示名，路由名与页面地址都不变）
 */
const CRM_MENU_TITLE_OVERRIDES: Record<string, string> = {
  '/crm/backlog': '待办中心',
  '/crm/home': '首页',
};

/**
 * 顶级菜单重命名（只覆盖菜单展示名 meta.title，不动 path / name，路由名与地址不变）
 *
 * 与 {@link transformWmsMenus} 中「AI 智能助手 → WMS 首页」的改名保持同一套做法：
 * 菜单文案属于后端 system_menu 数据，这里只按前端展示需要做覆盖，
 * 不改数据库、不影响其他应用与权限。
 */
const TOP_MENU_TITLE_OVERRIDES: Record<string, string> = {
  '/crm': '客户关系管理系统',
};

/**
 * CRM 一级菜单展示顺序覆盖
 *
 * 目的：按需求顺序排列（CRM 首页 → 待办中心 → 客户资源管理 → 销售业务管理 → 售后管理 → …）。
 * - 待办中心：后端 sort=0，但 0 在渲染链路里会被当成"未设置"而落到最后，改为 1；
 * - 售后管理：后端 sort=90，会排到两个分组（100/101）之前，改为 102。
 */
const CRM_MENU_ORDER_OVERRIDES: Record<string, number> = {
  '/crm/backlog': 1,
  '/crm/after-sale': 102,
};

/**
 * 把 CRM 下扁平的子菜单重组为「分组（可展开/折叠）+ 子菜单」两层结构
 *
 * 关键点：
 * 1. 必须在 convertServerMenuToRouteRecordStringComponent **之后**执行：
 *    此时子菜单的 path 已是绝对路径（/crm/clue 等），本函数只是往菜单树里插入容器节点，
 *    不参与任何路径拼接，因此「页面跳转与路由地址完全不变」；
 *    （若在转换前做嵌套，框架会把子路径拼成 /crm/<分组>/xxx，地址就变了）
 * 2. 未被分组的菜单（CRM 首页、待办事项、全局检索、数据统计、系统配置等）保持原样，
 *    仍作为 CRM 的一级项展示，功能入口不会丢失；
 * 3. 分组节点 component 为空（目录型，与后端目录菜单一致），只负责承载与展开子菜单。
 */
function groupCrmSideMenus(menus: any[]): any[] {
  const crm = menus.find((menu) => menu?.path === '/crm');
  const children: any[] = Array.isArray(crm?.children) ? crm.children : [];
  if (!crm || children.length === 0) {
    return menus;
  }

  // 1. 菜单重命名 / 展示顺序覆盖（只动 meta，不动 path 与 name，路由与跳转不受影响）
  for (const child of children) {
    const path = String(child?.path ?? '');
    const title = CRM_MENU_TITLE_OVERRIDES[path];
    if (title) {
      child.meta = { ...(child.meta ?? {}), title };
    }
    const order = CRM_MENU_ORDER_OVERRIDES[path];
    if (order !== undefined) {
      child.meta = { ...(child.meta ?? {}), order };
    }
  }

  // 2. 分组：按配置把成员挂到分组容器下，并按需求顺序重排组内菜单
  const groupedPaths = new Set<string>();
  const groups = CRM_SIDE_GROUPS.map((group, index) => {
    const members = group.members
      .map((member) => {
        const child = children.find(
          (c) => String(c?.path ?? '') === member.path,
        );
        if (!child) {
          return undefined;
        }
        groupedPaths.add(member.path);
        // 组内顺序以配置为准（覆盖后端 sort），仅影响菜单展示顺序
        child.meta = { ...(child.meta ?? {}), order: member.order };
        return child;
      })
      .filter((child) => child !== undefined);

    return {
      // 目录型节点：无组件，仅承载子菜单
      component: '',
      meta: {
        hideInMenu: false,
        icon: group.icon,
        // 排在「CRM 首页(-2) / 待办中心(0)」之后，按配置顺序递增
        order: 100 + index,
        title: group.title,
      },
      name: `crmGroup${index}`,
      path: `/crm/group-${group.key}`,
      children: members,
    };
  }).filter((group) => group.children.length > 0);

  if (groups.length === 0) {
    return menus;
  }

  // 未纳入分组的菜单保持原有层级与顺序（含 CRM 首页、待办中心、售后管理、数据统计等独立一级项）
  const rest = children.filter(
    (child) => !groupedPaths.has(String(child?.path ?? '')),
  );
  crm.children = [...rest, ...groups];
  return menus;
}

/**
 * 在后端菜单中注入「AI对话助手」菜单项（前端演示用）。
 * 找到 CRM 中心（path 为 /crm 的节点），作为其子项挂载；
 * 若未找到则挂载为顶级菜单，保证功能可见。
 */
function injectAiChatMenu(
  menus: AppRouteRecordRaw[],
  permissions: unknown[] | null | undefined,
) {
  const crmParent = findMenu(menus, (m) => m?.path === '/crm');
  const hasCrmPermission = (permissions ?? []).some((permission) =>
    String(permission ?? '').startsWith('crm:'),
  );
  if (!hasCrmPermission) {
    return;
  }
  const item = {
    id: 990_001,
    name: 'AI对话助手',
    componentName: 'CrmAiChat',
    path: 'ai-chat',
    component: 'views/crm/ai-chat/index.vue',
    icon: 'crmai:avatar',
    meta: {},
    visible: true,
    // 排序值设为 -1，保证排在 CRM 所有菜单最前面（第一个是「待办事项」sort=0）
    sort: -1,
    keepAlive: true,
    parentId: crmParent ? crmParent.id : 0,
  };
  if (crmParent) {
    crmParent.children = crmParent.children ?? [];
    if (
      !crmParent.children.some(
        (c) => (c.componentName || c.name) === 'CrmAiChat',
      )
    ) {
      // 同时插入数组首位，兼容不按 sort 排序的渲染场景
      crmParent.children.unshift(item);
    }
  } else {
    // 兜底：作为顶级菜单，并包裹 BasicLayout
    menus.push({ ...item, component: 'BasicLayout', children: [{ ...item }] });
  }
}

/**
 * 顶级菜单重命名（按 {@link TOP_MENU_TITLE_OVERRIDES} 覆盖展示名）
 *
 * 在路由转换完成之后执行，只改 meta.title：
 * 菜单标题取自 meta.title，路由名取自 name / componentName，
 * 因此改名不会影响已有的路由跳转（例如按路由名 push）。
 */
function renameTopMenus(menus: any[]): any[] {
  for (const menu of menus) {
    const title = TOP_MENU_TITLE_OVERRIDES[String(menu?.path ?? '')];
    if (title) {
      menu.meta = { ...(menu.meta ?? {}), title };
    }
  }
  return menus;
}

function findMenu(
  list: AppRouteRecordRaw[],
  predicate: (m: AppRouteRecordRaw) => boolean,
): AppRouteRecordRaw | undefined {
  for (const m of list) {
    if (predicate(m)) return m;
    if (m.children?.length) {
      const found = findMenu(m.children, predicate);
      if (found) return found;
    }
  }
  return undefined;
}

export { generateAccess };
