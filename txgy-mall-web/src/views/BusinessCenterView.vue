<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { enterpriseApi, type EnterpriseApplication } from '@/api/enterprise'
import BusinessSummary from '@/components/BusinessSummary.vue'
import type { Shop } from '@/types/mall'
const route=useRoute(), row=ref<EnterpriseApplication|null>(null), shops=ref<Shop[]>([]), loaded=ref(false)
const sales=computed(()=>route.path.startsWith('/sales'))
onMounted(async()=>{try{row.value=await enterpriseApi.mine();if(row.value?.status===1 && row.value.identity==='supplier')shops.value=await enterpriseApi.salesShops();loaded.value=true}catch{/*接口层提示，不展示假成功*/}})
</script>
<template>
 <div class="container business-center" :class="sales?'sales-center':'purchase-center'">
  <aside v-if="sales">
   <div class="sidebar-heading"><span>{{sales?'SUPPLIER CENTER':'PURCHASING CENTER'}}</span><h2>{{sales?'销售中心':'采购中心'}}</h2></div>
   <nav :aria-label="sales?'销售中心导航':'采购中心导航'">
    <template v-if="sales">
     <RouterLink class="current" to="/sales" aria-current="page">销售工作台</RouterLink>
     <div class="menu-group">商品与店铺</div>
     <template v-if="loaded && shops.length">
      <div v-for="shop in shops" :key="shop.id" class="shop-menu">
       <div class="shop-name">{{shop.name}}</div>
       <RouterLink :to="`/sales/shops/${shop.id}/products/new`">发布商品</RouterLink>
       <RouterLink :to="`/sales/shops/${shop.id}/products`">管理商品</RouterLink>
       <RouterLink :to="`/shops/${shop.id}`">查看店铺</RouterLink><RouterLink :to="`/sales/shops/${shop.id}/settings`">店铺资料与退货地址</RouterLink>
      </div>
     </template>
     <template v-else><span class="unavailable" aria-disabled="true">发布商品 <small>待授权</small></span><span class="unavailable" aria-disabled="true">管理商品 <small>待授权</small></span></template>
     <div class="menu-group">销售业务</div>
     <template v-if="loaded && shops.length"><div v-for="shop in shops" :key="shop.id"><RouterLink :to="`/sales/shops/${shop.id}/orders`">销售订单</RouterLink><RouterLink :to="`/sales/shops/${shop.id}/after-sales`">销售售后处理</RouterLink></div></template>
     <template v-else><span class="unavailable" aria-disabled="true">销售订单 <small>待授权</small></span><span class="unavailable" aria-disabled="true">销售售后处理 <small>待授权</small></span></template>
     <RouterLink v-for="shop in shops" :key="`settlement-${shop.id}`" :to="`/sales/shops/${shop.id}/settlements`">{{shop.name}} · 店铺结算</RouterLink>
    </template>
    <template v-else>
     <RouterLink class="current" to="/purchase" aria-current="page">采购工作台</RouterLink>
     <div class="menu-group">采购需求</div>
     <RouterLink to="/purchase/inquiries/new">发布询价单 / Excel 导入</RouterLink>
     <RouterLink to="/purchase/inquiries">管理询价单</RouterLink>
     <RouterLink to="/goods">选购商品</RouterLink>
     <RouterLink to="/cart">采购清单 / 购物车</RouterLink>
     <div class="menu-group">采购交易</div>
     <RouterLink to="/purchase/orders">采购订单与收货</RouterLink>
     <RouterLink to="/purchase/after-sales">采购售后</RouterLink>
     <RouterLink to="/purchase/addresses">收货地址</RouterLink>
    </template>
    <div class="menu-group">企业账户</div>
    <RouterLink to="/enterprise/profile">企业资料与认证</RouterLink>
   </nav>
   <div class="center-switch"><RouterLink v-if="sales" to="/purchase">切换到采购中心 →</RouterLink><RouterLink v-else-if="row?.identity==='supplier'" to="/sales">切换到销售中心 →</RouterLink></div>
  </aside>
  <main class="panel"><h1>{{sales?'企业销售工作台':'企业采购工作台'}}</h1><p>{{row?.name||'请先完善企业资料'}}</p>
   <el-alert v-if="!loaded" title="正在加载企业身份，加载失败请刷新重试" :closable="false"/>
   <el-alert v-else-if="row?.status!==1" title="企业尚未审核通过，请完善资料或等待后台审核" type="warning" :closable="false"/>
   <template v-else-if="sales"><el-alert v-if="!shops.length" title="销售权限未开通：须为供应商身份，且由后台关联启用店铺" type="warning" :closable="false"/><template v-else><el-alert title="企业认证已通过，以下为已关联店铺" type="success" :closable="false"/><div v-for="shop in shops" :key="shop.id" class="shop"><h3>{{shop.name}}</h3><BusinessSummary :shop-id="Number(shop.id)"/><div class="shortcuts"><RouterLink :to="'/shops/'+shop.id">查看店铺</RouterLink><RouterLink :to="`/sales/shops/${shop.id}/products`">管理商品</RouterLink><RouterLink :to="`/sales/shops/${shop.id}/products/new`">发布商品</RouterLink><RouterLink :to="`/sales/shops/${shop.id}/orders`">销售订单</RouterLink><RouterLink :to="`/sales/shops/${shop.id}/after-sales`">售后处理</RouterLink></div></div><p>商品和销售订单使用专用会员授权接口。供应商可审核售后和验收退货，实际退款仍由平台处理，店铺结算提供账单核对与确认，真实自动打款未接通。</p></template></template>
   <template v-else><BusinessSummary/><el-alert title="企业注册与资质认证成功，当前账号为企业采购人员" type="success" :closable="false"/><div class="shortcuts"><RouterLink to="/purchase/inquiries/new">发布询价单 / Excel 导入</RouterLink><RouterLink to="/purchase/inquiries">管理询价单</RouterLink><RouterLink to="/goods">选购商品</RouterLink><RouterLink to="/cart">采购清单</RouterLink><RouterLink to="/purchase/orders">采购订单与收货</RouterLink><RouterLink to="/purchase/after-sales">退款售后</RouterLink></div></template>
   <p v-if="!sales">询价提交仅保存在商城，真实 ERP、供应商报价及转订单尚未对接。</p>
   <p v-else>仅可经营后台明确授权的店铺；灰色菜单为待开发功能，暂不开放操作。</p>
  </main>
 </div>
</template>
<style scoped>
.business-center{display:grid;grid-template-columns:248px minmax(0,1fr);gap:24px;margin:32px auto;min-height:560px;--sidebar:#173e46;--active:#167664}
.sales-center{--sidebar:#243952;--active:#3267a6}
aside{background:var(--sidebar);color:white;border-radius:8px;overflow:hidden;align-self:start}
.sidebar-heading{padding:24px 22px 18px;border-bottom:1px solid #ffffff20}.sidebar-heading span{font-size:10px;letter-spacing:1.6px;color:#c1d3df}.sidebar-heading h2{margin:10px 0 0;font-size:23px}
nav{padding:14px 12px}aside a,.unavailable{display:flex;align-items:center;justify-content:space-between;padding:11px 12px;font-size:14px;border-radius:5px;line-height:1.5}aside a:hover{background:#ffffff12}.current{background:var(--active);font-weight:600}.menu-group{font-size:12px;color:#b8ccda;margin:22px 12px 8px}.unavailable{color:#b2bcc8;cursor:not-allowed}.unavailable small{font-size:10px;border:1px solid #ffffff25;border-radius:3px;padding:1px 4px}.shop-name{color:#ccdbe7;font-size:12px;padding:4px 12px;overflow-wrap:anywhere}.center-switch{padding:8px 12px 18px;border-top:1px solid #ffffff20}.center-switch:empty{display:none}
main{padding:32px}p{color:#64748b;line-height:1.8}.shortcuts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px;margin:28px 0}.shortcuts a,.shop{background:#f0f7f5;padding:24px;color:#126b59;border:1px solid #dae8e2}.sales-center .shop,.sales-center .shortcuts a{background:#f1f5fa;border-color:#dbe4f0;color:#28558b}
@media(max-width:1000px){.business-center{grid-template-columns:220px minmax(0,1fr);gap:16px}.shortcuts{grid-template-columns:repeat(2,minmax(0,1fr))}main{padding:22px}}
</style>
