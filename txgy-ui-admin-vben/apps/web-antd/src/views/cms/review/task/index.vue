<script lang="ts" setup>
import type { CmsReviewTask } from '#/api/cms/content-ext';

import {
  Button,
  Card,
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';
import { onMounted, reactive, ref } from 'vue';

import {
  assignReviewTask,
  completeReviewTask,
  getReviewTaskPage,
  scanOverdueTask,
} from '#/api/cms/content-ext';

defineOptions({ name: 'CmsReviewTask' });

const loading = ref(false);
const saving = ref(false);
const modalVisible = ref(false);
const list = ref<CmsReviewTask[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const statusFilter = ref<string | undefined>(undefined);

const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已完成', value: 'DONE' },
  { label: '已超时', value: 'OVERDUE' },
];

const statusColor: Record<string, string> = {
  DONE: 'green',
  OVERDUE: 'red',
  PENDING: 'default',
  PROCESSING: 'blue',
};

const statusName: Record<string, string> = {
  DONE: '已完成',
  OVERDUE: '已超时',
  PENDING: '待处理',
  PROCESSING: '处理中',
};

const columns = [
  { dataIndex: 'reviewId', key: 'reviewId', title: '审查单号', width: 280, ellipsis: true },
  { dataIndex: 'title', key: 'title', title: '内容标题', ellipsis: true },
  { dataIndex: 'assigneeName', key: 'assigneeName', title: '审查人', width: 120 },
  { key: 'status', title: '状态', width: 140 },
  { key: 'priority', title: '优先级', width: 90 },
  { dataIndex: 'dueTime', key: 'dueTime', title: '要求完成时间', width: 180 },
  { key: 'action', title: '操作', width: 160 },
];

const form = reactive<{
  assigneeId?: number;
  dueTime?: string;
  priority: number;
  remark?: string;
  reviewId?: string;
  title?: string;
}>({ priority: 1 });

async function load() {
  loading.value = true;
  try {
    const result = await getReviewTaskPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
    });
    list.value = result.list ?? [];
    total.value = Number(result.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function openAssign(row?: CmsReviewTask) {
  Object.assign(form, { assigneeId: undefined, dueTime: undefined, priority: 1, remark: undefined, reviewId: undefined, title: undefined });
  if (row) {
    Object.assign(form, {
      assigneeId: row.assigneeId,
      dueTime: row.dueTime,
      priority: row.priority,
      remark: row.remark,
      reviewId: row.reviewId,
      title: row.title,
    });
  }
  modalVisible.value = true;
}

async function submit() {
  if (!form.reviewId) {
    message.warning('请填写审查单号（reviewId）');
    return;
  }
  saving.value = true;
  try {
    await assignReviewTask({ ...form });
    message.success('审查任务已分配');
    modalVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function complete(row: CmsReviewTask) {
  await completeReviewTask(row.id);
  message.success('任务已标记完成');
  await load();
}

async function scan() {
  const count = await scanOverdueTask();
  message.success(`扫描完成，标记超时 ${count} 条`);
  await load();
}

onMounted(load);
</script>

<template>
  <Card :bordered="false">
    <template #title>审查任务分配</template>
    <template #extra>
      <Space>
        <Select
          v-model:value="statusFilter"
          :options="statusOptions"
          allow-clear
          placeholder="任务状态"
          style="width: 140px"
          @change="pageNo = 1; load()"
        />
        <Button @click="scan">超时扫描</Button>
        <Button type="primary" @click="openAssign()">分配审查任务</Button>
      </Space>
    </template>
    <Table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :pagination="{ current: pageNo, pageSize, total, onChange: (p: number) => { pageNo = p; load(); } }"
      :row-key="(row: CmsReviewTask) => row.id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <Tag :color="statusColor[record.status] || 'default'">{{ statusName[record.status] || record.status }}</Tag>
          <Tag v-if="record.overdue && record.status !== 'OVERDUE'" color="orange">已逾期</Tag>
        </template>
        <template v-else-if="column.key === 'priority'">
          <Tag :color="record.priority === 2 ? 'volcano' : 'default'">
            {{ record.priority === 2 ? '紧急' : '普通' }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <Space>
            <a @click="openAssign(record)">转交/改期</a>
            <a v-if="record.status !== 'DONE'" @click="complete(record)">标记完成</a>
          </Space>
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="modalVisible"
      :confirm-loading="saving"
      title="分配 / 转交审查任务"
      @ok="submit"
    >
      <Form layout="vertical">
        <FormItem label="审查单号 reviewId" required>
          <Input v-model:value="form.reviewId" placeholder="cms_document_review.review_id" />
        </FormItem>
        <FormItem label="内容标题">
          <Input v-model:value="form.title" />
        </FormItem>
        <FormItem label="审查人用户 ID">
          <InputNumber v-model:value="form.assigneeId" :min="1" style="width: 100%" />
        </FormItem>
        <FormItem label="优先级">
          <Select
            v-model:value="form.priority"
            :options="[
              { label: '普通', value: 1 },
              { label: '紧急', value: 2 },
            ]"
          />
        </FormItem>
        <FormItem label="要求完成时间">
          <DatePicker v-model:value="form.dueTime" show-time style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </FormItem>
        <FormItem label="备注">
          <Input v-model:value="form.remark" />
        </FormItem>
      </Form>
    </Modal>
  </Card>
</template>