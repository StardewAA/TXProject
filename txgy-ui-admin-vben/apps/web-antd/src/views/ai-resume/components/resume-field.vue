<script lang="ts" setup>
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import type { FieldSuggestion } from './resume-suggestions';

/**
 * 带字数计数与 AI 建议卡的多行字段。
 *
 * 对齐参考站：文本域右下角显示 `n/1000`，AI 建议以卡片形式出现在字段下方，
 * 卡片上是建议正文 + 理由 + 「忽略 / 使用」。
 *
 * 计数**只提示不拦截**：参考站用 wangEditor 的 maxLength 硬卡在 1000，
 * 照着做会把存量数据坑死 —— 现有简历里超过 1000 字的字段一旦被 maxlength 拦下，
 * 用户删几个字就再打不回来。这里只在超限时把数字标红，交给人判断。
 */

const props = withDefaults(
  defineProps<{
    label: string;
    modelValue?: string;
    placeholder?: string;
    required?: boolean;
    rows?: number;
    suggestions?: FieldSuggestion[];
  }>(),
  {
    modelValue: '',
    placeholder: '',
    required: false,
    rows: 3,
    suggestions: () => [],
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
  decide: [item: FieldSuggestion, decision: 'ACCEPT' | 'REJECT'];
}>();

/** 与服务端 OptimizeResultValidator 的 MAX_TEXT_LENGTH 口径一致 */
const LIMIT = 1000;

const text = computed(() => String(props.modelValue ?? ''));
const overLimit = computed(() => text.value.length > LIMIT);

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value);
}
</script>

<template>
  <div class="field">
    <span class="field-label">
      {{ label }}<em v-if="required">*</em>
    </span>

    <div class="box">
      <textarea
        :value="text"
        :rows="rows"
        :placeholder="placeholder"
        @input="onInput"
      />
      <span class="count" :class="{ 'is-over': overLimit }">
        {{ text.length }}/{{ LIMIT }}
      </span>
    </div>

    <article
      v-for="(item, index) in suggestions"
      :key="`${item.fieldPath}-${index}-${item.originalValue}`"
      class="sug"
    >
      <header class="sug-head">
        <IconifyIcon icon="lucide:sparkles" />
        <span>AI 建议</span>
      </header>
      <p class="sug-value">{{ item.suggestedValue }}</p>
      <p v-if="item.reason" class="sug-reason">{{ item.reason }}</p>
      <footer class="sug-actions">
        <button
          type="button"
          class="btn btn-ghost"
          @click="emit('decide', item, 'REJECT')"
        >
          忽略
        </button>
        <button
          type="button"
          class="btn btn-solid"
          @click="emit('decide', item, 'ACCEPT')"
        >
          使用
        </button>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.field-label {
  color: #7d7d7d;
  font-size: 12px;
  line-height: 1.4;
}

.field-label em {
  color: #d8504e;
  font-style: normal;
}

/* 计数叠在右下角，所以 textarea 底部留出它占的高度，正文不会钻到数字底下 */
.box {
  position: relative;
  display: flex;
}

.box textarea {
  width: 100%;
  padding: 7px 10px 22px;
  border: 1px solid #e6e6e6;
  border-radius: 6px;
  background: #fff;
  color: #292929;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  box-sizing: border-box;
  resize: vertical;
}

.box textarea:focus {
  border-color: #9ab583;
  outline: none;
}

.count {
  position: absolute;
  right: 8px;
  bottom: 4px;
  color: #a8a8a8;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  pointer-events: none;
}

.count.is-over {
  color: #d8504e;
}

/* ── AI 建议卡 ── */
.sug {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
  padding: 10px;
  border: 1px solid #dde8d2;
  border-radius: 8px;
  background: #f7faf4;
}

.sug-head {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #4e6840;
  font-size: 12px;
  font-weight: 600;
}

.sug-value {
  margin: 0;
  color: #292929;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.sug-reason {
  margin: 0;
  color: #7d7d7d;
  font-size: 12px;
  line-height: 1.6;
}

.sug-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  padding: 4px 14px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.btn-ghost {
  border: 1px solid #dcdcdc;
  background: #fff;
  color: #5e5e5e;
}

.btn-ghost:hover {
  border-color: #b8b8b8;
  color: #292929;
}

.btn-solid {
  border: 1px solid #6e8d57;
  background: #6e8d57;
  color: #fff;
}

.btn-solid:hover {
  background: #5e7a49;
  border-color: #5e7a49;
}
</style>
