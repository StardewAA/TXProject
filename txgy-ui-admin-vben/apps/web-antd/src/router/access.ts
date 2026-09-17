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
      // 注入「AI对话助手」菜单项（前端演示用，无需后端配置）
      injectAiChatMenu(menus, accessStore.accessCodes);
      return convertServerMenuToRouteRecordStringComponent(
        filterMallPromotionMenus(filterHrmEmployeePortalRecruitMenus(menus)),
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
