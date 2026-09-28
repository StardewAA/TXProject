<script lang="ts" setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

/**
 * 对话历史浮层。
 *
 * 两个页面各用一次：求职助理挂在内容区右上角，职位推荐的挂在右侧助手栏头部。
 * 定位交给调用方的 class（is-corner / is-inline），组件本身只管「按钮 + 面板」。
 *
 * 对话内容由父组件持有，这里只是列表；选哪一条、开不开新的都由父组件决定。
 */
interface HistoryItem {
  id: string;
  title: string;
  updatedAt: number;
}

const props = defineProps<{
  activeId: null | string;
  conversations: HistoryItem[];
  open: boolean;
}>();

const emit = defineEmits<{
  close: [];
  create: [];
  select: [id: string];
  toggle: [];
}>();

const root = ref<HTMLElement>();

function onDocumentPointerDown(event: MouseEvent) {
  if (!props.open) return;
  if (root.value && !root.value.contains(event.target as Node)) emit('close');
}

function onKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') emit('close');
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentPointerDown);
  document.addEventListener('keydown', onKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentPointerDown);
  document.removeEventListener('keydown', onKeydown);
});

/** 一分钟内说「刚刚」，一小时内说几分钟前，当天说几点，更早说日期 */
function formatTime(value: number) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const diff = Date.now() - value;
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
  const today = new Date();
  const sameDay =
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate();
  if (sameDay) {
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
  }
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日`;
}
</script>

<template>
  <div ref="root" class="chat-history">
    <button
      class="chat-history-trigger"
      type="button"
      aria-label="查看历史记录"
      :aria-expanded="open"
      @click="emit('toggle')"
    >
      <IconifyIcon icon="lucide:history" />
    </button>

    <div v-if="open" class="chat-history-panel" role="menu" aria-label="历史记录">
      <header class="chat-history-head">
        <strong>历史记录</strong>
        <button type="button" @click="emit('create')">
          <IconifyIcon icon="lucide:plus" /> 新对话
        </button>
      </header>

      <p v-if="!conversations.length" class="chat-history-empty">
        还没有历史记录。聊过之后会自动存到这里，换设备不同步。
      </p>
      <ul v-else class="chat-history-list">
        <li v-for="item in conversations" :key="item.id">
          <button
            type="button"
            :class="{ active: item.id === activeId }"
            :title="item.title"
            @click="emit('select', item.id)"
          >
            <strong>{{ item.title }}</strong>
            <small>{{ formatTime(item.updatedAt) }}</small>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.chat-history {
  position: relative;
}

.chat-history-trigger {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-blue);
  border-radius: 8px;
  background: var(--paper);
  color: var(--muted);
  cursor: pointer;
  font-size: 15px;
}

.chat-history-trigger:hover,
.chat-history-trigger[aria-expanded='true'] {
  border-color: var(--line-blue-strong);
  background: var(--hover-blue);
  color: var(--ink);
}

.chat-history-panel {
  position: absolute;
  z-index: 20;
  top: calc(100% + 8px);
  right: 0;
  display: flex;
  width: 260px;
  max-height: 320px;
  box-sizing: border-box;
  flex-direction: column;
  border: 1px solid var(--line-blue);
  border-radius: var(--radius-card);
  background: var(--paper);
  box-shadow: 0 14px 32px rgb(10 27 51 / 14%);
  overflow: hidden;
}

.chat-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line-soft);
}

.chat-history-head strong {
  color: var(--ink-strong);
  font-size: 13px;
  font-weight: 650;
}

.chat-history-head button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: var(--olive-deep);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.chat-history-head button:hover {
  color: var(--ink);
}

.chat-history-empty {
  margin: 0;
  padding: 18px 14px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.chat-history-list {
  display: grid;
  align-content: start;
  gap: 2px;
  margin: 0;
  overflow-y: auto;
  padding: 6px;
  list-style: none;
}

.chat-history-list button {
  display: grid;
  width: 100%;
  gap: 2px;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.chat-history-list button:hover {
  background: var(--hover-blue);
}

.chat-history-list button.active {
  background: var(--hover-blue);
}

.chat-history-list strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-history-list small {
  color: var(--muted-soft);
  font-size: 11px;
}
</style>
