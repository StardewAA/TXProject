<script lang="ts" setup>
import type { CourseAuditLog, TrainingCourseApi } from '../api';

import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tabs,
  Tag,
  message,
} from 'ant-design-vue';

import {
  approveCourse,
  getAuditLogPage,
  getAuditQueue,
  getCoursePage,
  rejectCourse,
} from '../api';
import { COURSE_STATUS_OPTIONS } from '../data';

defineOptions({ name: 'TrainingCourseAudit' });

const ACTION_TEXT: Record<string, string> = {
  SUBMIT: '提交审核',
  WITHDRAW: '撤回审核',
  APPROVE: '审核通过',
  REJECT: '审核驳回',
  ONLINE: '上架',
  OFFLINE: '下架',
  PUBLISH: '发布新版本',
  UPDATE_ATTR: '修改业务属性',
};

const statusText = (status?: number) =>
  COURSE_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '-';

// ==================== 待审核队列 ====================
const queueLoading = ref(false);
const queueList = ref<TrainingCourseApi.Course[]>([]);
const queueTotal = ref(0);
const queueQuery = reactive({ pageNo: 1, pageSize: 10, title: '' });

async function loadQueue() {
  queueLoading.value = true;
  try {
    const res = await getAuditQueue({
      ...queueQuery,
      title: queueQuery.title || undefined,
    });
    queueList.value = res?.list ?? [];
    queueTotal.value = Number(res?.total ?? 0);
  } finally {
    queueLoading.value = false;
  }
}

async function handleApprove(row: TrainingCourseApi.Course) {
  await approveCourse(row.id!);
  message.success('审核通过，课程进入待上架状态');
  loadQueue();
}

const rejectVisible = ref(false);
const rejectForm = ref({ courseId: undefined as number | undefined, reason: '' });

function openReject(row: TrainingCourseApi.Course) {
  rejectForm.value = { courseId: row.id, reason: '' };
  rejectVisible.value = true;
}

async function handleReject() {
  if (!rejectForm.value.reason?.trim()) {
    message.warning('驳回必须填写驳回意见');
    return;
  }
  await rejectCourse(rejectForm.value.courseId!, rejectForm.value.reason.trim());
  message.success('已驳回，课程退回草稿并附带驳回意见');
  rejectVisible.value = false;
  loadQueue();
}

// ==================== 审核日志（永久留痕） ====================
const logLoading = ref(false);
const logList = ref<CourseAuditLog[]>([]);
const logTotal = ref(0);
const logQuery = reactive({ pageNo: 1, pageSize: 10, courseId: undefined as number | undefined });
const courseOptions = ref<{ label: string; value: number }[]>([]);

async function loadCourseOptions() {
  const res = await getCoursePage({ pageNo: 1, pageSize: 100 });
  courseOptions.value = (res?.list ?? []).map((item) => ({
    label: `${item.title ?? ''}（${item.code ?? item.id}）`,
    value: item.id!,
  }));
}

async function loadLogs() {
  if (!logQuery.courseId) {
    message.warning('请先选择课程');
    return;
  }
  logLoading.value = true;
  try {
    const res = await getAuditLogPage({ ...logQuery });
    logList.value = res?.list ?? [];
    logTotal.value = Number(res?.total ?? 0);
  } finally {
    logLoading.value = false;
  }
}

function viewLogs(row: TrainingCourseApi.Course) {
  logQuery.courseId = row.id;
  activeTab.value = 'logs';
  loadLogs();
}

const activeTab = ref('queue');

onMounted(() => {
  loadQueue();
  loadCourseOptions();
});
</script>

<template>
  <div>
    <Tabs v-model:active-key="activeTab" type="card">
      <Tabs.TabPane key="queue" tab="待审核队列">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <Input
            v-model:value="queueQuery.title"
            class="!w-56"
            allow-clear
            placeholder="课程名称"
            @press-enter="loadQueue"
          />
          <Button type="primary" ghost @click="loadQueue">查询</Button>
        </div>
        <Table
          row-key="id"
          :data-source="queueList"
          :loading="queueLoading"
          :pagination="{
            current: queueQuery.pageNo,
            pageSize: queueQuery.pageSize,
            total: queueTotal,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条`,
          }"
          :columns="[
            { title: '课程名称', dataIndex: 'title', key: 'title' },
            { title: '课程编码', dataIndex: 'code', key: 'code', width: 150 },
            { title: '讲师', dataIndex: 'lecturer', key: 'lecturer', width: 120 },
            { title: '状态', key: 'status', width: 110, align: 'center' },
            { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 180 },
            { title: '操作', key: 'actions', width: 220, align: 'center' },
          ]"
          @change="
            (pager: any) => {
              queueQuery.pageNo = pager.current;
              queueQuery.pageSize = pager.pageSize;
              loadQueue();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag color="orange">{{ statusText(record.status) }}</Tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Button type="link" size="small" @click="handleApprove(record)">
                通过
              </Button>
              <Button type="link" size="small" @click="openReject(record)">驳回</Button>
              <Button type="link" size="small" @click="viewLogs(record)">日志</Button>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <Tabs.TabPane key="logs" tab="审核日志">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <span class="text-sm text-gray-500">课程</span>
          <Select
            v-model:value="logQuery.courseId"
            class="!w-96"
            show-search
            option-filter-prop="label"
            placeholder="请选择课程"
            :options="courseOptions"
          />
          <Button type="primary" ghost :loading="logLoading" @click="loadLogs">
            查询
          </Button>
        </div>
        <Table
          row-key="id"
          :data-source="logList"
          :loading="logLoading"
          :pagination="{
            current: logQuery.pageNo,
            pageSize: logQuery.pageSize,
            total: logTotal,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条`,
          }"
          :columns="[
            { title: '动作', key: 'action', width: 130, align: 'center' },
            { title: '状态流转', key: 'flow', width: 180, align: 'center' },
            { title: '操作人', dataIndex: 'operatorName', key: 'operatorName', width: 120 },
            { title: '说明 / 驳回意见', dataIndex: 'reason', key: 'reason' },
            { title: '操作时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
          ]"
          @change="
            (pager: any) => {
              logQuery.pageNo = pager.current;
              logQuery.pageSize = pager.pageSize;
              loadLogs();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <Tag :color="record.action === 'REJECT' ? 'red' : 'blue'">
                {{ ACTION_TEXT[record.action ?? ''] || record.action }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'flow'">
              {{ statusText(record.fromStatus) }} → {{ statusText(record.toStatus) }}
            </template>
          </template>
        </Table>
        <div class="mt-2 text-xs text-gray-400">
          审核日志永久保存、不可删除，用于追溯课程上线全过程。
        </div>
      </Tabs.TabPane>
    </Tabs>

    <Modal
      v-model:open="rejectVisible"
      title="审核驳回"
      :width="480"
      ok-text="确认驳回"
      cancel-text="取消"
      @ok="handleReject"
    >
      <div class="py-2">
        <div class="mb-1 text-sm text-gray-600">
          驳回意见 <span class="text-red-500">*</span>
        </div>
        <Input.TextArea
          v-model:value="rejectForm.reason"
          :rows="4"
          :maxlength="200"
          placeholder="请填写驳回原因，将返回给课程创建人"
        />
      </div>
    </Modal>
  </div>
</template>
