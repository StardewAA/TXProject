import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { tokenStorage } from '@/api/http'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MallLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
      { path: 'shops', name: 'shops', component: () => import('@/views/ShopsView.vue') },
      { path: 'shops/:id', name: 'shop-detail', component: () => import('@/views/ShopDetailView.vue') },
      { path: 'categories', name: 'categories', component: () => import('@/views/CategoriesView.vue') },
      { path: 'search', name: 'search', component: () => import('@/views/ProductListView.vue') },
      { path: 'goods', name: 'goods-list', component: () => import('@/views/ProductListView.vue') },
      { path: 'goods/:id', name: 'goods-detail', component: () => import('@/views/ProductDetailView.vue') },
      { path: 'marketing', name: 'marketing', component: () => import('@/views/MarketingView.vue') },
      { path: 'login', name: 'login', component: () => import('@/views/AuthView.vue') },
      { path: 'register', name: 'register', component: () => import('@/views/AuthView.vue') },
      { path: 'forgot-password', name: 'forgot-password', component: () => import('@/views/ForgotPasswordView.vue') },
      { path: 'cart', name: 'cart', meta: { auth: true }, component: () => import('@/views/CartView.vue') },
      { path: 'checkout', name: 'checkout', meta: { auth: true }, component: () => import('@/views/CheckoutView.vue') },
      { path: 'member', name: 'member', meta: { auth: true }, component: () => import('@/views/MemberView.vue') },
      { path: 'member/profile', redirect: '/member' },
      { path: 'member/addresses', name: 'addresses', meta: { auth: true }, component: () => import('@/views/AddressView.vue') },
      { path: 'member/orders', name: 'orders', meta: { auth: true }, component: () => import('@/views/OrdersView.vue') },
      { path: 'member/orders/:id', name: 'order-detail', meta: { auth: true }, component: () => import('@/views/OrderDetailView.vue') },
      { path: 'member/orders/:id/comment', name: 'order-comment', meta: { auth: true }, component: () => import('@/views/OrderCommentView.vue') },
      { path: 'member/favorites', name: 'favorites', meta: { auth: true }, component: () => import('@/views/CollectionView.vue'), props: { mode: 'favorite' } },
      { path: 'member/history', name: 'history', meta: { auth: true }, component: () => import('@/views/CollectionView.vue'), props: { mode: 'history' } },
      { path: 'member/after-sales', name: 'after-sales', meta: { auth: true }, component: () => import('@/views/AfterSalesView.vue') },
      { path: 'member/after-sales/apply', name: 'after-sale-apply', meta: { auth: true }, component: () => import('@/views/AfterSaleApplyView.vue') },
      { path: 'member/wallet', name: 'wallet', meta: { auth: true }, component: () => import('@/views/WalletView.vue') },
      { path: 'member/customer-service', name: 'customer-service', meta: { auth: true }, component: () => import('@/views/CustomerServiceView.vue') },
      { path: 'pay/result', name: 'pay-result', meta: { auth: true }, component: () => import('@/views/PayResultView.vue') },
      { path: 'pay/:orderId', name: 'pay', meta: { auth: true }, component: () => import('@/views/PayView.vue') },
      { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach((to) => {
  if (to.meta.auth && !tokenStorage.getAccess()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && tokenStorage.getAccess()) return { name: 'member' }
  return true
})

window.addEventListener('mall:unauthorized', () => {
  void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})

export default router
