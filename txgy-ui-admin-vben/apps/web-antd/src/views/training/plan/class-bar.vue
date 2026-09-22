<script lang="ts" setup>
import type { TrainingPlanApi } from './api';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button, message, Select, Space, Tag } from 'ant-design-vue';

import { getClassPage } from './api';
import {
  CLASS_STATUS_OPTIONS,
  CLASS_TYPE_TEXT,
} from './data';

/**
 * 培训管理(模块三)子模块通用的「培训班选择条」
 *
 * - 提供 下拉选班 + 班级关键信息展示；
 * - 选择结果同步到 URL query(classId) 与 sessionStorage，便于在
 *   培训班管理 / 参训人员维护 / 进度看板 / 督促 / 资格核查 间联动；
 * - change 事件携带当前完整班级对象，页面据此决定读写能力。
 */
defineOptions({ name: 'TrainingPlanClassBar' });

const props = defineProps<{ modelValue?: number }>();
const emit = defineEmits<{
  'update:modelValue': [value?: number];
  change: [row?: TrainingPlanApi.Class];
}>();

const route = useRoute();
const router = useRouter();

const STORAGE_KEY = 'training-plan-current-class-id';

const loading = ref(false);
const classList = ref<TrainingPlanApi.Class[]>([]);
const internalValue = ref<number>();
const currentClass = ref<TrainingPlanApi.Class>();

const statusColorMap: Record<number, string> = {};
CLASS_STATUS_OPTIONS.forEach((item) => {
  statusColorMap[item.value] = item.color as string;
});

const selectOptions = computed(() =>
  classList.value.map((item) => ({
    label: item.name || `培训班 ${item.id}`,
    value: item.id!,
  })),
);

/**
 * 培训班截止时间展示：
 * 后端该字段可能返回字符串（yyyy-MM-dd HH:mm:ss），也可能返回时间戳，
 * 因此统一格式化为 yyyy-MM-dd，避免直接 slice 导致页面渲染报错。
 */
function formatDeadline(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  if (typeof value === 'string') {
    return value.slice(0, 10);
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

async function loadOptions() {
  loading.value = true;
  try {
    const data = await getClassPage({ pageNo: 1, pageSize: 200 });
    classList.value = data?.list ?? [];
  } catch {
    classList.value = [];
  } finally {
    loading.value = false;
  }
}

function applyClass(id?: number, syncQuery = true) {
  if (!id) {
    internalValue.value = undefined;
    currentClass.value = undefined;
    emit('update:modelValue', undefined);
    emit('change', undefined);
    return;
  }
  const row = classList.value.find((item) => item.id === id);
  internalValue.value = id;
  currentClass.value = row;
  sessionStorage.setItem(STORAGE_KEY, String(id));
  if (syncQuery && String(route.query.classId ?? '') !== String(id)) {
    router.replace({ query: { ...route.query, classId: String(id) } });
  }
  emit('update:modelValue', id);
  emit('change', row);
}

/** 手动选班 */
function handleSelect(id?: number) {
  applyClass(id, true);
}

async function handleRefresh() {
  const before = currentClass.value?.id;
  await loadOptions();
  if (before != null && classList.value.some((item) => item.id === before)) {
    applyClass(before, false); // 刷新候选，保持当前选择
  } else {
    const fallback = resolveDefaultId();
    applyClass(fallback);
  }
}

/** 无显式选择时：优先「进行中」班级，其次「草稿」，最后列表首行 */
function resolveDefaultId(): number | undefined {
  const saved = Number(sessionStorage.getItem(STORAGE_KEY));
  if (saved && classList.value.some((item) => item.id === saved)) {
    return saved;
  }
  return (
    classList.value.find((item) => item.status === 1)?.id ??
    classList.value.find((item) => item.status === 0)?.id ??
    classList.value[0]?.id
  );
}

async function init() {
  await loadOptions();
  const fromQuery = route.query.classId
    ? Number(route.query.classId)
    : undefined;
  const initId =
    fromQuery && classList.value.some((item) => item.id === fromQuery)
      ? fromQuery
      : resolveDefaultId();
  if (initId && classList.value.some((item) => item.id === initId)) {
    applyClass(initId);
  } else {
    message.info('暂无培训班，请先到「培训班管理」创建并开班');
    applyClass(undefined);
  }
}

// 其它页面通过 URL 携带 classId 切换当前班级（如列表页快捷入口）
watch(
  () => route.query.classId,
  (val) => {
    if (!val || String(val) === String(internalValue.value)) {
      return;
    }
    const id = Number(val);
    if (classList.value.some((item) => item.id === id)) {
      applyClass(id, false);
    }
  },
);

onMounted(init);
</script>

<template>
  <div
    class="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-md border border-dashed border-gray-300 bg-gray-50 px-3 py-2"
  >
    <div class="flex items-center gap-2">
      <span class="whitespace-nowrap text-sm font-medium text-gray-700">
        当前培训班
      </span>
      <Select
        :value="internalValue"
        :loading="loading"
        :options="selectOptions"
        show-search
        option-filter-prop="label"
        placeholder="请选择培训班"
        style="min-width: 240px"
        @change="handleSelect"
      />
      <Button size="small" :loading="loading" @click="handleRefresh">
        刷新班级
      </Button>
    </div>
    <template v-if="currentClass">
      <Tag
        color="blue"
        class="mr-0"
      >
        {{
          currentClass.type != null
            ? CLASS_TYPE_TEXT[currentClass.type] || '-'
            : '-'
        }}
      </Tag>
      <Tag
        :color="
          currentClass.status != null
            ? statusColorMap[currentClass.status] || 'default'
            : 'default'
        "
        class="mr-0"
      >
        {{
          CLASS_STATUS_OPTIONS.find(
            (item) => item.value === currentClass.status,
          )?.label ?? '-'
        }}
      </Tag>
      <Space :size="12" class="text-xs text-gray-500">
        <span>学员 {{ currentClass.studentCount ?? 0 }} 人</span>
        <span>课程 {{ currentClass.courseCount ?? 0 }} 门</span>
        <span v-if="currentClass.deadline">
          截止 {{ formatDeadline(currentClass.deadline) }}
        </span>
      </Space>
    </template>
    <span v-else class="text-xs text-gray-400">未选择培训班</span>
  </div>
</template>
