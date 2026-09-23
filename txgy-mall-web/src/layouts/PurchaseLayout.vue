<script setup lang="ts">
import { useRoute } from 'vue-router'
import OrderWorkbenchBadge from '@/components/OrderWorkbenchBadge.vue'
import IndustrialIcon from '@/components/IndustrialIcon.vue'
const route = useRoute()
const links = [
  { to: '/purchase', label: '采购工作台', icon: 'compass', exact: true },
  { to: '/purchase/inquiries/new', label: '发布询价单 / Excel 导入', icon: 'clipboard', exact: true },
  { to: '/purchase/inquiries', label: '管理询价单', icon: 'layers' },
  { to: '/purchase/inquiry-quotes', label: '收到的报价', icon: 'package', exact: true },
  { to: '/purchase/orders', label: '采购订单与收货', icon: 'warehouse' },
  { to: '/purchase/after-sales', label: '采购售后', icon: 'shield' },
  { to: '/purchase/addresses', label: '收货地址', icon: 'pipe' },
  { to: '/purchase/profile', label: '企业资料与认证', icon: 'tools' },
]
function active(link: typeof links[number]) {
  if (link.to === '/purchase/inquiries' && route.path === '/purchase/inquiries/new') return false
  if (link.to === '/purchase/inquiries' && route.path.includes('/quotes')) return false
  if (link.to === '/purchase/inquiry-quotes') return route.path === link.to || /^\/purchase\/inquiries\/\d+\/quotes$/.test(route.path)
  return route.path === link.to || (!link.exact && route.path.startsWith(link.to + '/'))
}
</script>
<template>
  <div class="container purchase-layout">
    <aside class="purchase-sidebar">
      <div class="sidebar-brand"><span><IndustrialIcon name="clipboard" /></span><div><small>PURCHASING CENTER</small><h2>采购中心</h2></div></div>
      <p>工程材料需求、询价与订单履约统一管理</p>
      <nav aria-label="采购中心导航">
        <MallLink v-for="link in links" :key="link.to" :to="link.to" :class="{ current: active(link) }" :aria-current="active(link) ? 'page' : undefined"><i><IndustrialIcon :name="link.icon" /></i><span>{{ link.label }}</span><OrderWorkbenchBadge v-if="link.to==='/purchase/orders'" /></MallLink>
      </nav>
      <div class="switch-links"><MallLink to="/goods">前往商城选购 →</MallLink><MallLink to="/cart">采购清单 / 购物车 →</MallLink><MallLink to="/member">切换到会员中心 →</MallLink></div>
    </aside>
    <div class="purchase-body"><div class="scope-note"><span><IndustrialIcon name="shield" /></span><div><strong>企业采购工作区</strong><small>询价、采购订单及售后记录按当前企业协作；支付、评价、售后申请与退货操作由实际下单人处理</small></div><b>数据实时同步</b></div><slot /></div>
  </div>
</template>
<style scoped>
.purchase-layout{display:grid;grid-template-columns:272px minmax(0,1fr);gap:28px;margin:28px auto 42px;align-items:start}.purchase-sidebar{position:sticky;top:20px;overflow:hidden;padding:24px 14px 18px;border:1px solid #2b5960;border-radius:20px;background:radial-gradient(circle at 90% 0,#1f756d 0,transparent 32%),linear-gradient(160deg,#102f37 0%,#173f47 58%,#0f343b 100%);box-shadow:0 18px 42px #173e4628;color:white}.sidebar-brand{display:flex;align-items:center;gap:13px;padding:0 10px}.sidebar-brand>span{display:grid;place-items:center;width:48px;height:48px;border:1px solid #8ed2c755;border-radius:14px;background:#ffffff12;color:#75d2c0}.sidebar-brand svg{width:25px;height:25px}.purchase-sidebar small{display:block;letter-spacing:1.6px;color:#9fc7c5;font-size:9px}.purchase-sidebar h2{margin:5px 0 0;font-size:24px}.purchase-sidebar>p{margin:18px 10px 20px;padding-bottom:18px;border-bottom:1px solid #ffffff1c;color:#bad0d1;font-size:12px;line-height:1.7}.purchase-sidebar nav{display:grid;gap:5px}.purchase-sidebar nav a{display:flex;align-items:center;gap:11px;min-height:45px;padding:9px 11px;border:1px solid transparent;border-radius:11px;color:#eaf5f3;font-size:13px;transition:.2s ease}.purchase-sidebar nav a i{display:grid;place-items:center;width:30px;height:30px;flex:0 0 auto;border-radius:9px;background:#ffffff0d;color:#a9d9d1}.purchase-sidebar nav a i svg{width:17px;height:17px}.purchase-sidebar nav a span{flex:1}.purchase-sidebar nav a:hover{transform:translateX(2px);background:#ffffff12}.purchase-sidebar nav a.current{border-color:#70c9b849;background:linear-gradient(90deg,#168071,#126759);box-shadow:0 8px 20px #08282438;font-weight:700}.purchase-sidebar nav a.current i{background:#ffffff1c;color:#fff}.switch-links{display:grid;gap:3px;margin-top:20px;padding-top:15px;border-top:1px solid #ffffff20}.switch-links a{padding:8px 10px;border-radius:8px;color:#bcd7d5;font-size:12px}.switch-links a:hover{background:#ffffff0d;color:#fff}.purchase-body{min-width:0}.scope-note{display:flex;align-items:center;gap:12px;margin:0 0 16px;padding:13px 16px;border:1px solid #d6e6e2;border-radius:14px;background:linear-gradient(90deg,#f3faf8,#fff);box-shadow:0 8px 22px #173e4609}.scope-note>span{display:grid;place-items:center;width:36px;height:36px;border-radius:10px;background:#dff3ed;color:#137462}.scope-note svg{width:20px;height:20px}.scope-note div{display:flex;flex:1;flex-direction:column}.scope-note strong{color:#173f47;font-size:14px}.scope-note small{margin-top:2px;color:#71808a}.scope-note b{padding:6px 10px;border-radius:99px;background:#edf7f4;color:#147866;font-size:11px}.purchase-body :deep(.container){width:100%;max-width:none;margin:0}.purchase-body :deep(.purchase-center){display:block;min-height:560px}.purchase-body :deep(.member-layout){display:block}.purchase-body :deep(.panel),.purchase-body :deep(.page-section),.purchase-body :deep(.shop-settings){min-width:0;border:1px solid #e2e9e7;border-radius:18px;background:#fff;box-shadow:0 16px 38px #1d3f4710}.purchase-body :deep(.inquiry-list),.purchase-body :deep(.inquiry-edit),.purchase-body :deep(.page-section){margin:0;padding:28px}.purchase-body :deep(h1){color:#17323a;letter-spacing:-.5px}.purchase-body :deep(.filters){padding:14px;border:1px solid #e4ece9;border-radius:12px;background:#f7faf9}.purchase-body :deep(.el-table){overflow:hidden;border:1px solid #e6ecea;border-radius:12px}.purchase-body :deep(.el-table th.el-table__cell){background:#f3f7f6;color:#52676c}.purchase-body :deep(.el-pagination){justify-content:flex-end}.purchase-body :deep(.el-button--primary){--el-button-bg-color:#147866;--el-button-border-color:#147866;--el-button-hover-bg-color:#0f6858;--el-button-hover-border-color:#0f6858}@media(max-width:1000px){.purchase-layout{grid-template-columns:230px minmax(0,1fr);gap:18px}.purchase-sidebar{padding-inline:10px}.scope-note b{display:none}}@media(max-width:800px){.purchase-layout{grid-template-columns:1fr}.purchase-sidebar{position:static}.purchase-sidebar nav{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
