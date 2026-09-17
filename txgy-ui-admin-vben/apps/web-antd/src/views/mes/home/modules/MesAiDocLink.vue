<script lang="ts" setup>
/**
 * MES 助手 - 聊天消息里的"可点击文档链接"
 *
 * 【新增模块 1】聊天消息链接组件
 * 1. 纯展示 + 点击事件，不含业务逻辑，可复用到质检单/入库单等任意单据
 * 2. 使用原生 <a> + 局部样式，避免引入额外 UI 库；在气泡内保持紧凑、长文案自动换行
 */
defineProps<{
  /** 链接文字，如【点击打开生产工单 Word 文档】 */
  text: string;
  /** 是否禁用 */
  disabled?: boolean;
}>();

const emit = defineEmits<{ open: [] }>();
</script>

<template>
  <a
    class="mes-ai-doc-link"
    :class="{ 'is-disabled': disabled }"
    href="javascript:void(0)"
    @click="!disabled && emit('open')"
  >
    <span class="mes-ai-doc-link__icon">📄</span>
    <span class="mes-ai-doc-link__text">{{ text }}</span>
  </a>
</template>

<style scoped>
/* 链接：块级展示，文字过长自动换行，不撑破气泡 */
.mes-ai-doc-link {
  display: inline-flex;
  gap: 4px;
  align-items: flex-start;
  max-width: 100%;
  font-size: 13px;
  line-height: 1.7;
  color: #1677ff;
  text-align: left;
  text-decoration: none;
  white-space: normal;
  cursor: pointer;
}

.mes-ai-doc-link:hover {
  text-decoration: underline;
}

.mes-ai-doc-link.is-disabled {
  color: #9ca3af;
  cursor: not-allowed;
}

.mes-ai-doc-link__icon {
  flex-shrink: 0;
}

.mes-ai-doc-link__text {
  word-break: break-all;
}
</style>
