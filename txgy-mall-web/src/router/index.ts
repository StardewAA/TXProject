import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { tokenStorage, ensureAccountSession, ensureEntryReady, StaleSessionError } from '@/api/http'
import { getTenantContext, entryTenantId } from '@/utils/tenant-context'
import { isPlatformPath, scopeLocalPath } from '@/utils/tenant-navigation.mjs'

const routes: RouteRecordRaw[] = [
  { path: '/entry-unavailable', name: 'entry-unavailable', component: () => import('@/views/EntryUnavailableView.vue') },
  { path: '/markets/:pathMatch(.*)*', redirect: to => ({ path: '/', query: to.query }) },
  {
    path: '/',
    component: () => import('@/layouts/MallLayout.vue'),
    children: [
      { path: '', name: 'platform-home', meta: { platform: true }, component: () => import('@/views/PlatformPortalView.vue') },
      { path: 'tenants/:tenantSlug', name: 'tenant-mall', meta: { platform: true }, component: () => import('@/views/PlatformPortalView.vue') },
      { path: 'tenants/:tenantSlug/products/:spuId', name: 'tenant-product-detail', meta: { platform: true }, component: () => import('@/views/PlatformProductDetailView.vue') },
      { path: 'products/:spuId', name: 'platform-product-detail', meta: { platform: true }, component: () => import('@/views/PlatformProductDetailView.vue') },
      { path: 'tenant-home', name: 'legacy-tenant-home', component: () => import('@/views/HomeView.vue') },
      { path: 'companies', name: 'companies', meta: { platform: true }, component: () => import('@/views/CompaniesView.vue') },
      { path: 'shops', name: 'shops', component: () => import('@/views/ShopsView.vue') },
      { path: 'shops/:id', name: 'shop-detail', component: () => import('@/views/ShopDetailView.vue') },
      { path: 'categories', name: 'categories', component: () => import('@/views/CategoriesView.vue') },
      { path: 'search', name: 'search', component: () => import('@/views/ProductListView.vue') },
      { path: 'goods', name: 'goods-list', component: () => import('@/views/ProductListView.vue') },
      { path: 'goods/:id', name: 'goods-detail', component: () => import('@/views/ProductDetailView.vue') },
      { path: 'compare', name: 'comparison', component: () => import('@/views/ComparisonView.vue') },
      { path: 'marketing', name: 'marketing', component: () => import('@/views/MarketingView.vue') },
      { path: 'inquiries', name: 'inquiry-market', component: () => import('@/views/InquiryMarketView.vue') },
      { path: 'inquiries/:id(\\d+)', name: 'inquiry-public-detail', component: () => import('@/views/InquiryPublicDetailView.vue') },
      { path: 'inquiries/:pathMatch(.*)*', name: 'legacy-inquiry-entry', component: () => import('@/views/LegacyBusinessEntryView.vue') },
      { path: 'login', name: 'login', component: () => import('@/views/AuthView.vue') },
      { path: 'platform-login', name: 'platform-login', meta: { platform: true }, component: () => import('@/views/AuthView.vue') },
      { path: 'download/mobile', name: 'mobile-download', meta: { platform: true, mobileDownload: true }, component: () => import('@/views/MobileDownloadView.vue') },
      { path: 'register', name: 'register', component: () => import('@/views/AuthView.vue') },
      { path: 'forgot-password', name: 'forgot-password', component: () => import('@/views/ForgotPasswordView.vue') },
      { path: 'cart', name: 'cart', meta: { auth: true, platform: true }, component: () => import('@/views/CartView.vue') },
      { path: 'checkout', name: 'checkout', meta: { auth: true, platform: true }, component: () => import('@/views/CheckoutView.vue') },
      { path: 'member', name: 'member', meta: { auth: true }, component: () => import('@/views/MemberView.vue') },
      { path: 'enterprise/profile', meta: { auth: true }, component: () => import('@/views/EnterpriseProfileView.vue') },
      { path: 'purchase/profile', meta: { auth: true }, component: () => import('@/views/EnterpriseProfileView.vue') },
      { path: 'purchase/orders', meta: { auth: true }, component: () => import('@/views/OrdersView.vue') },
      { path: 'purchase/orders/:id', meta: { auth: true }, component: () => import('@/views/OrderDetailView.vue') },
      { path: 'purchase/orders/:id/comment', meta: { auth: true }, component: () => import('@/views/OrderCommentView.vue') },
      { path: 'purchase/after-sales', meta: { auth: true }, component: () => import('@/views/AfterSalesView.vue') },
      { path: 'purchase/after-sales/apply', meta: { auth: true }, component: () => import('@/views/AfterSaleApplyView.vue') },
      { path: 'purchase/addresses', meta: { auth: true }, component: () => import('@/views/AddressView.vue') },
      { path: 'purchase', meta: { auth: true }, component: () => import('@/views/BusinessCenterView.vue') },
      { path: 'sales', name: 'legacy-sales-center', component: () => import('@/views/LegacyBusinessEntryView.vue') },
      { path: 'sales/:pathMatch(.*)*', name: 'legacy-sales-entry', component: () => import('@/views/LegacyBusinessEntryView.vue') },
      { path: 'purchase/inquiry-quotes', name: 'received-inquiry-quotes', meta: { auth: true }, component: () => import('@/views/InquiryReceivedQuotesView.vue') },
      { path: 'purchase/inquiries/:id/quotes', name: 'inquiry-received-quotes', meta: { auth: true }, component: () => import('@/views/InquiryReceivedQuotesView.vue') },
      { path: 'purchase/inquiries', meta: { auth: true }, component: () => import('@/views/InquiryListView.vue') },
      { path: 'purchase/inquiries/:id', meta: { auth: true }, component: () => import('@/views/InquiryEditView.vue') },
      { path: 'member/profile', redirect: '/member' },
      { path: 'member/addresses', name: 'addresses', meta: { auth: true }, component: () => import('@/views/AddressView.vue') },
      { path: 'member/orders', name: 'orders', meta: { auth: true }, component: () => import('@/views/OrdersView.vue') },
      { path: 'member/platform-checkouts', name: 'platform-checkouts', meta: { auth: true, platform: true }, component: () => import('@/views/PlatformCheckoutsView.vue') },
      { path: 'member/orders/:id', name: 'order-detail', meta: { auth: true }, component: () => import('@/views/OrderDetailView.vue') },
      { path: 'member/orders/:id/comment', name: 'order-comment', meta: { auth: true }, component: () => import('@/views/OrderCommentView.vue') },
      { path: 'member/favorites', name: 'favorites', meta: { auth: true }, component: () => import('@/views/CollectionView.vue'), props: { mode: 'favorite' } },
      { path: 'member/history', name: 'history', meta: { auth: true }, component: () => import('@/views/CollectionView.vue'), props: { mode: 'history' } },
      { path: 'member/after-sales', name: 'after-sales', meta: { auth: true }, component: () => import('@/views/AfterSalesView.vue') },
      { path: 'member/after-sales/apply', name: 'after-sale-apply', meta: { auth: true }, component: () => import('@/views/AfterSaleApplyView.vue') },
      { path: 'member/wallet', name: 'wallet', meta: { auth: true }, component: () => import('@/views/WalletView.vue') },
      { path: 'member/customer-service', name: 'customer-service', meta: { auth: true }, redirect: '/member/customer-service/scoped' },
      { path: 'member/customer-service/scoped', name: 'scoped-customer-service', meta: { auth: true }, component: () => import('@/views/ScopedCustomerServiceView.vue') },
      { path: 'member/customer-service/shop/:shopId', name: 'shop-customer-service', meta: { auth: true }, component: () => import('@/views/ScopedCustomerServiceView.vue') },
      { path: 'pay/result', name: 'pay-result', meta: { auth: true }, component: () => import('@/views/PayResultView.vue') },
      { path: 'pay/:orderId', name: 'pay', meta: { auth: true }, component: () => import('@/views/PayView.vue') },
      { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach(async (to) => {
  if (to.name === 'entry-unavailable') return true
  const platformRoute = to.meta.platform === true || isPlatformPath(to.fullPath)
  if (platformRoute) {
    if (to.query.tenantId !== undefined || to.query['tenant-id'] !== undefined || to.query.tenant_id !== undefined) {
      return { path: to.path, query: { ...to.query, 'tenant-id': undefined, tenant_id: undefined, tenantId: undefined }, hash: to.hash, replace: true }
    }
    if (to.meta.auth && tokenStorage.getAccess()) {
      try { await ensureAccountSession() }
      catch (error) {
        if (error instanceof StaleSessionError) return false
        return { name: 'platform-login', query: { redirect: to.fullPath, session: 'unverified' } }
      }
    }
    if (to.meta.auth && !tokenStorage.getAccess()) return { name: 'platform-login', query: { redirect: to.fullPath } }
    return true
  }
  try { scopeLocalPath(to.fullPath, entryTenantId) } catch { return { name: 'entry-unavailable' } }
  if (to.query.tenantId !== entryTenantId || to.query['tenant-id'] !== undefined || to.query.tenant_id !== undefined) {
    return { path: to.path, query: { ...to.query, 'tenant-id': undefined, tenant_id: undefined, tenantId: entryTenantId }, hash: to.hash, replace: true }
  }
  try { await ensureEntryReady() } catch { return { name: 'entry-unavailable' } }
  if (to.meta.auth && tokenStorage.getAccess()) {
    try { await ensureAccountSession() }
    catch (error) {
      if (error instanceof StaleSessionError) return false
      return { name: 'login', query: { redirect: to.fullPath, session: 'unverified' } }
    }
  }
  if (to.meta.auth && !tokenStorage.getAccess()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && tokenStorage.getAccess() && getTenantContext().accountTenantId) return { name: 'member' }
  return true
})

window.addEventListener('mall:unauthorized', () => {
  const current = router.currentRoute.value.fullPath
  void router.push({ name: isPlatformPath(current) ? 'platform-login' : 'login', query: { redirect: current } })
})

export default router
