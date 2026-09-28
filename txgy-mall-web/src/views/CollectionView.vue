<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import ProductCard from '@/components/ProductCard.vue'
import { collectionEntry, type CollectionEntry } from '@/utils/collection-product'

const props = defineProps<{ mode: 'favorite' | 'history' }>()
const entries = ref<CollectionEntry[]>([]), loading = ref(true), failed = ref(false), changing = ref(false)
let requestId = 0, actionId = 0
async function load() {
  const currentRequest = ++requestId, mode = props.mode
  loading.value = true; failed.value = false
  try {
    const page = mode === 'favorite'
      ? await productApi.getFavorites({ pageNo: 1, pageSize: 50 })
      : await productApi.getHistory({ pageNo: 1, pageSize: 50 })
    if (currentRequest === requestId) entries.value = (page?.list || []).map(collectionEntry)
  } catch { if (currentRequest === requestId) failed.value = true }
  finally { if (currentRequest === requestId) loading.value = false }
}
async function remove(entry: CollectionEntry) {
  if (changing.value || loading.value) return
  const mode = props.mode, ticket = requestId
  try { await ElMessageBox.confirm(mode === 'favorite' ? '确定取消此商品的收藏？商品失效也可以取消收藏。' : '确定删除此商品的浏览足迹？', '移除记录', { confirmButtonText: '确定移除', cancelButtonText: '取消' }) } catch { return }
  if (ticket !== requestId || mode !== props.mode || changing.value) return
  const action = ++actionId
  changing.value = true
  try {
    if (mode === 'favorite') await productApi.deleteFavorite(entry.spuId)
    else await productApi.deleteHistory([entry.spuId])
    if (ticket !== requestId) return
    entries.value = entries.value.filter(item => item.spuId !== entry.spuId)
    ElMessage.success(mode === 'favorite' ? '已取消收藏' : '已删除足迹')
  } catch { /* 请求层显示错误；失败保留原记录，不伪造移除成功。 */ }
  finally { if (action === actionId) changing.value = false }
}
async function clear() {
  if (changing.value || loading.value || props.mode !== 'history') return
  const ticket = requestId
  try { await ElMessageBox.confirm('确定清空全部浏览足迹吗？', '清空足迹', { type: 'warning', confirmButtonText: '确定清空', cancelButtonText: '取消' }) } catch { return }
  if (ticket !== requestId || props.mode !== 'history' || changing.value) return
  const action = ++actionId
  changing.value = true
  try { await productApi.clearHistory(); if (ticket === requestId) { entries.value = []; ElMessage.success('已清空') } }
  catch { /* 失败保持现有记录。 */ }
  finally { if (action === actionId) changing.value = false }
}
watch(() => props.mode, () => { actionId++; changing.value = false; entries.value = []; void load() }, { immediate: true })
onBeforeUnmount(() => { requestId++; actionId++ })
</script>
<template>
  <MemberPage :title="mode === 'favorite' ? '商品收藏' : '浏览足迹'" :description="mode === 'favorite' ? '收藏状态与移动端保持一致' : '查看最近浏览的商品'">
    <template v-if="mode === 'history' && entries.length" #actions><el-button :disabled="changing||loading" @click="clear">清空足迹</el-button></template>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <el-alert v-else-if="failed" type="error" title="记录读取失败，请重试；没有清空您的收藏或足迹" :closable="false"><el-button @click="load">重试</el-button></el-alert>
    <EmptyState v-else-if="!entries.length" :title="mode === 'favorite' ? '暂无收藏商品' : '暂无浏览足迹'" />
    <div v-else class="collection-grid">
      <article v-for="entry in entries" :key="entry.relationId" class="collection-entry">
        <ProductCard v-if="entry.product" :product="entry.product" />
        <div v-else class="unavailable-product" role="note"><div class="unavailable-picture">暂不可购买</div><h3>商品暂不可购买</h3><p>{{ entry.reason }}</p><small>仅保留{{ mode === 'favorite' ? '收藏' : '足迹' }}记录，不展示未审核的商品价格和内容。</small></div>
        <el-button link :disabled="changing||loading" class="remove-record" @click="remove(entry)">{{ mode === 'favorite' ? '取消收藏' : '删除此足迹' }}</el-button>
      </article>
    </div>
  </MemberPage>
</template>
<style scoped>
.collection-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.collection-entry{display:flex;flex-direction:column;min-width:0}.collection-entry :deep(.product-card){flex:1}.unavailable-product{flex:1;padding:18px;border:1px solid #d8e0e4;border-radius:10px;background:#f5f7f8;color:#64748b}.unavailable-picture{height:155px;background:#e8edf0;display:grid;place-items:center;border-radius:6px;margin-bottom:18px;font-size:20px}.unavailable-product h3{color:#475569;font-size:16px}.unavailable-product p{margin:14px 0;overflow-wrap:anywhere;line-height:1.6}.remove-record{margin:12px auto}
</style>
