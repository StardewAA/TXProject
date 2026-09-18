<script setup lang="ts">
import { checkInquiryCategory, type InquiryCategoryOption } from '@/utils/inquiry-category'
defineProps<{ modelValue: string; options: InquiryCategoryOption[]; ready: boolean; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
function choose(event: Event) {
  const input = event.target as HTMLSelectElement
  if (input.value) emit('update:modelValue', input.value)
  input.value = ''
}
</script>
<template>
  <div class="inquiry-category">
    <select aria-label="选择商城分类" :disabled="disabled || !ready" value="" @change="choose">
      <option value="">从商城分类选择（可手填）</option>
      <option v-for="option in options" :key="option.id" :value="option.name">{{ option.path }}</option>
    </select>
    <input aria-label="询价物品分类" :value="modelValue" maxlength="100" :disabled="disabled" placeholder="选择或填写分类" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"/>
    <small role="status">{{ checkInquiryCategory(modelValue, options, ready) }}</small>
  </div>
</template>
<style scoped>
.inquiry-category{display:grid;gap:6px;min-width:220px}input,select{width:100%;box-sizing:border-box;border:1px solid #dcdfe6;border-radius:4px;padding:7px;color:#475569;background:white}input:disabled,select:disabled{background:#f5f7fa;color:#8b939e}small{color:#64748b;line-height:1.5;overflow-wrap:anywhere}
</style>
