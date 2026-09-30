<!--
  标书审查 · 字典标签

  职责：按 (dictType, value) 渲染一个 antd Tag，label 与颜色都取字典（bid_review_*）。
  为什么抽组件：改造前 statusTag / severityTag / verdictLabel 在三个面板里各写了一份，
  新增枚举值要改三个文件；现在只有一处，且后端改字典 label 前端自动跟着变。
-->
<script lang="ts" setup>
import { computed } from 'vue';

import { Tag } from 'ant-design-vue';

import { useBiddingDict } from '../composables/useBiddingDict';

const props = defineProps<{
  /** 字典类型，如 bid_review_severity */
  dictType: string;
  /** 字典值，如 HIGH；空值渲染占位符 */
  value?: null | string;
  /** 值缺失时的占位文案 */
  placeholder?: string;
}>();

const { dictTag } = useBiddingDict();

const tag = computed(() => dictTag(props.dictType, props.value));
</script>

<template>
  <Tag :color="tag.color">
    {{ value ? tag.label : (placeholder ?? '—') }}
  </Tag>
</template>
