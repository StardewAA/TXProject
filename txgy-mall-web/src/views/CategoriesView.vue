<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import type { Category } from '@/types/mall'

const categories = ref<Category[]>([])
const loading = ref(true)
const failed = ref(false)
const search = ref('')
function presentation(name: string) {
  if (/防腐|漆|胶/.test(name)) return { image: 'protection', tip: '关注基材处理、耐腐蚀性能与涂层配套', tag: '表面防护' }
  if (/保温|保冷|防潮|外护/.test(name)) return { image: 'insulation', tip: '核对使用温度、厚度及防潮外护要求', tag: '温度管理' }
  return { image: 'pipes', tip: '按材质、规格与工程使用条件选择配套', tag: '工程配套' }
}
const roots = computed(() => {
  const rootList = categories.value.filter((item) => !item.parentId || item.parentId === 0)
  return rootList.map((root) => ({
    ...root,
    children: root.children?.length ? root.children : categories.value.filter((item) => item.parentId === root.id),
  }))
})
const visibleRoots = computed(() => roots.value.filter(root =>
  !search.value.trim() || [root.name, ...root.children.map(child => child.name)].some(name => name.includes(search.value.trim()))))

async function load() {
  loading.value = true
  failed.value = false
  try { categories.value = await productApi.getCategories() || [] } catch { failed.value = true } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="container page-section">
    <header class="catalog-header"><div><span class="catalog-eyebrow">MATERIAL DIRECTORY / 材料目录</span><h1>从工程需要，找到合适材料</h1><p>防腐涂装 · 保温保冷 · 管材与施工配套</p></div><label class="catalog-search"><span>查找分类</span><input v-model="search" type="search" placeholder="输入分类名称，如保温、底漆" /></label></header>
    <div class="catalog-summary"><strong>全部商品分类</strong><span>{{ visibleRoots.length }} 个分类方向</span><span class="catalog-hint">选材提示：先确认工况，再核对规格与数量</span></div>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="failed" title="分类加载失败"><el-button @click="load">重新加载</el-button></EmptyState>
    <EmptyState v-else-if="!roots.length" title="暂无商品分类" />
    <EmptyState v-else-if="!visibleRoots.length" title="没有匹配的分类"><el-button @click="search = ''">清空搜索</el-button></EmptyState>
    <div v-else class="category-groups">
      <section v-for="root in visibleRoots" :key="root.id" class="category-group panel">
        <MallLink class="category-group__title" :to="{ path: '/goods', query: { categoryId: root.id } }">
          <img :src="'/banners/' + presentation(root.name).image + '.svg'" alt="" /><small>{{ presentation(root.name).tag }}</small><strong>{{ root.name }}</strong><span class="category-arrow">↗</span>
        </MallLink>
        <p class="category-tip">{{ presentation(root.name).tip }}</p>
        <div class="category-group__children">
          <MallLink v-for="child in root.children" :key="child.id" :to="{ path: '/goods', query: { categoryId: child.id } }">{{ child.name }}</MallLink>
        </div>
        <MallLink class="category-all" :to="{ path: '/goods', query: { categoryId: root.id } }">浏览{{ root.name }} <span>→</span></MallLink>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.catalog-header{display:flex;align-items:center;justify-content:space-between;gap:30px;padding:36px;border-radius:16px;background:linear-gradient(120deg,#e2efea,#f5f8fa);border:1px solid #d8e7e3}.catalog-eyebrow{font-size:12px;letter-spacing:2px;color:#147d6b}.catalog-header h1{font-size:28px;margin:14px 0}.catalog-header p{color:#62767e;margin:0}.catalog-search{display:grid;gap:10px;font-size:13px;color:#526770}.catalog-search input{width:300px;padding:13px 16px;border:1px solid #cbdcd6;border-radius:8px;background:white}.catalog-summary{display:flex;gap:16px;align-items:center;padding:26px 0 18px}.catalog-summary strong{font-size:20px}.catalog-summary span{color:#788a92;font-size:13px}.catalog-hint{margin-left:auto}
.category-groups{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}.category-group{display:flex;flex-direction:column;overflow:hidden;border-radius:14px;transition:box-shadow .2s,transform .2s}.category-group:hover{transform:translateY(-3px);box-shadow:0 12px 30px #163c3912}.category-group__title{position:relative;height:132px;display:flex;flex-direction:column;justify-content:center;padding:24px;color:white;overflow:hidden}.category-group__title img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}.category-group__title small,.category-group__title strong,.category-arrow{position:relative}.category-group__title small{color:#bde5de;font-size:12px;margin-bottom:10px}.category-group__title strong{font-size:23px}.category-arrow{position:absolute;right:24px;bottom:28px;font-size:24px}.category-tip{font-size:13px;line-height:1.7;color:#72828a;margin:18px 20px 12px}.category-group__children{display:flex;flex-wrap:wrap;gap:8px;padding:0 20px 18px}.category-group__children a{background:#edf5f2;color:#306e60;border-radius:5px;padding:6px 10px;font-size:13px}.category-all{margin:auto 20px 0;padding:14px 0;border-top:1px solid #edf0f1;display:flex;justify-content:space-between;font-size:13px;color:#147d6b;font-weight:600}@media(max-width:1100px){.category-groups{grid-template-columns:repeat(2,minmax(0,1fr))}.catalog-header{padding:24px}.catalog-header h1{font-size:24px}.catalog-search input{width:260px}}
</style>
