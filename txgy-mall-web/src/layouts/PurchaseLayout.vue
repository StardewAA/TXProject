<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import OrderWorkbenchBadge from '@/components/OrderWorkbenchBadge.vue'
const auth = useAuthStore()
const route = useRoute()
const links = [
  { to: '/purchase', label: '采购工作台', exact: true },
  { to: '/purchase/inquiries/new', label: '发布询价单 / Excel 导入', exact: true },
  { to: '/purchase/inquiries', label: '管理询价单' },
  { to: '/purchase/orders', label: '采购订单与收货' },
  { to: '/purchase/after-sales', label: '采购售后' },
  { to: '/purchase/addresses', label: '收货地址' },
  { to: '/purchase/profile', label: '企业资料与认证' },
]
function active(link: typeof links[number]) {
  if (link.to === '/purchase/inquiries' && route.path === '/purchase/inquiries/new') return false
  return route.path === link.to || (!link.exact && route.path.startsWith(link.to + '/'))
}
</script>
<template>
  <div class="container purchase-layout">
    <aside class="purchase-sidebar">
      <small>PURCHASING CENTER</small><h2>采购中心</h2>
      <nav aria-label="采购中心导航">
        <MallLink v-for="link in links" :key="link.to" :to="link.to" :class="{ current: active(link) }" :aria-current="active(link) ? 'page' : undefined">{{ link.label }}<OrderWorkbenchBadge v-if="link.to==='/purchase/orders'" /></MallLink>
      </nav>
      <div class="switch-links"><MallLink to="/goods">前往商城选购 →</MallLink><MallLink to="/cart">采购清单 / 购物车 →</MallLink><MallLink v-if="auth.profile?.memberIdentity === 'supplier'" to="/sales">切换到销售中心 →</MallLink><MallLink to="/member">切换到会员中心 →</MallLink></div>
    </aside>
    <div class="purchase-body"><p class="scope-note">采购中心 · 订单、售后及地址沿用当前账号数据</p><slot /></div>
  </div>
</template>
<style scoped>
.purchase-layout{display:grid;grid-template-columns:248px minmax(0,1fr);gap:24px;margin:32px auto;align-items:start}.purchase-sidebar{background:#173e46;color:white;border-radius:8px;padding:24px 12px}.purchase-sidebar small{letter-spacing:1.6px;color:#bbd3d5;font-size:10px;margin:0 12px}.purchase-sidebar h2{margin:10px 12px 22px}.purchase-sidebar a{display:block;padding:12px;border-radius:5px;font-size:14px}.purchase-sidebar a:hover{background:#ffffff12}.purchase-sidebar a.current{background:#167664;font-weight:600}.switch-links{border-top:1px solid #ffffff25;margin-top:20px;padding-top:12px}.scope-note{color:#64748b;margin:0 0 16px;font-size:13px}.purchase-body{min-width:0}.purchase-body :deep(.container){width:100%;max-width:none;margin:0}.purchase-body :deep(.purchase-center){display:block;min-height:560px}.purchase-body :deep(.member-layout){display:block}.purchase-body :deep(.panel){min-width:0}@media(max-width:800px){.purchase-layout{grid-template-columns:1fr}.purchase-sidebar nav{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
