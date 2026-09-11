<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import type { Category } from '@/types/mall'

const categories = ref<Category[]>([])
const loading = ref(true)
const roots = computed(() => {
  const rootList = categories.value.filter((item) => !item.parentId || item.parentId === 0)
  return rootList.map((root) => ({
    ...root,
    children: root.children?.length ? root.children : categories.value.filter((item) => item.parentId === root.id),
  }))
})

onMounted(async () => {
  try { categories.value = await productApi.getCategories() } finally { loading.value = false }
})
</script>

<template>
  <div class="container page-section">
    <div class="section-heading"><div><h1>全部商品分类</h1><p>按平台正式分类查找防腐保温产品</p></div></div>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!roots.length" title="暂无商品分类" />
    <div v-else class="category-groups">
      <section v-for="root in roots" :key="root.id" class="category-group panel">
        <RouterLink class="category-group__title" :to="{ path: '/goods', query: { categoryId: root.id } }">
          <span>{{ root.name.slice(0, 1) }}</span><strong>{{ root.name }}</strong>
        </RouterLink>
        <div class="category-group__children">
          <RouterLink v-for="child in root.children" :key="child.id" :to="{ path: '/goods', query: { categoryId: child.id } }">{{ child.name }}</RouterLink>
          <RouterLink v-if="!root.children?.length" :to="{ path: '/goods', query: { categoryId: root.id } }">查看全部</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.category-groups { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }
.category-group { min-height: 130px; display: grid; grid-template-columns: 170px 1fr; overflow: hidden; }
.category-group__title { display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 10px; background: #293740; color: white; }
.category-group__title span { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 50%; background: var(--brand); font-size: 20px; }
.category-group__children { display: flex; align-content: center; align-items: center; flex-wrap: wrap; gap: 12px 24px; padding: 24px; }
.category-group__children a { color: #56636d; }
.category-group__children a:hover { color: var(--brand); }
</style>
