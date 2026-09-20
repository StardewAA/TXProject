<script lang="ts" setup>
import type { TrainingLearningApi } from '../api';

import { computed, onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  Modal,
  Progress,
  Select,
  Table,
  Tabs,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import { getAllClasses } from '../../plan/api';
import {
  assignCourse,
  getAssignCandidates,
  getLearnerProgressList,
  getLearningClassStudents,
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
  { label: '培训班任务（入班生成）', value: 'class' },
];

const taskStatusText = (status?: number) =>
  TASK_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '-';

// ========== 公共：培训班学员（来源：培训管理-培训班在班学员） ==========
const classStudentOptions = ref<TrainingLearningApi.ClassStudentOption[]>([]);
const studentSelectOptions = computed(() =>
  classStudentOptions.value.map((item) => ({
    label: `${item.userName || `用户${item.userId}`}${
      item.deptName ? `（${item.deptName}）` : ''
    }`,
    value: item.userId!,
  })),
);

async function loadClassStudents() {
  classStudentOptions.value = (await getLearningClassStudents()) ?? [];
}

// ========== 培训班下拉 ==========
const classOptions = ref<{ label: string; value: number }[]>([]);
async function loadClassOptions() {
  const classes = await getAllClasses();
  classOptions.value = (classes ?? []).map((item) => ({
    label: item.name ?? `培训班${item.id}`,
    value: item.id!,
  }));
}

const activeTab = ref('progress');

// ========== Tab 1：学员整体学习进度（与培训管理班级学习进度打通） ==========
const progressLoading = ref(false);
const progressList = ref<TrainingLearningApi.LearnerProgress[]>([]);
const progressQuery = reactive({
  classId: undefined as number | undefined,
  keyword: '',
});

const filteredProgress = computed(() => {
  const keyword = progressQuery.keyword.trim();
  if (!keyword) {
    return progressList.value;
  }
  return progressList.value.filter(
    (item) =>
      (item.userName ?? '').includes(keyword) ||
      (item.deptName ?? '').includes(keyword),
  );
});

const progressStats = computed(() => {
  const list = progressList.value;
  const count = (fn: (item: TrainingLearningApi.LearnerProgress) => boolean) =>
    list.filter(fn).length;
  return {
    total: list.length,
    done: count((item) => item.progressPercent === 100),
    running: count(
      (item) => (item.progressPercent ?? 0) > 0 && item.progressPercent !== 100,
    ),
    notStart: count((item) => (item.progressPercent ?? 0) === 0),
  };
});

async function loadProgress() {
  progressLoading.value = true;
  try {
    progressList.value =
      (await getLearnerProgressList(progressQuery.classId)) ?? [];
  } finally {
    progressLoading.value = false;
  }
}

// ========== Tab 2：学习任务明细 ==========
const loading = ref(false);
const list = ref<TrainingLearningApi.LearnerTask[]>([]);
const total = ref(0);
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  userId: undefined as number | undefined,
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

function handleTabChange(key: string | number) {
  if (key === 'progress') {
    loadProgress();
  } else {
    load();
  }
}

// ========== 课程指派（学员改为从培训班学员中筛选） ==========
const assignVisible = ref(false);
const assignForm = ref({
  courseId: undefined as number | undefined,
  userIds: [] as number[],
  remark: '',
});
const candidates = ref<TrainingLearningApi.AssignCandidate>({});
const courseOptions = computed(() =>
  (candidates.value.courses ?? []).map((item) => ({
    label: `${item.title ?? ''}（${item.credit ?? 0} 学时）`,
    value: item.id,
  })),
);

async function openAssign() {
  if (!candidates.value.courses) {
    candidates.value = (await getAssignCandidates()) ?? {};
  }
  if (classStudentOptions.value.length === 0) {
    await loadClassStudents();
  }
  assignForm.value = { courseId: undefined, userIds: [], remark: '' };
  assignVisible.value = true;
}

async function handleAssign() {
  if (!assignForm.value.courseId) {
    message.warning('请选择要指派的课程');
    return;
  }
  if (assignForm.value.userIds.length === 0) {
    message.warning('请选择要指派的学员');
    return;
  }
  const added = await assignCourse({
    courseId: assignForm.value.courseId,
    userIds: assignForm.value.userIds,
    remark: assignForm.value.remark || undefined,
  });
  message.success(`指派完成，本次新增 ${added ?? 0} 条学习任务`);
  assignVisible.value = false;
  load();
  loadProgress();
}

onMounted(async () => {
  await Promise.all([loadClassStudents(), loadClassOptions()]);
  loadProgress();
});
</script>

<template>
  <div>
    <Tabs v-model:active-key="activeTab" type="card" @change="handleTabChange">
      <!-- 学员整体学习进度：数据来源与培训管理「班级学习进度」一致 -->
      <Tabs.TabPane key="progress" tab="学员学习进度">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <Select
            v-model:value="progressQuery.classId"
            class="!w-64"
            allow-clear
            show-search
            option-filter-prop="label"
            placeholder="按培训班筛选（默认全部）"
            :options="classOptions"
            @change="loadProgress"
          />
          <Input
            v-model:value="progressQuery.keyword"
            class="!w-56"
            allow-clear
            placeholder="学员姓名 / 部门"
          />
          <Button type="primary" ghost :loading="progressLoading" @click="loadProgress">
            刷新
          </Button>
          <span class="text-sm text-gray-500">
            共 {{ progressStats.total }} 名学员 · 未开始 {{ progressStats.notStart }} ·
            进行中 {{ progressStats.running }} · 已完成 {{ progressStats.done }}
          </span>
          <Button type="primary" class="ml-auto" @click="openAssign">
            课程指派
          </Button>
        </div>

        <Table
          row-key="userId"
          :data-source="filteredProgress"
          :loading="progressLoading"
          :pagination="{ pageSize: 20, showSizeChanger: true }"
          :columns="[
            { title: '学员', dataIndex: 'userName', key: 'userName', width: 130 },
            { title: '部门', dataIndex: 'deptName', key: 'deptName', width: 150 },
            { title: '所在培训班', dataIndex: 'className', key: 'className', minWidth: 200 },
            { title: '课程任务数', dataIndex: 'totalTasks', key: 'totalTasks', width: 100, align: 'center' },
            { title: '待学习', dataIndex: 'todoCount', key: 'todoCount', width: 90, align: 'center' },
            { title: '学习中', dataIndex: 'learningCount', key: 'learningCount', width: 90, align: 'center' },
            { title: '已完成', dataIndex: 'completedCount', key: 'completedCount', width: 90, align: 'center' },
            { title: '整体学习进度', key: 'progress', width: 190 },
            { title: '学时（已获/应获）', key: 'credit', width: 140, align: 'center' },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'userName'">
              {{ record.userName || `用户${record.userId}` }}
            </template>
            <template v-else-if="column.key === 'progress'">
              <Progress :percent="Number(record.progressPercent ?? 0)" size="small" />
            </template>
            <template v-else-if="column.key === 'credit'">
              {{ record.creditEarned ?? 0 }} / {{ record.creditTotal ?? 0 }}
            </template>
          </template>
        </Table>
        <div class="mt-2 text-xs text-gray-400">
          统计口径：培训班的班级学习任务（课程任务数）与学习中心的学习任务合并去重后按学员聚合，
          与「培训管理 → 班级学习进度」保持一致。
        </div>
      </Tabs.TabPane>

      <!-- 学习任务明细 -->
      <Tabs.TabPane key="tasks" tab="学习任务明细">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <Select
            v-model:value="query.userId"
            class="!w-64"
            allow-clear
            show-search
            option-filter-prop="label"
            placeholder="筛选学员（培训班已导入学员）"
            :options="studentSelectOptions"
            @change="load"
          />
          <Input
            v-model:value="query.courseTitle"
            class="!w-52"
            allow-clear
            placeholder="课程名称"
            @press-enter="load"
          />
          <Select
            v-model:value="query.status"
            class="!w-32"
            allow-clear
            placeholder="学习状态"
            :options="TASK_STATUS_OPTIONS"
            @change="load"
          />
          <Select
            v-model:value="query.sourceType"
            class="!w-36"
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
            { title: '学员', dataIndex: 'userName', key: 'userName', width: 120 },
            { title: '部门', dataIndex: 'deptName', key: 'deptName', width: 140 },
            { title: '课程', dataIndex: 'courseTitle', key: 'courseTitle' },
            { title: '来源', key: 'sourceType', width: 110, align: 'center' },
            { title: '状态', key: 'status', width: 100, align: 'center' },
            { title: '学习进度', key: 'progress', width: 170 },
            { title: '已获学时', key: 'credit', width: 110, align: 'center' },
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
            <template v-if="column.key === 'userName'">
              {{ record.userName || `用户${record.userId}` }}
            </template>
            <template v-else-if="column.key === 'sourceType'">
              <Tag
                :color="
                  record.sourceType === 'self'
                    ? 'purple'
                    : record.sourceType === 'class'
                      ? 'cyan'
                      : 'blue'
                "
              >
                {{
                  record.sourceType === 'self'
                    ? '自主选学'
                    : record.sourceType === 'class'
                      ? '培训班'
                      : '指派'
                }}
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
        <div class="mt-2 text-xs text-gray-400">
          学习任务明细为管理员指派 / 学员自主选学产生的记录；培训班导入的「学员 × 课程」任务
          请在上方「学员学习进度」中按学员查看课程任务数与整体进度。
        </div>
      </Tabs.TabPane>
    </Tabs>

    <Modal
      v-model:open="assignVisible"
      title="课程指派"
      :width="620"
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
            筛选学员（培训班已导入学员，可多选）
            <span class="text-red-500">*</span>
          </div>
          <Select
            v-model:value="assignForm.userIds"
            class="w-full"
            mode="multiple"
            :max-tag-count="6"
            show-search
            option-filter-prop="label"
            placeholder="输入姓名 / 部门搜索后选择学员"
            :options="studentSelectOptions"
          />
          <div class="mt-1 text-xs text-gray-400">
            学员名单来自「培训管理 → 培训班」已导入的在班学员，共
            {{ studentSelectOptions.length }} 人。
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
