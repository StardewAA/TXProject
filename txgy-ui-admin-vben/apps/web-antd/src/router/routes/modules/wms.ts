import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/wms/approval/detail',
    name: 'WmsApprovalDetail',
    component: () => import('#/views/bpm/processInstance/detail/index.vue'),
    meta: {
      title: 'WMS 审批详情',
      activePath: '/wms/approval/my',
      icon: 'ant-design:history-outlined',
      keepAlive: false,
      hideInMenu: true,
    },
    props: (route) => ({
      id: route.query.id,
      taskId: route.query.taskId,
      activityId: route.query.activityId,
    }),
  },
];

export default routes;
