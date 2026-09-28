<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import type { PlatformCartItem } from '@/api/platform-commerce'
import EmptyState from '@/components/EmptyState.vue'
import ServiceEntry from '@/components/ServiceEntry.vue'
import { useAccountMessageBox, useAccountOperation } from '@/composables/useAccountOperation'
import { useCartStore } from '@/stores/cart'
import { formatMoney, normalizeImage, propertyText } from '@/utils/format'
import { platformCheckoutDraft } from '@/utils/platform-checkout-draft'

const ElMessageBox = useAccountMessageBox()
const accountOperation = useAccountOperation()
const router = useRouter()
const cartStore = useCartStore()
const allSelected = computed(() => cartStore.items.length > 0 && cartStore.items.every(item => item.selected))
const displayGroups = computed(() => cartStore.tenantGroups.map(tenant => ({
  ...tenant,
  shops: tenant.shops.map(shop => ({ ...shop, items: shop.items.filter(item => item.valid) }))
    .filter(shop => shop.items.length),
})).filter(tenant => tenant.shops.length))

async function changeCount(item: PlatformCartItem, value: number | undefined) {
  if (!value || value === item.quantity) return
  try { await cartStore.changeCount(item, value) }
  catch (error) { ElMessage.warning(error instanceof Error ? error.message : '数量更新失败') }
}

async function remove(ids: Array<string | number>) {
  if (!ids.length || cartStore.mutating || !cartStore.ready) return
  try {
    await ElMessageBox.confirm('确定从购物车删除选中的商品吗？', '删除商品', {
      type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消',
    })
  } catch { return }
  await cartStore.remove(ids)
  accountOperation.assert()
  ElMessage.success('已删除')
}

async function checkout() {
  accountOperation.assert()
  if (!cartStore.ready || cartStore.mutating) return ElMessage.warning('请等待购物车更新完成')
  if (!cartStore.selectedItems.length) return ElMessage.warning('请先选择要结算的商品')
  platformCheckoutDraft.save(cartStore.selectedItems.map(item => item.id))
  await router.push('/checkout')
}

onMounted(cartStore.fetchCart)
</script>

<template>
  <div class="container page-section">
    <div class="section-heading"><div><h1>平台购物车</h1><p>跨租户商品按租户和店铺分组，归属、价格与库存均由服务端实时核验</p></div></div>
    <ServiceEntry label="平台购物咨询（不代表任一店铺）" />
    <div v-if="cartStore.loading" class="panel loading-box"><el-skeleton :rows="8" animated /></div>
    <el-result v-else-if="cartStore.error" class="panel" icon="warning" title="购物车加载失败" :sub-title="cartStore.error"><template #extra><el-button type="primary" @click="cartStore.fetchCart()">重新加载</el-button></template></el-result>
    <EmptyState v-else-if="cartStore.ready && !cartStore.allItems.length" class="panel" title="购物车还是空的" description="去平台门户挑选适合工程项目的防腐保温材料吧"><el-button type="primary" @click="router.push('/')">去选购</el-button></EmptyState>
    <template v-else>
      <section v-for="tenant in displayGroups" :key="String(tenant.tenantId)" class="tenant-cart panel">
        <header class="tenant-head"><div><small>经营租户</small><h2>{{ tenant.tenantName }}</h2></div><span>租户编号 {{ tenant.tenantId }}</span></header>
        <section v-for="shop in tenant.shops" :key="String(shop.shopId)" class="shop-cart">
          <header class="shop-head"><div><strong>{{ shop.shopName }}</strong><span>{{ shop.enterpriseName }}</span></div><small>店铺编号 {{ shop.shopId }}</small></header>
          <div class="cart-row cart-head"><span>选择</span><span>商品信息</span><span>单价</span><span>数量</span><span>小计</span><span>操作</span></div>
          <div v-for="item in shop.items" :key="String(item.id)" class="cart-row">
            <el-checkbox :model-value="item.selected" :disabled="cartStore.mutating || !cartStore.ready" @change="cartStore.select(item, Boolean($event))" />
            <div class="cart-product"><div class="cart-product__image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品图片" /></div><div><strong>{{ item.spuName || '商品' }}</strong><small>{{ propertyText(item.properties) || `SKU ${item.skuId}` }}</small></div></div>
            <span>¥{{ formatMoney(item.price) }}</span>
            <el-input-number :model-value="item.quantity" :disabled="cartStore.mutating || !cartStore.ready" :min="1" :max="99999" size="small" @change="changeCount(item, $event)" />
            <strong class="price">¥{{ formatMoney(Number(item.price || 0) * item.quantity) }}</strong>
            <button class="text-action" :disabled="cartStore.mutating || !cartStore.ready" @click="remove([item.id])">删除</button>
          </div>
        </section>
      </section>

      <section v-if="cartStore.invalidItems.length" class="invalid-cart panel" aria-label="暂不可购买的商品">
        <header><div><h2>暂不可购买（{{ cartStore.invalidItems.length }}）</h2><p>服务端已核验到商品下架、库存不足、规格失效或经营归属变化；这些商品不参与结算。</p></div><el-button :disabled="cartStore.mutating || !cartStore.ready" @click="remove(cartStore.invalidItems.map(item => item.id))">清理失效商品</el-button></header>
        <div v-for="item in cartStore.invalidItems" :key="String(item.id)" class="invalid-row">
          <div><strong>{{ item.spuName || '商品暂不可购买' }}</strong><p>{{ propertyText(item.properties) || `SKU ${item.skuId}` }}</p><small>{{ item.invalidReason || '商品当前不可购买' }} · 原采购数量 {{ item.quantity }}</small></div>
          <span>租户 {{ item.tenantId }} / 店铺 {{ item.shopId }}</span><button class="text-action" :disabled="cartStore.mutating || !cartStore.ready" @click="remove([item.id])">删除</button>
        </div>
      </section>

      <section v-if="cartStore.items.length" class="cart-footer panel"><div><el-checkbox :model-value="allSelected" :disabled="cartStore.mutating || !cartStore.ready" @change="cartStore.selectAll(Boolean($event))">全选有效商品</el-checkbox><button class="text-action" :disabled="cartStore.mutating || !cartStore.ready || !cartStore.selectedItems.length" @click="remove(cartStore.selectedItems.map(item => item.id))">删除选中</button></div><div class="cart-summary"><span>已选 <b>{{ cartStore.selectedItems.length }}</b> 行</span><span>商品预估：<strong>¥{{ formatMoney(cartStore.selectedAmount) }}</strong></span><el-button type="primary" size="large" :disabled="cartStore.mutating || !cartStore.ready || !cartStore.selectedItems.length" @click="checkout">跨租户结算预览</el-button></div></section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.tenant-cart{margin-bottom:18px;overflow:hidden}.tenant-head{display:flex;align-items:center;justify-content:space-between;padding:20px 24px;background:#123941;color:#fff}.tenant-head small{color:#8fc8bd;letter-spacing:1px}.tenant-head h2{margin:5px 0 0;font-size:20px}.tenant-head>span{color:#b8d6d0;font-size:12px}.shop-cart+.shop-cart{border-top:8px solid #f2f5f5}.shop-head{display:flex;align-items:center;justify-content:space-between;padding:16px 22px;border-bottom:1px solid var(--line)}.shop-head strong,.shop-head span{display:block}.shop-head span{margin-top:5px;color:var(--muted);font-size:12px}.invalid-cart{margin-top:18px;padding:20px}.invalid-cart header{display:flex;justify-content:space-between;align-items:center;gap:20px}.invalid-cart h2{font-size:18px;margin:0}.invalid-cart p,.invalid-row small,.invalid-row>span{color:var(--muted)}.invalid-row{display:grid;grid-template-columns:1fr 240px 70px;gap:16px;align-items:center;padding:18px 0;border-top:1px solid var(--line)}.text-action:disabled{opacity:.5;cursor:not-allowed}.cart-row{display:grid;grid-template-columns:56px minmax(360px,1fr) 120px 160px 120px 70px;align-items:center;gap:12px;min-height:120px;padding:16px 22px;border-top:1px solid var(--line)}.cart-head{min-height:48px;border:0;background:#f7f8f9;color:var(--muted);font-size:13px}.cart-product{display:flex;align-items:center;gap:14px}.cart-product__image{width:82px;height:82px;display:grid;place-items:center;background:#f5f5f5}.cart-product img{width:100%;height:100%;object-fit:contain}.cart-product small{display:block;margin-top:9px;color:var(--muted)}.text-action{border:0;background:transparent;color:var(--brand)}.cart-footer{position:sticky;bottom:0;display:flex;justify-content:space-between;align-items:center;gap:30px;margin-top:16px;padding:15px 20px;box-shadow:0 -5px 18px rgba(0,0,0,.05)}.cart-footer>div:first-child{display:flex;align-items:center;gap:24px}.cart-summary{display:flex;align-items:center;gap:30px}.cart-summary>span{color:var(--muted)}.cart-summary b,.cart-summary strong{color:#df421f}.cart-summary strong{font-size:24px}@media(max-width:980px){.cart-row{grid-template-columns:42px 1fr 100px}.cart-row>*:nth-child(3),.cart-row>*:nth-child(5){display:none}.cart-summary{gap:12px}.cart-footer{align-items:flex-start;flex-direction:column}}
</style>
