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
      injectAiChatMenu(menus);
      return convertServerMenuToRouteRecordStringComponent(menus);
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
function injectAiChatMenu(menus: any[]) {
  const crmParent = findMenu(menus, (m) => m?.path === '/crm');
  const item = {
    id: 990001,
    name: 'AI对话助手',
    componentName: 'CrmAiChat',
    path: 'ai-chat',
    component: 'views/crm/ai-chat/index.vue',
    icon: 'lucide:bot',
    visible: true,
    sort: 999,
    keepAlive: true,
    parentId: crmParent ? crmParent.id : 0,
  };
  if (crmParent) {
    crmParent.children = crmParent.children ?? [];
    if (
      !crmParent.children.some(
        (c: any) => (c.componentName || c.name) === 'CrmAiChat',
      )
    ) {
      crmParent.children.push(item);
    }
  } else {
    // 兜底：作为顶级菜单，并包裹 BasicLayout
    menus.push({ ...item, component: 'BasicLayout', children: [{ ...item }] });
  }
}

function findMenu(
  list: any[],
  predicate: (m: any) => boolean,
): any | undefined {
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
