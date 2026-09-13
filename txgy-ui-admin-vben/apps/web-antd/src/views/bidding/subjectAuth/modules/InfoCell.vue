<!-- 键值信息格：左侧灰标签 + 右侧值（空值统一 '—'）。抽出自 index.vue（同 SpinWrap 的理由）。 -->
<script lang="ts" setup>
withDefaults(defineProps<{ label?: string; value?: null | string }>(), { label: '', value: '' });
</script>

<template>
  <div class="rounded border border-gray-100 bg-gray-50 p-2">
    <div class="text-gray-400">{{ label }}</div>
    <div class="font-medium text-gray-700">{{ value || '—' }}</div>
  </div>
</template>
