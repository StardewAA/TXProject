<script lang="ts" setup>
/**
 * 首页「我的待办任务」面板
 *
 * 报工审批等单据的审批操作已统一收敛到 BPM 待办，本面板把当前登录人的待办任务
 * 直接摆到 MES 首页，点击任意一条跳转「工作流程 → 待办任务」处理。
 * 数据来源：GET /bpm/task/todo-page（按登录人过滤）
 */
import type { BpmTaskApi } from '#/api/bpm/task';

import { onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Card, Empty, Spin } from 'ant-design-vue';

import { getTaskTodoPage } from '#/api/bpm/task';

defineOptions({ name: 'MesHomeTodoPanel' });

const emit = defineEmits<{
  navigate: [name: string];
}>();

/** 首页只展示最近几条，全部任务去待办页看 */
const TODO_PAGE_SIZE = 5;

const loading = ref(false);
const tasks = ref<BpmTaskApi.Task[]>([]);
const total = ref(0);

/** 时间字段可能是时间戳或字符串，两种都兜住 */
function formatTime(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const date = new Date(
    typeof value === 'number' ? value : String(value).replaceAll('-', '/'),
  );
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 加载当前登录人的待办任务 */
async function loadTodo() {
  loading.value = true;
  try {
    const page = await getTaskTodoPage({
      pageNo: 1,
      pageSize: TODO_PAGE_SIZE,
    });
    tasks.value = page.list ?? [];
    total.value = page.total ?? 0;
  } catch {
    // 无 BPM 权限或接口异常时不阻断首页其它面板
    tasks.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

onMounted(loadTodo);
</script>

<template>
  <Card class="h-full">
    <template #title>
      我的待办任务
      <span v-if="total > 0" class="text-muted-foreground ml-1 text-xs">
        （{{ total }}）
      </span>
    </template>
    <template #extra>
      <a v-if="total > 0" @click="emit('navigate', 'BpmTodoTask')">
        查看全部
      </a>
    </template>

    <Spin :spinning="loading">
      <Empty
        v-if="tasks.length === 0"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
        description="暂无待办任务"
      />
      <div v-else class="flex flex-col">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="hover:bg-accent flex cursor-pointer items-center gap-3 border-b px-2 py-3 transition-colors last:border-b-0"
          @click="emit('navigate', 'BpmTodoTask')"
        >
          <div
            class="flex size-10 flex-shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-500"
          >
            <IconifyIcon class="size-5" icon="lucide:clipboard-check" />
          </div>
          <div class="flex min-w-0 flex-1 flex-col gap-0.5">
            <span class="truncate text-sm font-medium">
              {{ task.name || '审批任务' }}
            </span>
            <span class="text-muted-foreground truncate text-xs">
              {{ task.processInstance?.name || '流程任务' }}
              · 发起人 {{ task.processInstance?.startUser?.nickname || '-' }}
            </span>
          </div>
          <span class="text-muted-foreground flex-shrink-0 text-xs">
            {{ formatTime(task.createTime) }}
          </span>
        </div>
      </div>
    </Spin>
  </Card>
</template>
