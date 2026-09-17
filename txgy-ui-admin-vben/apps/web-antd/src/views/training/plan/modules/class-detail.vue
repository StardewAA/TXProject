<script lang="ts" setup>
import type { TrainingPlanApi } from '../api';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Descriptions, Table, Tag } from 'ant-design-vue';

import { getClassCourseList } from '../api';
import {
  CLASS_STATUS_OPTIONS,
  CLASS_TYPE_TEXT,
  formatTargetPosts,
  parseTargetCondition,
  targetConditionSummary,
} from '../data';
import { loadPostOptions } from '../post-options';

defineOptions({ name: 'TrainingPlanClassDetail' });

/** 当前班级（由列表行传入） */
const currentClass = ref<TrainingPlanApi.Class>();
const courseList = ref<TrainingPlanApi.ClassCourse[]>([]);
const loading = ref(false);

const statusColorMap: Record<number, string> = {};
CLASS_STATUS_OPTIONS.forEach((item) => {
  statusColorMap[item.value] = item.color as string;
});

/** 后端日期字段为毫秒时间戳，统一格式化为 YYYY-MM-DD HH:mm */
function formatDateTime(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value).slice(0, 16);
  }
  const pad = (num: number) => String(num).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

async function loadDetail(classId: number) {
  courseList.value = await getClassCourseList(classId);
}

/** 岗位 ID → 岗位名称（用于回显参训岗位） */
const postNameMap = ref<Map<number, string>>(new Map());

async function loadPostNames() {
  if (postNameMap.value.size > 0) {
    return;
  }
  const options = await loadPostOptions();
  postNameMap.value = new Map(options.map((item) => [item.value, item.label]));
}

/** 参训岗位（按岗位圈选参训人员）：回显岗位名称 */
const scopePostNames = computed(() => {
  const scope = parseTargetCondition(currentClass.value?.targetCondition);
  if (scope.postIds.length === 0) {
    return '';
  }
  return scope.postIds
    .map((id) => postNameMap.value.get(id) ?? `#${id}`)
    .join('、');
});

/** 定岗（结业后岗位） */
const targetPostNames = computed(() =>
  formatTargetPosts(currentClass.value?.targetPosts),
);

const [ModalWrap, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      currentClass.value = undefined;
      courseList.value = [];
      return;
    }
    const data = modalApi.getData<TrainingPlanApi.Class>();
    currentClass.value = data;
    loading.value = true;
    try {
      // 岗位名称映射（回显参训岗位用），失败不影响详情展示
      await loadPostNames().catch(() => undefined);
      if (data?.id) {
        await loadDetail(data.id);
      }
    } finally {
      loading.value = false;
    }
  },
});

const courseColumns = [
  { title: '课程编码', dataIndex: 'courseCode', key: 'courseCode', width: 180 },
  { title: '课程标题', dataIndex: 'courseTitle', key: 'courseTitle' },
  {
    title: '总时长(分钟)',
    dataIndex: 'durationMinutes',
    key: 'durationMinutes',
    width: 140,
  },
];
</script>

<template>
  <ModalWrap
    :title="`培训班信息：${currentClass?.name ?? ''}`"
    class="w-3/4"
    :loading="loading"
  >
    <template v-if="currentClass">
      <Descriptions
        :column="2"
        size="small"
        bordered
        class="mb-3"
      >
        <Descriptions.Item label="培训班编码">
          {{ currentClass.code || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="培训类型">
          {{
            currentClass.type != null
              ? CLASS_TYPE_TEXT[currentClass.type] || '-'
              : '-'
          }}
        </Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag
            :color="
              currentClass.status != null
                ? statusColorMap[currentClass.status] || 'default'
                : 'default'
            "
          >
            {{
              CLASS_STATUS_OPTIONS.find(
                (item) => item.value === currentClass.status,
              )?.label ?? '-'
            }}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="开班时间">
          {{ formatDateTime(currentClass.startTime) }}
        </Descriptions.Item>
        <Descriptions.Item label="截止时间">
          {{ formatDateTime(currentClass.deadline) }}
        </Descriptions.Item>
        <Descriptions.Item label="在班学员">
          {{ currentClass.studentCount ?? 0 }} 人
        </Descriptions.Item>
        <Descriptions.Item label="绑定课程">
          {{ courseList.length }} 门
        </Descriptions.Item>
        <Descriptions.Item label="参训对象筛选条件" :span="2">
          {{ targetConditionSummary(currentClass.targetCondition) }}
        </Descriptions.Item>
        <Descriptions.Item label="参训岗位（按岗位圈选）" :span="2">
          {{ scopePostNames || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="定岗（结业后岗位）" :span="2">
          {{ targetPostNames || '-' }}
        </Descriptions.Item>
      </Descriptions>

      <div class="mb-2 text-sm font-medium text-gray-700">
        绑定课程清单（定课快照）
      </div>
      <Table
        row-key="courseId"
        size="small"
        :columns="courseColumns"
        :data-source="courseList"
        :pagination="false"
      />
    </template>
  </ModalWrap>
</template>
