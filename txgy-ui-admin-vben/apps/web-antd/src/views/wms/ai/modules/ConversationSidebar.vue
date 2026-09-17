<script setup lang="ts">
/**
 * WMS AI 历史对话侧边栏
 * 支持：新建 / 搜索 / 切换 / 重命名 / 置顶 / 删除
 */
import type { AiConversation } from '../composables/useConversations';

import { computed, nextTick, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Empty, Input, message, Modal } from 'ant-design-vue';

import { groupByTime } from '../composables/useConversations';

const props = defineProps<{
  activeId: null | string;
  conversations: AiConversation[];
}>();

const emits = defineEmits<{
  clear: [];
  create: [];
  delete: [conversation: AiConversation];
  rename: [conversation: AiConversation, title: string];
  select: [conversation: AiConversation];
  top: [conversation: AiConversation];
}>();

const searchName = ref('');
const hoverId = ref<null | string>(null);

/** 重命名弹窗（受控 Modal）：
 * 不用 Modal.confirm + onOk 返回 Promise.reject 的方式拦空值——
 * ant-design-vue 的 ActionButton 内部 .then(_, e => Promise.reject(e)) 会产生
 * 无人接管的 rejection，控制台报 pageerror。受控组件由我们决定何时关闭弹窗。 */
const renameVisible = ref(false);
const renameValue = ref('');
const renameTarget = ref<AiConversation | null>(null);
const renameInputRef = ref<InstanceType<typeof Input>>();

watch(renameVisible, (v) => {
  if (v) void nextTick(() => renameInputRef.value?.focus());
});

const groups = computed(() => {
  const keyword = searchName.value.trim();
  const base = keyword
    ? props.conversations.filter((c) => c.title.includes(keyword))
    : props.conversations;
  return groupByTime(base);
});

const visibleGroups = computed(() =>
  Object.keys(groups.value).filter((k) => (groups.value[k]?.length ?? 0) > 0),
);

const total = computed(() => props.conversations.length);

function handleSelect(c: AiConversation) {
  emits('select', c);
}

/** 打开重命名弹窗 */
function openRename(c: AiConversation) {
  renameTarget.value = c;
  renameValue.value = c.title;
  renameVisible.value = true;
}

/** 确认重命名：空标题仅提示，弹窗保持打开 */
function confirmRename() {
  const next = renameValue.value.trim();
  if (!next) {
    message.warning('标题不能为空');
    return;
  }
  renameVisible.value = false;
  if (renameTarget.value) emits('rename', renameTarget.value, next);
}

function handleDelete(c: AiConversation) {
  Modal.confirm({
    content: `删除后该对话记录将不可恢复，确认删除「${c.title}」吗？`,
    okText: '删除',
    okType: 'danger',
    onOk: () => {
      emits('delete', c);
      return Promise.resolve();
    },
    title: '删除对话',
  });
}

function handleClear() {
  Modal.confirm({
    content: '将清空全部未置顶的历史对话，置顶对话会被保留。',
    okText: '清空',
    okType: 'danger',
    onOk: () => {
      emits('clear');
      return Promise.resolve();
    },
    title: '清空历史对话',
  });
}
</script>

<template>
  <div class="flex h-full w-60 shrink-0 flex-col border-r border-gray-200 bg-white">
    <!-- 新建 -->
    <div class="px-3 pb-2 pt-3">
      <Button block class="h-9" type="primary" @click="emits('create')">
        <IconifyIcon class="mr-1 inline size-4" icon="lucide:plus" />
        新建对话
      </Button>
    </div>

    <!-- 搜索 -->
    <div class="px-3 pb-2">
      <Input v-model:value="searchName" allow-clear placeholder="搜索历史记录">
        <template #prefix>
          <IconifyIcon class="size-3.5 text-gray-400" icon="lucide:search" />
        </template>
      </Input>
    </div>

    <!-- 列表 -->
    <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
      <Empty
        v-if="total === 0"
        class="mt-10"
        description="暂无历史对话"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
      />

      <template v-else>
        <div v-for="key in visibleGroups" :key="key" class="mb-1">
          <div class="px-2 pb-1 pt-2 text-xs font-medium text-gray-400">
            {{ key }}
          </div>
          <div
            v-for="c in groups[key]"
            :key="c.id"
            class="group mb-0.5 flex cursor-pointer items-center justify-between rounded-md px-2 py-1.5 transition-colors hover:bg-gray-100"
            :class="c.id === activeId ? 'bg-blue-50 hover:bg-blue-50' : ''"
            @click="handleSelect(c)"
            @mouseenter="hoverId = c.id"
            @mouseleave="hoverId = null"
          >
            <div class="flex min-w-0 items-center gap-1.5">
              <IconifyIcon
                class="size-3.5 shrink-0"
                :class="c.id === activeId ? 'text-blue-500' : 'text-gray-400'"
                :icon="c.pinned ? 'lucide:pin' : 'lucide:message-square'"
              />
              <span
                class="truncate text-sm"
                :class="c.id === activeId ? 'text-blue-600' : 'text-gray-700'"
              >
                {{ c.title }}
              </span>
            </div>

            <div
              v-show="hoverId === c.id || c.id === activeId"
              class="ml-1 flex shrink-0 items-center gap-0.5 text-gray-400"
            >
              <button
                class="rounded p-0.5 hover:bg-gray-200 hover:text-gray-600"
                :title="c.pinned ? '取消置顶' : '置顶'"
                @click.stop="emits('top', c)"
              >
                <IconifyIcon
                  class="size-3.5"
                  :icon="c.pinned ? 'lucide:pin-off' : 'lucide:pin'"
                />
              </button>
              <button
                class="rounded p-0.5 hover:bg-gray-200 hover:text-gray-600"
                title="重命名"
                @click.stop="openRename(c)"
              >
                <IconifyIcon class="size-3.5" icon="lucide:edit-3" />
              </button>
              <button
                class="rounded p-0.5 hover:bg-red-50 hover:text-red-500"
                title="删除"
                @click.stop="handleDelete(c)"
              >
                <IconifyIcon class="size-3.5" icon="lucide:trash-2" />
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 底部清空 -->
    <div
      v-if="total > 0"
      class="flex cursor-pointer items-center justify-center gap-1 border-t border-gray-100 py-2 text-xs text-gray-400 hover:text-red-500"
      @click="handleClear"
    >
      <IconifyIcon class="size-3.5" icon="lucide:trash" />
      <span>清空未置顶对话</span>
    </div>

    <!-- 重命名弹窗（受控，空标题时保持打开） -->
    <Modal
      v-model:open="renameVisible"
      cancel-text="取消"
      ok-text="确定"
      :mask-closable="false"
      title="重命名对话"
      :width="420"
      @ok="confirmRename"
    >
      <Input
        ref="renameInputRef"
        v-model:value="renameValue"
        allow-clear
        placeholder="请输入标题"
        @press-enter="confirmRename"
      />
    </Modal>
  </div>
</template>
