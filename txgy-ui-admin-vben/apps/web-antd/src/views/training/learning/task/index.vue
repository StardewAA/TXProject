<script lang="ts" setup>
import type { TrainingLearningApi } from '../api';

import { computed, onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  assignCourse,
  getAssignCandidates,
  getLearningTaskPage,
} from '../api';

defineOptions({ name: 'TrainingLearningTask' });

const TASK_STATUS_OPTIONS = [
  { label: '待学习', value: 0 },
  { label: '学习中', value: 1 },
  { label: '已完成', value: 2 },
];
const SOURCE_TYPE_OPTIONS = [
  { label: '管理员指派', value: 'assign' },
  { label: '自主选学', value: 'self' },
];

const taskStatusText = (status?: number) =>
  TASK_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '-';

const loading = ref(false);
const list = ref<TrainingLearningApi.LearnerTask[]>([]);
const total = ref(0);
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  courseTitle: '',
  status: undefined as number | undefined,
  sourceType: undefined as string | undefined,
});

async function load() {
  loading.value = true;
  try {
    const res = await getLearningTaskPage({
      ...query,
      courseTitle: query.courseTitle || undefined,
    });
    list.value = res?.list ?? [];
    total.value = Number(res?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

// 指派弹窗
const assignVisible = ref(false);
const assignForm = ref({
  courseId: undefined as number | undefined,
  userIdsText: '',
  remark: '',
});
const candidates = ref<TrainingLearningApi.AssignCandidate>({});
const courseOptions = computed(() =>
  (candidates.value.courses ?? []).map((item) => ({
    label: `${item.title ?? ''}（${item.credit ?? 0} 学时）`,
    value: item.id,
  })),
);
const historyUsers = computed(() => candidates.value.users ?? []);

async function openAssign() {
  if (!candidates.value.courses) {
    candidates.value = (await getAssignCandidates()) ?? {};
  }
  assignForm.value = { courseId: undefined, userIdsText: '', remark: '' };
  assignVisible.value = true;
}

function appendUser(id?: number) {
  if (!id) return;
  const current = assignForm.value.userIdsText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  if (!current.includes(String(id))) {
    current.push(String(id));
  }
  assignForm.value.userIdsText = current.join(',');
}

async function handleAssign() {
  const userIds = assignForm.value.userIdsText
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map(Number)
    .filter((item) => !Number.isNaN(item));
  if (!assignForm.value.courseId) {
    message.warning('请选择要指派的课程');
    return;
  }
  if (userIds.length === 0) {
    message.warning('请填写至少一个学员用户 ID');
    return;
  }
  const added = await assignCourse({
    courseId: assignForm.value.courseId,
    userIds,
    remark: assignForm.value.remark || undefined,
  });
  message.success(`指派完成，本次新增 ${added ?? 0} 条学习任务`);
  assignVisible.value = false;
  load();
}

onMounted(load);
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Input
        v-model:value="query.courseTitle"
        class="!w-56"
        allow-clear
        placeholder="课程名称"
        @press-enter="load"
      />
      <Select
        v-model:value="query.status"
        class="!w-36"
        allow-clear
        placeholder="学习状态"
        :options="TASK_STATUS_OPTIONS"
        @change="load"
      />
      <Select
        v-model:value="query.sourceType"
        class="!w-40"
        allow-clear
        placeholder="任务来源"
        :options="SOURCE_TYPE_OPTIONS"
        @change="load"
      />
      <Button type="primary" ghost @click="load">查询</Button>
      <Button type="primary" class="ml-auto" @click="openAssign">
        课程指派
      </Button>
    </div>

    <Table
      row-key="id"
      :data-source="list"
      :loading="loading"
      :pagination="{
        current: query.pageNo,
        pageSize: query.pageSize,
        total: total,
        showSizeChanger: true,
        showTotal: (total: number) => `共 ${total} 条`,
      }"
      :columns="[
        { title: '学员 ID', dataIndex: 'userId', key: 'userId', width: 100 },
        { title: '课程', dataIndex: 'courseTitle', key: 'courseTitle' },
        { title: '来源', key: 'sourceType', width: 120, align: 'center' },
        { title: '状态', key: 'status', width: 100, align: 'center' },
        { title: '学习进度', key: 'progress', width: 180 },
        { title: '已获学时', key: 'credit', width: 120, align: 'center' },
        { title: '完成时间', dataIndex: 'completedAt', key: 'completedAt', width: 180 },
      ]"
      @change="
        (pager: any) => {
          query.pageNo = pager.current;
          query.pageSize = pager.pageSize;
          load();
        }
      "
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'sourceType'">
          <Tag :color="record.sourceType === 'self' ? 'purple' : 'blue'">
            {{ record.sourceType === 'self' ? '自主选学' : '指派' }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <Tag
            :color="
              record.status === 2
                ? 'green'
                : record.status === 1
                  ? 'orange'
                  : 'default'
            "
          >
            {{ taskStatusText(record.status) }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'progress'">
          <Progress :percent="Number(record.progressPercent ?? 0)" size="small" />
        </template>
        <template v-else-if="column.key === 'credit'">
          {{ record.creditEarned ?? 0 }} / {{ record.creditTotal ?? 0 }}
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="assignVisible"
      title="课程指派"
      :width="560"
      ok-text="确定指派"
      cancel-text="取消"
      @ok="handleAssign"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">
            指派课程 <span class="text-red-500">*</span>
          </div>
          <Select
            v-model:value="assignForm.courseId"
            class="w-full"
            placeholder="请选择已上架课程"
            :options="courseOptions"
            show-search
            option-filter-prop="label"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            学员用户 ID（多个用逗号分隔）<span class="text-red-500">*</span>
          </div>
          <Input
            v-model:value="assignForm.userIdsText"
            placeholder="如：3015,3016"
          />
          <div v-if="historyUsers.length" class="mt-2 flex flex-wrap gap-2">
            <span class="text-xs text-gray-400">历史学员：</span>
            <Button
              v-for="user in historyUsers"
              :key="user.id"
              size="small"
              type="dashed"
              @click="appendUser(user.id)"
            >
              {{ user.nickname || `用户${user.id}` }}
            </Button>
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">备注</div>
          <Input
            v-model:value="assignForm.remark"
            :maxlength="100"
            placeholder="可填写培训批次等说明"
          />
        </div>
        <div class="text-xs text-gray-400">
          已存在的学习任务会自动跳过，仅统计本次新增数量。
        </div>
      </div>
    </Modal>
  </div>
</template>