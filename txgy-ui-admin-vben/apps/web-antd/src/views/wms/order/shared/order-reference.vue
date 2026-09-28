<script setup lang="ts">
import { TypographyParagraph } from 'ant-design-vue';
defineProps<{ no?: string; businessNo?: string }>();
</script>
<template>
  <div class="space-y-2 rounded border p-3 text-sm">
    <div>
      <span class="text-gray-500">WMS 单号</span>
      <TypographyParagraph class="!mb-0 break-all" :copyable="no ? { text: no } : false">{{ no || '-' }}</TypographyParagraph>
    </div>
    <div>
      <span class="text-gray-500">来源业务单号</span>
      <TypographyParagraph class="!mb-0 break-all" :copyable="businessNo ? { text: businessNo } : false">{{ businessNo || '-' }}</TypographyParagraph>
    </div>
  </div>
</template>
