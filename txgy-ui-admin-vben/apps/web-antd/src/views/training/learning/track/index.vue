<script lang="ts" setup>
import type { TrainingLearningApi } from '../api';

import { onMounted, ref } from 'vue';

import {
  Button,
  Input,
  Modal,
  Table,
  message,
} from 'ant-design-vue';

import { getCourseTrack, getLearningStats } from '../api';

defineOptions({ name: 'TrainingLearningTrack' });

const loading = ref(false);
const list = ref<TrainingLearningApi.LearningStat[]>([]);

async function load() {
  loading.value = true;
  try {
    list.value = (await getLearningStats()) ?? [];
  } finally {
    loading.value = false;
  }
}

const trackVisible = ref(false);
const trackLoading = ref(false);
const trackQuery = ref({ userId: undefined as number | undefined, courseId: undefined as number | undefined });
const trackData = ref<TrainingLearningApi.CourseTrack | null>(null);

function openTrack(row: TrainingLearningApi.LearningStat) {
  trackQuery.value = { userId: undefined, courseId: row.courseId };
  trackData.value = null;
  trackVisible.value = true;
}

async function loadTrack() {
  const { userId, courseId } = trackQuery.value;
  if (!userId || !courseId) {
    message.warning('请输入学员用户 ID');
    return;
  }
  trackLoading.value = true;
  try {
    trackData.value = (await getCourseTrack(userId, courseId)) ?? null;
  } finally {
    trackLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
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
        { title: '完成率(%)', dataIndex: 'completedRate', key: 'completedRate', width: 100, align: 'center' },
        { title: '已获学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 100, align: 'center' },
        { title: '操作', key: 'actions', width: 120, align: 'center' },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openTrack(record)">
            查看轨迹
          </Button>
        </template>
      </template>
    </Table>
    <div class="mt-2 text-xs text-gray-400">
      统计口径：按课程汇总全部学习任务，进度与学时由服务端实时计算。
    </div>

    <Modal
      v-model:open="trackVisible"
      title="课程学习轨迹"
      :width="860"
      :footer="null"
    >
      <div class="mb-3 flex flex-wrap items-center gap-3">
        <Input
          v-model:value="trackQuery.userId"
          class="!w-48"
          placeholder="学员用户 ID"
        />
        <Button type="primary" ghost :loading="trackLoading" @click="loadTrack">
          查询
        </Button>
        <span v-if="trackData" class="text-sm text-gray-500">
          {{ trackData.nickname || `用户${trackData.userId}` }} ·
          {{ trackData.courseTitle }} · 有效
          {{ trackData.totalValidSeconds ?? 0 }} 秒 · 已获
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
        请输入学员用户 ID 后查询学习轨迹
      </div>
    </Modal>
  </div>
</template>