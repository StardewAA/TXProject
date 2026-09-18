<script lang="ts" setup>
import type { TrainingLearningApi } from '../api';

import { computed, onMounted, ref } from 'vue';

import {
  Button,
  Card,
  Modal,
  Progress,
  Select,
  Statistic,
  Table,
  message,
} from 'ant-design-vue';

import { getAllClasses } from '../../plan/api';
import { getCourseTrack, getLearningClassStudents, getLearningStats } from '../api';

defineOptions({ name: 'TrainingLearningTrack' });

// ========== 培训管理联动：培训班 / 在班学员 ==========
const classOptions = ref<{ label: string; value: number }[]>([]);
const classStudentOptions = ref<TrainingLearningApi.ClassStudentOption[]>([]);
const studentSelectOptions = computed(() =>
  classStudentOptions.value.map((item) => ({
    label: `${item.userName || `用户${item.userId}`}${
      item.deptName ? `（${item.deptName}）` : ''
    }`,
    value: item.userId!,
  })),
);

async function loadClassOptions() {
  const classes = await getAllClasses();
  classOptions.value = (classes ?? []).map((item) => ({
    label: item.name ?? `培训班${item.id}`,
    value: item.id!,
  }));
}

async function loadClassStudents() {
  classStudentOptions.value = (await getLearningClassStudents()) ?? [];
}

// ========== 按课程汇总统计（与培训管理班级学习进度同源） ==========
const loading = ref(false);
const list = ref<TrainingLearningApi.LearningStat[]>([]);
const classId = ref<number | undefined>();

const stats = computed(() => {
  const rows = list.value;
  const sum = (fn: (item: TrainingLearningApi.LearningStat) => number) =>
    rows.reduce((acc, item) => acc + fn(item), 0);
  return {
    courseCount: rows.length,
    taskTotal: sum((item) => Number(item.taskTotal ?? 0)),
    completed: sum((item) => Number(item.completedCount ?? 0)),
    creditEarned: sum((item) => Number(item.creditEarned ?? 0)),
  };
});

async function load() {
  loading.value = true;
  try {
    list.value = (await getLearningStats(classId.value)) ?? [];
  } finally {
    loading.value = false;
  }
}

// ========== 查看某学员在某课程的学习轨迹 ==========
const trackVisible = ref(false);
const trackLoading = ref(false);
const trackCourse = ref<TrainingLearningApi.LearningStat | null>(null);
const trackUserId = ref<number | undefined>();
const trackData = ref<TrainingLearningApi.CourseTrack | null>(null);

function openTrack(row: TrainingLearningApi.LearningStat) {
  trackCourse.value = row;
  trackUserId.value = undefined;
  trackData.value = null;
  trackVisible.value = true;
}

async function loadTrack() {
  if (!trackUserId.value || !trackCourse.value?.courseId) {
    message.warning('请选择学员');
    return;
  }
  trackLoading.value = true;
  try {
    trackData.value =
      (await getCourseTrack(trackUserId.value, trackCourse.value.courseId)) ??
      null;
  } finally {
    trackLoading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadClassOptions(), loadClassStudents()]);
  load();
});
</script>

<template>
  <div>
    <div class="mb-3 grid grid-cols-4 gap-3">
      <Card size="small">
        <Statistic title="课程数" :value="stats.courseCount" />
      </Card>
      <Card size="small">
        <Statistic title="课程任务数" :value="stats.taskTotal" />
      </Card>
      <Card size="small">
        <Statistic title="已完成任务" :value="stats.completed" />
      </Card>
      <Card size="small">
        <Statistic title="已获学时合计" :value="stats.creditEarned" />
      </Card>
    </div>

    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Select
        v-model:value="classId"
        class="!w-64"
        allow-clear
        show-search
        option-filter-prop="label"
        placeholder="按培训班筛选（默认全部在班学员）"
        :options="classOptions"
        @change="load"
      />
      <Button type="primary" ghost :loading="loading" @click="load">刷新</Button>
      <span class="text-sm text-gray-500">
        任务数与「培训管理 → 班级学习进度」同源（培训班学习任务 + 学习任务合并去重）
      </span>
    </div>

    <Table
      row-key="courseId"
      :data-source="list"
      :loading="loading"
      :pagination="false"
      :columns="[
        { title: '课程', dataIndex: 'courseTitle', key: 'courseTitle' },
        { title: '任务数', dataIndex: 'taskTotal', key: 'taskTotal', width: 90, align: 'center' },
        { title: '待学习', dataIndex: 'todoCount', key: 'todoCount', width: 90, align: 'center' },
        { title: '学习中', dataIndex: 'learningCount', key: 'learningCount', width: 90, align: 'center' },
        { title: '已完成', dataIndex: 'completedCount', key: 'completedCount', width: 90, align: 'center' },
        { title: '平均进度', key: 'avgProgress', width: 170 },
        { title: '完成率(%)', dataIndex: 'completedRate', key: 'completedRate', width: 100, align: 'center' },
        { title: '任务总学时', dataIndex: 'creditTotal', key: 'creditTotal', width: 110, align: 'center' },
        { title: '已获学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 100, align: 'center' },
        { title: '操作', key: 'actions', width: 110, align: 'center' },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'avgProgress'">
          <Progress :percent="Number(record.avgProgressPercent ?? 0)" size="small" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openTrack(record)">
            查看轨迹
          </Button>
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="trackVisible"
      :title="`课程学习轨迹 - ${trackCourse?.courseTitle ?? ''}`"
      :width="900"
      :footer="null"
    >
      <div class="mb-3 flex flex-wrap items-center gap-3">
        <Select
          v-model:value="trackUserId"
          class="!w-72"
          show-search
          option-filter-prop="label"
          placeholder="选择学员（培训班已导入学员）"
          :options="studentSelectOptions"
          @change="loadTrack"
        />
        <Button type="primary" ghost :loading="trackLoading" @click="loadTrack">
          查询
        </Button>
        <span v-if="trackData" class="text-sm text-gray-500">
          {{ trackData.nickname || `用户${trackData.userId}` }} ·
          有效 {{ trackData.totalValidSeconds ?? 0 }} 秒 · 已获
          {{ trackData.creditEarned ?? 0 }} 学时
        </span>
      </div>
      <Table
        v-if="trackData"
        row-key="mediaId"
        :data-source="trackData.mediaTracks ?? []"
        :pagination="false"
        :columns="[
          { title: '媒资', dataIndex: 'name', key: 'name' },
          { title: '类型', dataIndex: 'mediaTypeName', key: 'mediaTypeName', width: 90, align: 'center' },
          { title: '会话数', dataIndex: 'sessionCount', key: 'sessionCount', width: 90, align: 'center' },
          { title: '有效秒', dataIndex: 'totalValidSeconds', key: 'totalValidSeconds', width: 100, align: 'center' },
          { title: '进度(%)', dataIndex: 'ratioPercent', key: 'ratioPercent', width: 100, align: 'center' },
          { title: '已获学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 100, align: 'center' },
        ]"
      >
        <template #expandedRowRender="{ record }">
          <Table
            row-key="sessionId"
            size="small"
            :data-source="record.sessions ?? []"
            :pagination="false"
            :columns="[
              { title: '开始时间', dataIndex: 'startAt', key: 'startAt' },
              { title: '结束时间', dataIndex: 'endAt', key: 'endAt' },
              { title: '有效秒', dataIndex: 'validSeconds', key: 'validSeconds' },
              { title: '结束原因', dataIndex: 'endReason', key: 'endReason' },
              { title: '折算学时', dataIndex: 'creditEarned', key: 'creditEarned' },
            ]"
          />
        </template>
      </Table>
      <div v-else class="py-6 text-center text-sm text-gray-400">
        请选择学员后查询学习轨迹
      </div>
    </Modal>
  </div>
</template>
