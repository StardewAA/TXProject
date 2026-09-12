<script setup lang="ts">
withDefaults(defineProps<{ title?: string; description?: string }>(), {
  title: '暂无数据',
  description: '当前没有可展示的内容',
})
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">□</div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>
