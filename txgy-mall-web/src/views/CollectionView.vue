<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import ProductCard from '@/components/ProductCard.vue'
import type { Product } from '@/types/mall'

const props = defineProps<{ mode: 'favorite' | 'history' }>()
const products = ref<Product[]>([])
const loading = ref(true)
let requestId = 0

async function load() {
  const currentRequest = ++requestId
  const mode = props.mode
  loading.value = true
  try {
    const page = mode === 'favorite'
      ? await productApi.getFavorites({ pageNo: 1, pageSize: 50 })
      : await productApi.getHistory({ pageNo: 1, pageSize: 50 })
    if (currentRequest !== requestId) return
    products.value = (page?.list || []).map((item) => mode === 'history'
      ? {
          ...item,
          id: (item as Product & { spuId?: Product['id'] }).spuId || item.id,
          name: (item as Product & { spuName?: string }).spuName || item.name,
        }
      : item)
  } finally { if (currentRequest === requestId) loading.value = false }
}

async function clear() {
  await ElMessageBox.confirm('确定清空全部浏览足迹吗？', '清空足迹', { type: 'warning', confirmButtonText: '确定清空', cancelButtonText: '取消' })
  await productApi.clearHistory(); products.value = []; ElMessage.success('已清空')
}

onMounted(load)
watch(() => props.mode, load)
</script>

<template>
  <MemberPage :title="mode === 'favorite' ? '商品收藏' : '浏览足迹'" :description="mode === 'favorite' ? '收藏状态与移动端保持一致' : '查看最近浏览的商品'">
    <template v-if="mode === 'history' && products.length" #actions><el-button @click="clear">清空足迹</el-button></template>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!products.length" :title="mode === 'favorite' ? '暂无收藏商品' : '暂无浏览足迹'" />
    <div v-else class="collection-grid"><ProductCard v-for="product in products" :key="product.id" :product="product" /></div>
  </MemberPage>
</template>

<style scoped>.collection-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }</style>
