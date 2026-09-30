import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:layout-dashboard',
      order: -1,
      title: $t('page.dashboard.title'),
    },
    name: 'Dashboard',
    path: '/dashboard',
    children: [
      {
        name: 'Analytics',
        path: 'analytics',
        component: () => import('#/views/dashboard/analytics/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:area-chart',
          title: $t('page.dashboard.analytics'),
        },
      },
      {
        name: 'AiHome',
        path: 'ai-home',
        component: () => import('#/views/dashboard/ai-home/index.vue'),
        meta: {
          icon: 'tabler:ai',
          title: 'AI首页',
        },
      },
      {
        // 资源中心 - 文档资源库（菜单 API 注册了 dashboard/doc-list，路由直接渲染培训资源中心文档列表）
        path: 'doc-list',
        component: () => import('#/views/training/resource/doc-list/index.vue'),
        meta: {
          hideInMenu: true,
          icon: 'lucide:library-big',
          title: '文档资源库',
        },
        name: 'ResourceDocList',
      },
    ],
  },
  {
    name: 'Profile',
    path: '/profile',
    component: () => import('#/views/_core/profile/index.vue'),
    meta: {
      icon: 'ant-design:profile-outlined',
      title: $t('ui.widgets.profile'),
      hideInMenu: true,
    },
  },
];

export default routes;
