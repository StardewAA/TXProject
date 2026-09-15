import { createRouter, createWebHistory } from 'vue-router';

import Detail from '../views/Detail.vue';
import Home from '../views/Home.vue';
import Login from '../views/Login.vue';
import Search from '../views/Search.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: Login, meta: { public: true } },
    { path: '/', name: 'home', component: Home },
    { path: '/search', name: 'search', component: Search },
    { path: '/content/:type/:id', name: 'content-detail', component: Detail },
  ],
});

router.beforeEach((to) => {
  const token = localStorage.getItem('cms_access_token');
  if (!to.meta.public && !token) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    };
  }
  if (to.name === 'login' && token) {
    return { name: 'home' };
  }
  return true;
});

export default router;
