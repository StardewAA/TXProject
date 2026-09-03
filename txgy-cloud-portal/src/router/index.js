import { createRouter, createWebHistory } from 'vue-router';

import Home from '../views/Home.vue';
import Search from '../views/Search.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: Home },
    { path: '/search', name: 'search', component: Search },
  ],
});

export default router;
