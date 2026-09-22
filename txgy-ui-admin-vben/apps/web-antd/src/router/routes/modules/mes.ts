import type { RouteRecordRaw } from 'vue-router';

import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

const routes: RouteRecordRaw[] = [
  {
    path: '/mes',
    name: 'MesCenter',
    meta: {
      title: '智慧生产执行系统',
      icon: 'lucide:factory',
      keepAlive: true,
      hideInMenu: true,
    },
    children: [
      {
        path: 'wm/warehouse/location',
        name: 'MesWmLocation',
        meta: {
          title: '库区设置',
          activePath: '/mes/wm/warehouse',
        },
        component: () => import('#/views/mes/wm/warehouse/location/index.vue'),
      },
      {
        path: 'wm/warehouse/area',
        name: 'MesWmArea',
        meta: {
          title: '库位设置',
          activePath: '/mes/wm/warehouse',
        },
        component: () => import('#/views/mes/wm/warehouse/area/index.vue'),
      },
      {
        path: 'wm/barcode/config',
        name: 'MesWmBarcodeConfig',
        meta: {
          title: '条码配置',
          activePath: '/mes/wm/barcode',
        },
        component: () => import('#/views/mes/wm/barcode/config/index.vue'),
      },
      {
        path: 'pro/task/gantt-edit',
        name: 'MesProTaskGanttEdit',
        meta: {
          title: '甘特图编辑',
          activePath: '/mes/pro/task',
        },
        component: () => import('#/views/mes/pro/task/edit/index.vue'),
      },
      {
        /**
         * 报表配置中心（隐藏功能页，不在左侧菜单展示）
         *
         * 作为「生产执行报表 → 新增报表」按钮的落地页，复用原报表数据集页面。
         * 不依赖 system_menu 授权，避免非超管角色因菜单未授权而无法生成路由（404）。
         * 访问权限由下方 beforeEnter 依据权限码 report:dataset:create 校验。
         */
        path: 'report-dataset',
        name: 'MesReportDatasetConfig',
        meta: {
          title: '报表配置中心',
          hideInMenu: true,
          activePath: '/mes/report-center',
        },
        component: () => import('#/views/report/dataset/index.vue'),
        beforeEnter: () => {
          const accessStore = useAccessStore();
          // 进入报表配置中心 = 「报表配置中心」权限（report:dataset:query）
          // 能否「新增报表」由按钮 v-access 与后端 report:dataset:create 校验决定
          if (accessStore.accessCodes.includes('report:dataset:query')) {
            return true;
          }
          // 无权限用户不允许绕过入口直接进入
          message.warning('您没有「报表配置中心」的访问权限');
          return { path: '/mes/report-center', replace: true };
        },
      },
      {
        /**
         * 报表查看页（独立页面，非弹窗/非内嵌）
         *
         * 两个入口共用同一页面：
         *  - 报表配置中心「预览」→ from=dataset，按数据集口径取数
         *  - 生产执行报表「查看报表」→ from=center，按报表中心口径取数
         *
         * 两个入口的权限码不同，具备任意一个即可进入；
         * 页面内部再按来源与权限选择取数口径。
         */
        path: 'report-dataset/preview/:code',
        name: 'MesReportDatasetPreview',
        meta: {
          title: '报表查看',
          hideInMenu: true,
          activePath: '/mes/report-dataset',
        },
        component: () => import('#/views/report/dataset/preview.vue'),
        beforeEnter: () => {
          const accessStore = useAccessStore();
          const hasCenterAccess =
            accessStore.accessCodes.includes('report:center:query');
          const hasDatasetAccess =
            accessStore.accessCodes.includes('report:dataset:execute');
          if (hasCenterAccess || hasDatasetAccess) {
            return true;
          }
          message.warning('您没有「报表查看」的访问权限');
          return { path: '/mes/report-dataset', replace: true };
        },
      },
    ],
  },
];

export default routes;
