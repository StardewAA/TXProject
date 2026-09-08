<script lang="ts" setup>
import type { TrainingPlanApi } from './api';

import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  message,
  Modal,
  Progress,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  getProgressPage,
  getStudentTaskList,
  registerProgress,
} from './api';
import { OVERALL_STATUS_TEXT } from './data';
import ClassBar from './class-bar.vue';

defineOptions({ name: 'TrainingPlanProgress' });

/** 当前班级 */
const currentClass = ref<TrainingPlanApi.Class>();
/** 是否可登记进度：进行中 / 已完成（草稿不可） */
const canRegister = computed(
  () =>
    currentClass.value != null &&
    (currentClass.value.status === 1 || currentClass.value.status === 2),
);

// ========== 数据加载（班级规模有限，整页拉取后内存聚合/筛选） ==========
const loading = ref(false);
const allRows = ref<TrainingPlanApi.ClassProgress[]>([]);
const filter = ref<number | undefined>();

const filteredRows = computed(() =>
  filter.value === undefined
    ? allRows.value
    : allRows.value.filter((item) => item.overallStatus === filter.value),
);

const stats = computed(() => {
  const list = allRows.value;
  const count = (fn: (item: TrainingPlanApi.ClassProgress) => boolean) =>
    list.filter(fn).length;
  return {
    total: list.length,
    notStart: count((item) => item.overallStatus === 0),
    running: count((item) => item.overallStatus === 1),
    done: count((item) => item.overallStatus === 2),
    examFail: count((item) => item.overallStatus === 3),
  };
});

async function loadProgress() {
  if (!currentClass.value?.id) {
    allRows.value = [];
    return;
  }
  loading.value = true;
  try {
    const data = await getProgressPage({
      pageNo: 1,
      pageSize: 500,
      classId: currentClass.value.id,
    });
    allRows.value = data?.list ?? [];
  } finally {
    loading.value = false;
  }
}

function handleClassChange(row?: TrainingPlanApi.Class) {
  currentClass.value = row;
  filter.value = undefined;
  loadProgress();
}

// ========== 进度登记（教务代录） ==========
const registerVisible = ref(false);
const registerRows = ref<
  {
    taskId: number;
    courseTitle?: string;
    studyFinished: boolean;
    courseStatus: number;
    examStatus: number;
    certStatus: boolean;
  }[]
>([]);
const registerUser = ref<TrainingPlanApi.ClassProgress>();

async function handleRegister(row: TrainingPlanApi.ClassProgress) {
  if (!currentClass.value?.id || row.userId == null) {
    return;
  }
  const tasks = await getStudentTaskList(currentClass.value.id, row.userId);
  registerRows.value = (tasks ?? []).map((task) => ({
    taskId: task.id!,
    courseTitle: task.courseTitle,
    studyFinished: task.studyStatus === 1,
    courseStatus: task.courseStatus ?? 0,
    examStatus: task.examStatus ?? 0,
    certStatus: task.certStatus === 1,
  }));
  registerUser.value = row;
  registerVisible.value = true;
}

async function handleSaveRegisterRow(item: Record<string, any>) {
  await registerProgress({
    taskId: item.taskId,
    studyFinished: item.studyFinished,
    courseStatus: item.courseStatus,
    examStatus: item.examStatus,
    certStatus: item.certStatus,
  });
  message.success('登记成功');
  await loadProgress();
}

// ========== 表格列 ==========
const columns = [
  { title: '学员', dataIndex: 'userName', key: 'userName', width: 120 },
  { title: '学习进度', key: 'progress', width: 170 },
  { title: '学时', key: 'study', width: 90 },
  { title: '课程', key: 'course', width: 90 },
  { title: '考试', key: 'exam', width: 90 },
  { title: '证书', key: 'cert', width: 90 },
  { title: '进度维度', key: 'overall', width: 110 },
  { title: '操作', key: 'action', width: 110 },
];

const registerColumns = [
  { title: '课程', dataIndex: 'courseTitle', key: 'courseTitle' },
  { title: '学时完成', key: 'study', width: 110 },
  { title: '课程状态', key: 'courseStatus', width: 150 },
  { title: '考试结果', key: 'examStatus', width: 150 },
  { title: '已取证', key: 'cert', width: 100 },
  { title: '操作', key: 'action', width: 100 },
];
</script>

<template>
  <Page auto-content-height>
    <ClassBar @change="handleClassChange" />

    <template v-if="currentClass">
      <!-- 班级进度总览 -->
      <div class="mb-3 grid grid-cols-5 gap-3">
        <Card size="small">
          <Statistic title="在班学员" :value="stats.total" />
        </Card>
        <Card size="small">
          <Statistic title="未开始" :value="stats.notStart" />
        </Card>
        <Card size="small">
          <Statistic title="进行中" :value="stats.running" />
        </Card>
        <Card size="small">
          <Statistic title="已完成" :value="stats.done" />
        </Card>
        <Card size="small">
          <Statistic
            title="考试未通过"
            :value="stats.examFail"
            :value-style="{ color: stats.examFail > 0 ? '#cf1322' : undefined }"
          />
        </Card>
      </div>

      <!-- 筛选 + 刷新 -->
      <Space class="mb-3">
        <Select
          v-model:value="filter"
          allow-clear
          placeholder="按进度维度筛选"
          style="min-width: 180px"
          :options="[
            { label: '未开始', value: 0 },
            { label: '进行中', value: 1 },
            { label: '已完成', value: 2 },
            { label: '考试未通过', value: 3 },
          ]"
        />
        <Button :loading="loading" @click="loadProgress">刷新</Button>
      </Space>

      <Table
        row-key="userId"
        size="small"
        :columns="columns"
        :data-source="filteredRows"
        :loading="loading"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'progress'">
            <Progress :percent="record.progressPercent ?? 0" size="small" />
            <span class="text-xs text-gray-500">
              {{ record.finishedTasks }}/{{ record.totalTasks }}
            </span>
          </template>
          <template v-if="column.key === 'study'">
            <Tag v-if="record.studyFinished" color="success">完成</Tag>
            <Tag v-else>未完成</Tag>
          </template>
          <template v-if="column.key === 'course'">
            <Tag v-if="record.courseFinished" color="success">完成</Tag>
            <Tag v-else>未完成</Tag>
          </template>
          <template v-if="column.key === 'exam'">
            <Tag v-if="record.examStatus === 1" color="success">通过</Tag>
            <Tag v-else-if="record.examStatus === 2" color="error">未通过</Tag>
            <Tag v-else>未考试</Tag>
          </template>
          <template v-if="column.key === 'cert'">
            <Tag v-if="record.certFinished" color="processing">已取</Tag>
            <Tag v-else>未取</Tag>
          </template>
          <template v-if="column.key === 'overall'">
            <Tag
              :color="
                record.overallStatus === 3
                  ? 'error'
                  : record.overallStatus === 2
                    ? 'success'
                    : record.overallStatus === 1
                      ? 'processing'
                      : 'default'
              "
            >
              {{
                record.overallStatus != null
                  ? OVERALL_STATUS_TEXT[record.overallStatus]
                  : '-'
              }}
            </Tag>
          </template>
          <template v-if="column.key === 'action'">
            <Button
              v-if="canRegister"
              type="link"
              size="small"
              @click="handleRegister(record)"
            >
              登记进度
            </Button>
            <span v-else class="text-gray-400">-</span>
          </template>
        </template>
      </Table>
    </template>
    <div
      v-else
      class="mt-10 text-center text-sm text-gray-400"
    >
      请先在上方选择培训班（暂无班级请先到培训班管理创建并开班）
    </div>

    <!-- 进度登记弹窗 -->
    <Modal
      v-model:open="registerVisible"
      :title="`学习进度登记：${registerUser?.userName ?? ''}`"
      :footer="null"
      width="900px"
    >
      <Table
        row-key="taskId"
        size="small"
        :columns="registerColumns"
        :data-source="registerRows"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'study'">
            <Switch v-model:checked="record.studyFinished" size="small" />
          </template>
          <template v-if="column.key === 'courseStatus'">
            <Select
              v-model:value="record.courseStatus"
              size="small"
              :options="[
                { label: '未开始', value: 0 },
                { label: '进行中', value: 1 },
                { label: '已完成', value: 2 },
              ]"
            />
          </template>
          <template v-if="column.key === 'examStatus'">
            <Select
              v-model:value="record.examStatus"
              size="small"
              :options="[
                { label: '未参加', value: 0 },
                { label: '已通过', value: 1 },
                { label: '未通过', value: 2 },
              ]"
            />
          </template>
          <template v-if="column.key === 'cert'">
            <Switch v-model:checked="record.certStatus" size="small" />
          </template>
          <template v-if="column.key === 'action'">
            <Button
              type="link"
              size="small"
              @click="handleSaveRegisterRow(record)"
            >
              {{ $t('ui.actionTitle.save') }}
            </Button>
          </template>
        </template>
      </Table>
    </Modal>
  </Page>
</template>
