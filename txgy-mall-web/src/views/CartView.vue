<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import EmptyState from '@/components/EmptyState.vue'
import { useCartStore } from '@/stores/cart'
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
  await ElMessageBox.confirm('确定从购物车删除选中的商品吗？', '删除商品', { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' })
  await cartStore.remove(ids)
  ElMessage.success('已删除')
}

async function checkout() {
  if (!cartStore.selectedItems.length) return ElMessage.warning('请先选择要结算的商品')
  const payload = cartStore.selectedItems.map((item) => ({ skuId: item.sku.id, count: item.count, cartId: item.id }))
  sessionStorage.setItem('mall-checkout-items', JSON.stringify(payload))
  await router.push('/checkout')
}

onMounted(cartStore.fetchCart)
</script>

<template>
  <div class="container page-section">
    <div class="section-heading"><div><h1>我的购物车</h1><p>购物车数据保存在服务端，与移动端保持一致</p></div></div>
    <div v-if="cartStore.loading" class="panel loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!cartStore.items.length" class="panel" title="购物车还是空的" description="去挑选适合工程项目的防腐保温材料吧"><el-button type="primary" @click="router.push('/goods')">去选购</el-button></EmptyState>
    <template v-else>
      <section class="cart-table panel">
        <div class="cart-row cart-head"><span>选择</span><span>商品信息</span><span>单价</span><span>数量</span><span>小计</span><span>操作</span></div>
        <div v-for="item in cartStore.items" :key="item.id" class="cart-row">
          <el-checkbox :model-value="item.selected" @change="cartStore.select(item, Boolean($event))" />
          <div class="cart-product"><RouterLink :to="`/goods/${item.spu?.id || item.sku.spuId}`"><div class="cart-product__image"><img v-if="item.sku.picUrl || item.spu?.picUrl" :src="normalizeImage(item.sku.picUrl || item.spu?.picUrl)" alt="商品图片" /></div></RouterLink><div><RouterLink :to="`/goods/${item.spu?.id || item.sku.spuId}`">{{ item.spu?.name || item.sku.name || '商品' }}</RouterLink><small>{{ propertyText(item.sku.properties) }}</small></div></div>
          <span>¥{{ formatMoney(item.sku.price) }}</span>
          <el-input-number :model-value="item.count" :min="1" :max="Math.max(1, item.sku.stock)" size="small" @change="changeCount(item, $event)" />
          <strong class="price">¥{{ formatMoney(item.sku.price * item.count) }}</strong>
          <button class="text-action" @click="remove([item.id])">删除</button>
        </div>
      </section>
      <section class="cart-footer panel"><div><el-checkbox :model-value="allSelected" @change="cartStore.selectAll(Boolean($event))">全选</el-checkbox><button class="text-action" @click="remove(cartStore.selectedItems.map((item) => item.id))">删除选中</button></div><div class="cart-summary"><span>已选 <b>{{ cartStore.selectedItems.length }}</b> 件</span><span>合计（不含运费）：<strong>¥{{ formatMoney(cartStore.selectedAmount) }}</strong></span><el-button type="primary" size="large" @click="checkout">去结算</el-button></div></section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.cart-table { overflow: hidden; }.cart-row { display: grid; grid-template-columns: 56px minmax(360px, 1fr) 120px 160px 120px 70px; align-items: center; gap: 12px; min-height: 120px; padding: 16px 22px; border-top: 1px solid var(--line); }.cart-head { min-height: 48px; border: 0; background: #f7f8f9; color: var(--muted); font-size: 13px; }.cart-product { display: flex; align-items: center; gap: 14px; }.cart-product__image { width: 82px; height: 82px; display: grid; place-items: center; background: #f5f5f5; }.cart-product img { width: 100%; height: 100%; object-fit: cover; }.cart-product small { display: block; margin-top: 9px; color: var(--muted); }.text-action { border: 0; background: transparent; color: var(--brand); }.cart-footer { position: sticky; bottom: 0; display: flex; justify-content: space-between; align-items: center; gap: 30px; margin-top: 16px; padding: 15px 20px; box-shadow: 0 -5px 18px rgba(0,0,0,.05); }.cart-footer > div:first-child { display: flex; align-items: center; gap: 24px; }.cart-summary { display: flex; align-items: center; gap: 30px; }.cart-summary > span { color: var(--muted); }.cart-summary b, .cart-summary strong { color: #df421f; }.cart-summary strong { font-size: 24px; }
</style>
