<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox, useAccountOperation } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
const accountOperation = useAccountOperation()
import { useRouter } from 'vue-router'

import EmptyState from '@/components/EmptyState.vue'
import ServiceEntry from '@/components/ServiceEntry.vue'
import { useCartStore } from '@/stores/cart'
import { checkoutDraft } from '@/utils/checkout-draft'
import type { CartItem } from '@/types/mall'
import { formatMoney, normalizeImage, propertyText } from '@/utils/format'

const router = useRouter()
const cartStore = useCartStore()
const allSelected = computed(() => cartStore.items.length > 0 && cartStore.items.every((item) => item.selected))

async function changeCount(item: CartItem, value: number | undefined) {
  if (!value || value === item.count) return
  await cartStore.changeCount(item, value)
}

async function remove(ids: Array<string | number>) {
  if (!ids.length || cartStore.mutating || !cartStore.ready) return
  try { await ElMessageBox.confirm('确定从购物车删除选中的商品吗？', '删除商品', { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }) } catch { return }
  await cartStore.remove(ids)
  accountOperation.assert()
  ElMessage.success('已删除')
}

async function checkout() {
  accountOperation.assert()
  if (!cartStore.ready || cartStore.mutating) return ElMessage.warning('请等待购物车更新完成')
  if (!cartStore.selectedItems.length) return ElMessage.warning('请先选择要结算的商品')
  const payload = cartStore.selectedItems.map((item) => ({ skuId: item.sku.id, count: item.count, cartId: item.id }))
  checkoutDraft.save(payload)
  await router.push('/checkout')
}

onMounted(cartStore.fetchCart)
</script>

<template>
  <div class="container page-section">
    <div class="section-heading"><div><h1>我的购物车</h1><p>购物车数据保存在服务端，与移动端保持一致</p></div></div>
    <ServiceEntry label="平台购物咨询（不代表任一店铺）" />
    <div v-if="cartStore.loading" class="panel loading-box"><el-skeleton :rows="8" animated /></div>
    <el-result v-else-if="cartStore.error" class="panel" icon="warning" title="购物车加载失败" :sub-title="cartStore.error"><template #extra><el-button type="primary" @click="cartStore.fetchCart()">重新加载</el-button></template></el-result>
    <EmptyState v-else-if="cartStore.ready && !cartStore.items.length && !cartStore.invalidItems.length" class="panel" title="购物车还是空的" description="去挑选适合工程项目的防腐保温材料吧"><el-button type="primary" @click="router.push('/goods')">去选购</el-button></EmptyState>
    <template v-else>
      <section v-if="cartStore.items.length" class="cart-table panel">
        <div class="cart-row cart-head"><span>选择</span><span>商品信息</span><span>单价</span><span>数量</span><span>小计</span><span>操作</span></div>
        <div v-for="item in cartStore.items" :key="item.id" class="cart-row">
          <el-checkbox :model-value="item.selected" :disabled="cartStore.mutating || !cartStore.ready" @change="cartStore.select(item, Boolean($event))" />
          <div class="cart-product"><MallLink :to="`/goods/${item.spu?.id || item.sku.spuId}`"><div class="cart-product__image"><img v-if="item.sku.picUrl || item.spu?.picUrl" :src="normalizeImage(item.sku.picUrl || item.spu?.picUrl)" alt="商品图片" /></div></MallLink><div><MallLink :to="`/goods/${item.spu?.id || item.sku.spuId}`">{{ item.spu?.name || item.sku.name || '商品' }}</MallLink><small>{{ propertyText(item.sku.properties) }}</small><ServiceEntry resource-type="PRODUCT" :resource-id="item.spu?.id || item.sku.spuId" :disabled="cartStore.mutating || !cartStore.ready" /></div></div>
          <span>¥{{ formatMoney(item.sku.price) }}</span>
          <el-input-number :model-value="item.count" :disabled="cartStore.mutating || !cartStore.ready" :min="1" :max="Math.max(1, item.sku.stock)" size="small" @change="changeCount(item, $event)" />
          <strong class="price">¥{{ formatMoney(item.sku.price * item.count) }}</strong>
          <button class="text-action" :disabled="cartStore.mutating || !cartStore.ready" @click="remove([item.id])">删除</button>
        </div>
      </section>
      <section v-if="cartStore.invalidItems.length" class="invalid-cart panel" aria-label="暂不可购买的商品">
        <header><div><h2>暂不可购买（{{ cartStore.invalidItems.length }}）</h2><p>可能已下架、待审核、规格失效或数量超过可售库存；不参与选择、合计及结算。</p></div><el-button :disabled="cartStore.mutating || !cartStore.ready" @click="remove(cartStore.invalidItems.map(item => item.id))">清理失效商品</el-button></header>
        <div v-for="item in cartStore.invalidItems" :key="item.id" class="invalid-row">
          <div><strong>{{ item.spu?.name || '商品暂不可购买' }}</strong><p>{{ propertyText(item.sku?.properties) || '商品规格暂不可用' }}</p><small>原采购数量：{{ item.count }}</small></div>
          <span>暂不可结算</span><button class="text-action" :disabled="cartStore.mutating || !cartStore.ready" @click="remove([item.id])">删除</button>
        </div>
      </section>
      <section v-if="cartStore.items.length" class="cart-footer panel"><div><el-checkbox :model-value="allSelected" :disabled="cartStore.mutating || !cartStore.ready" @change="cartStore.selectAll(Boolean($event))">全选</el-checkbox><button class="text-action" :disabled="cartStore.mutating || !cartStore.ready || !cartStore.selectedItems.length" @click="remove(cartStore.selectedItems.map((item) => item.id))">删除选中</button></div><div class="cart-summary"><span>已选 <b>{{ cartStore.selectedItems.length }}</b> 件</span><span>合计（不含运费）：<strong>¥{{ formatMoney(cartStore.selectedAmount) }}</strong></span><el-button type="primary" size="large" :disabled="cartStore.mutating || !cartStore.ready || !cartStore.selectedItems.length" @click="checkout">去结算</el-button></div></section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.invalid-cart { margin-top: 18px; padding: 20px; }.invalid-cart header { display:flex;justify-content:space-between;align-items:center;gap:20px; }.invalid-cart h2 { font-size:18px;margin:0; }.invalid-cart p,.invalid-row small,.invalid-row>span { color:var(--muted); }.invalid-row { display:grid;grid-template-columns:1fr 130px 70px;gap:16px;align-items:center;padding:18px 0;border-top:1px solid var(--line); }.text-action:disabled { opacity:.5;cursor:not-allowed; }
.cart-table { overflow: hidden; }.cart-row { display: grid; grid-template-columns: 56px minmax(360px, 1fr) 120px 160px 120px 70px; align-items: center; gap: 12px; min-height: 120px; padding: 16px 22px; border-top: 1px solid var(--line); }.cart-head { min-height: 48px; border: 0; background: #f7f8f9; color: var(--muted); font-size: 13px; }.cart-product { display: flex; align-items: center; gap: 14px; }.cart-product__image { width: 82px; height: 82px; display: grid; place-items: center; background: #f5f5f5; }.cart-product img { width: 100%; height: 100%; object-fit: cover; }.cart-product small { display: block; margin-top: 9px; color: var(--muted); }.text-action { border: 0; background: transparent; color: var(--brand); }.cart-footer { position: sticky; bottom: 0; display: flex; justify-content: space-between; align-items: center; gap: 30px; margin-top: 16px; padding: 15px 20px; box-shadow: 0 -5px 18px rgba(0,0,0,.05); }.cart-footer > div:first-child { display: flex; align-items: center; gap: 24px; }.cart-summary { display: flex; align-items: center; gap: 30px; }.cart-summary > span { color: var(--muted); }.cart-summary b, .cart-summary strong { color: #df421f; }.cart-summary strong { font-size: 24px; }
</style>
