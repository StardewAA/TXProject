<script lang="ts" setup>
import type { TrainingPlanApi } from './api';

import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Input,
  message,
  Select,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import { checkQualification, getClassStudentPage } from './api';
import { CLASS_TYPE_TEXT } from './data';
import ClassBar from './class-bar.vue';

defineOptions({ name: 'TrainingPlanQualification' });

/** 当前班级 */
const currentClass = ref<TrainingPlanApi.Class>();
/** 可发起核查的班级（须已开班/进行中/完成，有真实在班学员） */
const canCheck = computed(
  () =>
    currentClass.value != null &&
    currentClass.value.status !== 0 &&
    currentClass.value.status !== 3,
);

// ========== 在班学员（下拉选择核查对象） ==========
const students = ref<TrainingPlanApi.ClassStudent[]>([]);
const loading = ref(false);

async function loadStudents() {
  if (!currentClass.value?.id) {
    students.value = [];
    return;
  }
  loading.value = true;
  try {
    const data = await getClassStudentPage({
      pageNo: 1,
      pageSize: 500,
      classId: currentClass.value.id,
    });
    students.value = data?.list ?? [];
  } finally {
    loading.value = false;
  }
}

function handleClassChange(row?: TrainingPlanApi.Class) {
  currentClass.value = row;
  qualUser.value = undefined;
  qualResult.value = undefined;
  qualProject.value = row?.projectId || '';
  if (row?.id) {
    loadStudents();
  } else {
    students.value = [];
  }
}

// ========== 核查 ==========
const qualUser = ref<number | undefined>();
const qualProject = ref('');
const qualLoading = ref(false);
const qualResult = ref<TrainingPlanApi.QualificationResult>();

async function handleQualificationCheck() {
  if (!qualUser.value) {
    message.warning('请先选择要核查的人员');
    return;
  }
  if (!qualProject.value.trim()) {
    message.warning('请填写工程项目编号');
    return;
  }
  qualLoading.value = true;
  try {
    qualResult.value = await checkQualification(
      qualUser.value,
      qualProject.value.trim(),
    );
  } finally {
    qualLoading.value = false;
  }
}

const columns = [
  {
    title: '培训类型',
    key: 'typeName',
    width: 130,
  },
  { title: '培训班', dataIndex: 'className', key: 'className', minWidth: 200 },
  { title: '任务完成', key: 'tasks', width: 110 },
  { title: '截止时间', dataIndex: 'deadline', key: 'deadline', width: 180 },
  { title: '结果', key: 'qualified', width: 100 },
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
        message="上岗资格核查（3-5）"
        description="传入人员 + 工程项目，系统自动核对该人员在岗前/复工等培训班的完成情况，输出合格/不合格结论。"
      />
      <Space class="mb-3" wrap>
        <Select
          v-model:value="qualUser"
          :loading="loading"
          show-search
          option-filter-prop="label"
          placeholder="选择要核查的学员"
          style="min-width: 200px"
          :options="students.map((item) => ({
            label: item.userName || `用户${item.userId}`,
            value: item.userId,
          }))"
        />
        <Input
          v-model:value="qualProject"
          allow-clear
          placeholder="工程项目编号（默认带出本班关联项目）"
          style="min-width: 260px"
        />
        <Button
          type="primary"
          :loading="qualLoading"
          :disabled="!canCheck"
          @click="handleQualificationCheck"
        >
          核查上岗资格
        </Button>
        <Button @click="loadStudents">载入学员</Button>
      </Space>

      <template v-if="qualResult">
        <Alert
          class="mb-3"
          :type="qualResult.qualified ? 'success' : 'error'"
          show-icon
          :message="
            qualResult.qualified
              ? '核查通过：该人员具备上岗资格'
              : '核查未通过：存在未完成培训项目'
          "
          :description="qualResult.reason"
        />
        <Table
          row-key="classId"
          size="small"
          :columns="columns"
          :data-source="qualResult.items"
          :pagination="false"
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
    </template>
    <div
      v-else
      class="mt-10 text-center text-sm text-gray-400"
    >
      请先在上方选择培训班（暂无班级请先到培训班管理创建并开班）
    </div>
  </Page>
</template>
