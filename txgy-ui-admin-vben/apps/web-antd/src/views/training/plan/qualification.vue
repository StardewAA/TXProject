<script lang="ts" setup>
import type { TrainingPlanApi } from './api';

import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Alert, Button, Input, message, Space, Table, Tag } from 'ant-design-vue';

import {
  checkQualification,
  deleteQualificationRecord,
  deleteQualificationRecordBatch,
  getQualificationRecordPage,
} from './api';
import { CLASS_TYPE_TEXT } from './data';

defineOptions({ name: 'TrainingPlanQualification' });

// ========== 核查（姓名 + 手机号） ==========
const qualName = ref('');
const qualMobile = ref('');
const qualLoading = ref(false);
const qualResult = ref<TrainingPlanApi.QualificationResult>();

async function handleQualificationCheck() {
  if (!qualName.value.trim()) {
    message.warning('请输入人员姓名');
    return;
  }
  if (!qualMobile.value.trim()) {
    message.warning('请输入手机号');
    return;
  }
  qualLoading.value = true;
  try {
    qualResult.value = await checkQualification({
      userName: qualName.value.trim(),
      mobile: qualMobile.value.trim(),
    });
    await loadRecords();
  } finally {
    qualLoading.value = false;
  }
}

/** 已参加的培训班明细表 */
const columns = [
  { title: '培训类型', key: 'typeName', width: 130 },
  { title: '培训班', dataIndex: 'className', key: 'className', minWidth: 200 },
  { title: '任务完成', key: 'tasks', width: 110 },
  { title: '截止时间', dataIndex: 'deadline', width: 180 },
  { title: '结果', key: 'qualified', width: 100 },
];

// ========== 历史记录（支持删除 / 批量删除） ==========
const records = ref<TrainingPlanApi.QualificationRecord[]>([]);
const recordTotal = ref(0);
const recordLoading = ref(false);
const recordPage = ref(1);
const recordPageSize = 10;
const selectedRecordIds = ref<number[]>([]);

const recordPagination = computed(() => ({
  current: recordPage.value,
  pageSize: recordPageSize,
  total: recordTotal.value,
}));

async function loadRecords() {
  recordLoading.value = true;
  try {
    const res = await getQualificationRecordPage({
      pageNo: recordPage.value,
      pageSize: recordPageSize,
    });
    records.value = res.list || [];
    recordTotal.value = Number(res.total || 0);
  } finally {
    recordLoading.value = false;
  }
}

function handleRecordTableChange(p: { current?: number }) {
  recordPage.value = p.current || 1;
  loadRecords();
}

async function handleDeleteRecord(id?: number) {
  if (!id) return;
  await deleteQualificationRecord(id);
  message.success('已删除该条核查记录');
  if (records.value.length === 1 && recordPage.value > 1) {
    recordPage.value -= 1;
  }
  await loadRecords();
}

async function handleBatchDelete() {
  if (!selectedRecordIds.value.length) {
    message.warning('请先勾选要删除的记录');
    return;
  }
  const count = selectedRecordIds.value.length;
  await deleteQualificationRecordBatch(selectedRecordIds.value);
  message.success(`已删除 ${count} 条核查记录`);
  selectedRecordIds.value = [];
  if (records.value.length <= count && recordPage.value > 1) {
    recordPage.value -= 1;
  }
  await loadRecords();
}

function parsePosts(json?: string): string[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    return Array.isArray(arr) ? arr.filter((x) => typeof x === 'string') : [];
  } catch {
    return [];
  }
}

const recordColumns = [
  { title: '核查时间', dataIndex: 'createTime', width: 170 },
  { title: '姓名', dataIndex: 'userName', width: 110 },
  { title: '手机号', dataIndex: 'mobile', width: 130 },
  { title: '通过岗位', key: 'passedPosts', minWidth: 180 },
  { title: '结论', key: 'qualified', width: 100 },
  { title: '说明', dataIndex: 'reason', minWidth: 220 },
  { title: '操作', key: 'actions', width: 80 },
];

loadRecords();
</script>

<template>
  <Page auto-content-height>
    <Alert
      class="mb-3"
      type="info"
      show-icon
      message="上岗资格核查（3-5）"
      description="输入人员姓名与手机号，系统自动核对其岗前/复工等培训班的完成情况，输出是否具备上岗资格、通过岗位以及参加的培训班明细。"
    />

    <Space class="mb-3" wrap>
      <Input
        v-model:value="qualName"
        allow-clear
        placeholder="人员姓名"
        style="width: 200px"
        @press-enter="handleQualificationCheck"
      />
      <Input
        v-model:value="qualMobile"
        allow-clear
        placeholder="手机号"
        style="width: 200px"
        @press-enter="handleQualificationCheck"
      />
      <Button
        type="primary"
        :loading="qualLoading"
        @click="handleQualificationCheck"
      >
        核查上岗资格
      </Button>
    </Space>

    <template v-if="qualResult">
      <Alert
        class="mb-3"
        :type="qualResult.qualified ? 'success' : 'error'"
        show-icon
        :message="
          qualResult.qualified
            ? '核查通过：该人员具备上岗资格'
            : '核查未通过：暂不具备上岗资格'
        "
        :description="qualResult.reason"
      />
      <div v-if="qualResult.passedPosts?.length" class="mb-3">
        <span class="mr-2 text-sm text-gray-500">通过岗位：</span>
        <Tag v-for="post in qualResult.passedPosts" :key="post" color="success">
          {{ post }}
        </Tag>
      </div>
      <Table
        row-key="classId"
        size="small"
        :columns="columns"
        :data-source="qualResult.items"
        :pagination="false"
        class="mb-3"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'typeName'">
            {{
              record.type != null
                ? CLASS_TYPE_TEXT[record.type] || '-'
                : '-'
            }}
          </template>
          <template v-if="column.key === 'tasks'">
            {{ record.finishedTasks }}/{{ record.totalTasks }}
          </template>
          <template v-if="column.key === 'qualified'">
            <Tag v-if="record.qualified" color="success">合格</Tag>
            <Tag v-else color="error">未完成</Tag>
          </template>
        </template>
      </Table>
    </template>

    <!-- 历史记录 -->
    <div class="mb-2 mt-2 flex items-center justify-between">
      <span class="text-sm font-medium">上岗资格核查历史记录</span>
      <Button
        danger
        :disabled="!selectedRecordIds.length"
        @click="handleBatchDelete"
      >
        批量删除
      </Button>
    </div>
    <Table
      row-key="id"
      size="small"
      :columns="recordColumns"
      :data-source="records"
      :loading="recordLoading"
      :pagination="recordPagination"
      :row-selection="{
        selectedRowKeys: selectedRecordIds,
        onChange: (keys: any) => (selectedRecordIds = keys as number[]),
      }"
      @change="handleRecordTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'passedPosts'">
          <template v-if="parsePosts(record.passedPosts).length">
            <Tag
              v-for="post in parsePosts(record.passedPosts)"
              :key="post"
              color="success"
            >
              {{ post }}
            </Tag>
          </template>
          <span v-else>-</span>
        </template>
        <template v-if="column.key === 'qualified'">
          <Tag v-if="record.qualified" color="success">具备资格</Tag>
          <Tag v-else color="error">不具备</Tag>
        </template>
        <template v-if="column.key === 'actions'">
          <Button
            type="link"
            danger
            size="small"
            @click="handleDeleteRecord(record.id)"
          >
            删除
          </Button>
        </template>
      </template>
    </Table>
  </Page>
</template>
