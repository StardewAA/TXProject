<script lang="ts" setup>
import type { TrainingPlanApi } from './api';

import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  message,
  Popconfirm,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import { getAllProgress, getRemindRecordPage, sendRemind } from './api';
import ClassBar from './class-bar.vue';

defineOptions({ name: 'TrainingPlanRemind' });

/** 当前班级 */
const currentClass = ref<TrainingPlanApi.Class>();
/** 可发送督促：草稿 / 进行中 */
const canRemind = computed(
  () =>
    currentClass.value != null &&
    (currentClass.value.status === 0 || currentClass.value.status === 1),
);

// ========== 待督促学员（未完成学习任务的在班学员） ==========
const unfinishedCount = ref(0);
const unfinishedLoading = ref(false);

async function loadUnfinished() {
  if (!currentClass.value?.id) {
    unfinishedCount.value = 0;
    return;
  }
  unfinishedLoading.value = true;
  try {
    const rows = await getAllProgress(currentClass.value.id);
    unfinishedCount.value = rows.filter(
      (item) => item.overallStatus !== 2,
    ).length;
  } finally {
    unfinishedLoading.value = false;
  }
}

// ========== 督促记录 ==========
const records = ref<TrainingPlanApi.RemindRecord[]>([]);
const recordTotal = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const loading = ref(false);
const sending = ref(false);

async function loadRecords() {
  if (!currentClass.value?.id) {
    records.value = [];
    recordTotal.value = 0;
    return;
  }
  loading.value = true;
  try {
    const data = await getRemindRecordPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      classId: currentClass.value.id,
    });
    records.value = data?.list ?? [];
    recordTotal.value = data?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function handleClassChange(row?: TrainingPlanApi.Class) {
  currentClass.value = row;
  pageNo.value = 1;
  if (row?.id) {
    loadRecords();
    loadUnfinished();
  } else {
    records.value = [];
    recordTotal.value = 0;
    unfinishedCount.value = 0;
  }
}

/** 一键督促：对未完成学员批量发送站内信（内容含班级名 + 任务提示） */
async function handleSendRemind() {
  if (!currentClass.value?.id) {
    return;
  }
  sending.value = true;
  try {
    const count = await sendRemind(currentClass.value.id);
    message.success(`已向 ${count} 名未完成学员发送督促站内信`);
    await Promise.all([loadRecords(), loadUnfinished()]);
  } finally {
    sending.value = false;
  }
}

function handleTableChange(pagination: any) {
  pageNo.value = pagination.current;
  pageSize.value = pagination.pageSize;
  loadRecords();
}

const columns = [
  { title: '学员', dataIndex: 'userName', key: 'userName', width: 130 },
  {
    title: '发送时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
  },
  { title: '发送结果', key: 'status', width: 110 },
  { title: '督促内容', dataIndex: 'content', key: 'content' },
];
</script>

<template>
  <Page auto-content-height>
    <ClassBar @change="handleClassChange" />

    <template v-if="currentClass">
      <Alert
        class="mb-3"
        type="info"
        show-icon
        :message="`待督促学员：${unfinishedCount} 人（未完成学习任务）`"
        :description="`系统将批量生成站内信提醒，内容包含【${currentClass.name}】班级名称与待完成任务提示，并保留督促记录。`"
      />
      <Space class="mb-3">
        <Popconfirm
          v-if="canRemind"
          title="确认对全部未完成学员批量发送督促站内信？"
          @confirm="handleSendRemind"
        >
          <Button type="primary" :loading="sending">
            一键督促（未完成学员）
          </Button>
        </Popconfirm>
        <Button :loading="loading" @click="loadRecords">刷新记录</Button>
      </Space>

      <Table
        row-key="id"
        size="small"
        :columns="columns"
        :data-source="records"
        :pagination="{
          current: pageNo,
          pageSize,
          total: recordTotal,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条督促记录`,
        }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <Tag v-if="record.status === 0" color="success">发送成功</Tag>
            <Tag v-else color="error">发送失败</Tag>
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
  </Page>
</template>
