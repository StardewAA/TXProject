import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [{
  name: 'CmsDocumentReview',
  path: '/cms/review',
  component: () => import('#/views/cms/review/index.vue'),
  meta: { title: '文档审查', icon: 'lucide:file-check', authority: ['super_admin'] },
}];
export default routes;
