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
      // 注入 CRM 前端菜单项（CRM 首页，无需后端配置）
      injectCrmMenus(menus, accessStore.accessCodes);
      // 先完成「后端菜单 → 路由记录」转换，再把 CRM 的扁平子菜单重组为分组：
      // 分组必须发生在转换之后，否则子菜单路径会被拼接成 /crm/<分组>/xxx，导致地址变化。
      return groupCrmSideMenus(
        convertServerMenuToRouteRecordStringComponent(
          filterMallPromotionMenus(
            filterHrmEmployeePortalRecruitMenus(transformWmsMenus(menus)),
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
 * 1. 删除旧的「WMS 首页」/「分析页」菜单（旧首页组件 views/wms/home/index.vue）。
 * 2. 把 AI 智能助手（/wms/ai-center）改名为「WMS 首页」，作为新的首页入口。
 */
function transformWmsMenus(menus: AppRouteRecordRaw[]): AppRouteRecordRaw[] {
  return menus.reduce<AppRouteRecordRaw[]>((result, menu) => {
    const normalizedPath = normalizeMenuPath(menu.path);
    const component = normalizeMenuPath(menu.component);
    const name = String(menu.name ?? '').trim();
    const title = String((menu.meta as Record<string, unknown>)?.title ?? '').trim();

    // 1. 移除旧的 WMS 首页
    const isOldWmsHome =
      normalizedPath === 'wms/home' ||
      component === 'views/wms/home/index.vue' ||
      name === 'WMS 首页' ||
      name === '分析页' ||
      title === 'WMS 首页' ||
      title === '分析页';
    if (isOldWmsHome) {
      return result;
    }

    // 2. 重命名 AI 智能助手为 WMS 首页
    const isAiAssistant =
      normalizedPath === 'wms/ai-center' ||
      component === 'views/wms/ai/index.vue' ||
      name === 'AI 智能助手' ||
      title === 'AI 智能助手';
    if (isAiAssistant) {
      menu = { ...menu };
      menu.name = 'WMS 首页';
      menu.meta = { ...(menu.meta ?? {}), title: 'WMS 首页' };
    }

    if (Array.isArray(menu.children)) {
      menu.children = transformWmsMenus(menu.children);
    }

    result.push(menu);
    return result;
  }, []);
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
 * 只描述「哪些菜单归到哪个分组」，不涉及任何路由数据。
 * 分组的落地放在路由转换之后（见 {@link groupCrmSideMenus}），
 * 因此子菜单的 path 保持绝对路径原值，页面地址与改造前完全一致。
 */
const CRM_SIDE_GROUPS: Array<{
  icon: string;
  key: string;
  paths: string[];
  title: string;
}> = [
  {
    key: 'business',
    title: 'CRM业务管理',
    icon: 'lucide:briefcase',
    paths: [
      '/crm/clue',
      '/crm/customer',
      '/crm/contact',
      '/crm/customer/pool',
      '/crm/business',
    ],
  },
  {
    key: 'contract-receivable',
    title: '合同与回款',
    icon: 'lucide:file-text',
    paths: [
      '/crm/contract',
      '/crm/receivable',
      '/crm/receivable-plan',
      '/crm/quotation',
    ],
  },
  {
    key: 'product-after-sale',
    title: '产品与售后',
    icon: 'lucide:package',
    paths: ['/crm/product', '/crm/after-sale'],
  },
];

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

  const groupedPaths = new Set<string>();
  const groups = CRM_SIDE_GROUPS.map((group, index) => {
    const members = children.filter((child) => {
      const path = String(child?.path ?? '');
      if (!group.paths.includes(path)) {
        return false;
      }
      groupedPaths.add(path);
      return true;
    });
    return {
      // 目录型节点：无组件，仅承载子菜单
      component: '',
      meta: {
        hideInMenu: false,
        icon: group.icon,
        // 排在「CRM 首页(-2) / 待办事项(0)」之后，按配置顺序递增
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

  // 未纳入分组的菜单保持原有层级与顺序（含 CRM 首页、待办事项等独立一级项）
  const rest = children.filter(
    (child) => !groupedPaths.has(String(child?.path ?? '')),
  );
  crm.children = [...rest, ...groups];
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
