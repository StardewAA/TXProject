<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, Checkbox, FormItem, InputNumber, message, Modal, Progress, Select, Statistic, Steps, Tabs, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { getMyTaskList, getRecordPage, submitPractice } from '../api';
import {
  VR_ASSESS_METHOD_TEXT,
  VR_MY_TASK_TAB_OPTIONS,
  VR_PRACTICE_SOURCE_OPTIONS,
  vrTaskStatusColor,
  vrTaskStatusText,
} from '../constants';

defineOptions({ name: 'TrainingVrPractice' });

// 演示：切换当前学员身份
const STUDENT_OPTIONS = [
  { label: '张伟（T001）', value: '张伟', no: 'T001' },
  { label: '刘洋（T002）', value: '刘洋', no: 'T002' },
  { label: '陈静（T003）', value: '陈静', no: 'T003' },
  { label: '赵磊（T004）', value: '赵磊', no: 'T004' },
  { label: '孙丽（T005）', value: '孙丽', no: 'T005' },
];

const student = ref(STUDENT_OPTIONS[0]);
const studentNo = computed(() => student.value?.no || 'T001');
const studentName = computed(() => student.value?.value || '张伟');

const activeTab = ref('pending');
const totalHours = ref(0);

const [TaskGrid, taskGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '项目名称 / 任务说明' },
        fieldName: 'keyword',
        label: '关键字',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'projectName', title: 'VR 实操项目', minWidth: 180, showOverflow: true },
      { field: 'projectStatus', title: '资源状态', width: 90, slots: { default: 'resStatus' } },
      { field: 'bindName', title: '来源课程 / 培训班', minWidth: 170, showOverflow: true },
      { field: 'requireTimes', title: '完成次数', width: 90, align: 'center' },
      { field: 'passScore', title: '合格线', width: 80, align: 'center' },
      { field: 'deadline', title: '截止日期', width: 110 },
      { field: 'status', title: '任务状态', width: 100, slots: { default: 'status' } },
      { field: 'doneCount', title: '已完成次数', width: 95, align: 'center' },
      { title: '最近成绩', width: 90, align: 'center', slots: { default: 'lastScore' } },
      { title: '操作', width: 120, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const list = await getMyTaskList(activeTab.value as 'pending' | 'finished' | 'overdue');
          const keyword = formValues?.keyword;
          const rows = keyword
            ? list.filter((row) => `${row.projectName}${row.bindName}`.includes(keyword))
            : list;
          return { list: rows.slice(0, 200), total: rows.length };
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  },
});

function refreshStats() {
  // 汇总当前学员的合格学时与各任务最近一次成绩
  // 后端分页每页条数上限为 200，超出会报「每页条数最大值为 200」
  getRecordPage({ keyword: studentName.value, pageNo: 1, pageSize: 200 }).then((result) => {
    const records = result.list;
    totalHours.value = records.reduce((sum, row) => sum + (row.gainedHours ?? 0), 0);
    const map: Record<string, number> = {};
    for (const row of records) {
      if (row.taskId != null) {
        map[String(row.taskId)] = row.score ?? 0;
      }
    }
    lastScoreMap.value = map;
  });
}

// 重置 tab 时刷新列表（getMyTaskList 依赖 activeTab）
function handleTabChange() {
  taskGridApi.query();
  refreshStats();
}

// ==================== 开始实操（模拟 VR 流程） ====================
const practiceOpen = ref(false);
const saving = ref(false);
const practiceTask = ref<any>();
const step = ref(0);
const scoreMap = reactive<Record<string, boolean>>({});
const costMinutes = ref(20);
const source = ref(1);
const practiceResult = ref<any>();

const CHECK_POINTS = [
  '佩戴 VR 头显并完成设备自检',
  '进入实操场景并确认安全操作规程',
  '按工艺顺序完成关键操作动作',
  '正确使用工器具且无违规操作',
  '完成作业面自检并提交结果',
];

const currentPoint = computed(() => CHECK_POINTS[step.value - 1]);

function startPractice(row: any) {
  practiceTask.value = row;
  practiceResult.value = undefined;
  source.value = 1;
  costMinutes.value = 20;
  step.value = 0;
  Object.keys(scoreMap).forEach((key) => delete scoreMap[key]);
  practiceOpen.value = true;
}

function nextStep() {
  if (step.value < CHECK_POINTS.length) {
    step.value += 1;
  }
}

function prevStep() {
  if (step.value > 0) {
    step.value -= 1;
  }
}

async function finishPractice() {
  if (step.value < CHECK_POINTS.length) {
    message.info('请完成全部实操步骤');
    return;
  }
  const correctCount = CHECK_POINTS.filter((_, index) => scoreMap[String(index + 1)]).length;
  if (correctCount === 0) {
    message.warning('请至少标记完成一个实操要点（演示：勾选代表操作成功）');
    return;
  }
  saving.value = true;
  try {
    const score = Math.max(
      45,
      Math.min(100, Math.round((correctCount / CHECK_POINTS.length) * 100 + (source.value === 1 ? -3 : 3))),
    );
    const result = await submitPractice({
      taskId: practiceTask.value?.id,
      studentNo: studentNo.value,
      studentName: studentName.value,
      source: source.value as 1 | 2,
      score,
      costMinutes: costMinutes.value,
    });
    practiceResult.value = result;
    taskGridApi.query();
    refreshStats();
  } finally {
    saving.value = false;
  }
}

function closePractice() {
  practiceOpen.value = false;
  practiceResult.value = undefined;
}

function buildActions(row: any) {
  const finished = (row.doneCount ?? 0) >= (row.requireTimes ?? 1) || row.status === 2;
  if (finished) {
    return [];
  }
  return [
    {
      label: '开始实操',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      onClick: () => startPractice(row),
    },
  ];
}

const lastScoreMap = ref<Record<string, number>>({});

// 首次进入与切换学员时刷新统计
function handleStudentChange() {
  refreshStats();
  taskGridApi.query();
}

onMounted(() => {
  refreshStats();
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-3 rounded border border-gray-100 bg-white p-3">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-3">
          <span class="text-sm text-gray-500">当前学员：</span>
          <Select v-model:value="student" style="width: 180px" @change="handleStudentChange" />
        </div>
        <Card size="small" class="!w-44 !bg-green-50">
          <Statistic title="累计获得学时" :value="totalHours" suffix="h" />
        </Card>
      </div>
      <Tabs v-model:active-key="activeTab" size="small" class="mt-2" @change="handleTabChange">
        <Tabs.TabPane
          v-for="tab in VR_MY_TASK_TAB_OPTIONS"
          :key="tab.value"
          :tab="tab.label"
        />
      </Tabs>
    </div>

    <TaskGrid table-title="学员 VR 实操训练（73 · 待完成 / 已完成 / 已逾期分组）">
      <template #resStatus="{ row }">
        <Tag v-if="row.projectStatus === 3" color="error">已归档</Tag>
        <Tag v-else-if="row.projectStatus === 2" color="warning">已下架</Tag>
        <Tag v-else color="success">正常</Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="vrTaskStatusColor(row.status) || 'default'">{{ vrTaskStatusText(row.status) }}</Tag>
      </template>
      <template #lastScore="{ row }">
        <span v-if="lastScoreMap[row.id] != null">{{ lastScoreMap[row.id] }}</span>
        <span v-else class="text-gray-300">-</span>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </TaskGrid>

    <!-- 模拟 VR 实操弹窗 -->
    <Modal
      v-model:open="practiceOpen"
      :footer="null"
      :title="`开始实操：${practiceTask?.projectName || ''}`"
      width="620px"
      :mask-closable="false"
    >
      <template v-if="!practiceResult">
        <div class="mb-4 text-xs text-gray-400">
          任务：{{ practiceTask?.bindName }} · 合格线 {{ practiceTask?.passScore }} 分 · 已完成
          {{ practiceTask?.doneCount ?? 0 }} / {{ practiceTask?.requireTimes ?? 1 }} 次
        </div>
        <Steps :current="step" size="small" class="mb-4">
          <Steps.Step title="设备就绪" />
          <Steps.Step v-for="(point, index) in CHECK_POINTS" :key="index" :title="`步骤${index + 1}`" />
          <Steps.Step title="提交成绩" />
        </Steps>
        <template v-if="step === 0">
          <div class="rounded border border-dashed border-blue-200 bg-blue-50 p-4">
            <div class="mb-2 text-sm font-medium text-blue-700">即将进入 VR 实操场景</div>
            <div class="mb-2 text-xs text-blue-600">
              请选择本次实操类型；完成后系统按「{{ practiceTask?.passScore }} 分合格」自动评分，合格自动累计学时。
            </div>
            <FormItem label="实操类型">
              <Select v-model:value="source" :options="VR_PRACTICE_SOURCE_OPTIONS" style="width: 200px" />
            </FormItem>
            <FormItem label="预计用时（分钟）">
              <InputNumber v-model:value="costMinutes" :min="5" :max="120" style="width: 200px" />
            </FormItem>
            <Button type="primary" @click="nextStep">进入场景</Button>
          </div>
        </template>
        <template v-else-if="step > 0 && step <= CHECK_POINTS.length">
          <div class="rounded border p-4">
            <div class="mb-2 text-sm font-medium">当前操作要点：{{ currentPoint }}</div>
            <div class="mb-3 text-xs text-gray-400">
              （演示模式：勾选代表该要点操作正确，未勾选视为动作失误）
            </div>
            <Checkbox v-model:checked="scoreMap[String(step)]">本要点操作正确</Checkbox>
            <div class="mt-4 flex gap-2">
              <Button @click="prevStep">上一步</Button>
              <Button type="primary" @click="nextStep">{{ step === CHECK_POINTS.length ? '完成实操' : '下一步' }}</Button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="rounded border border-green-200 bg-green-50 p-4">
            <div class="mb-2 text-sm font-medium text-green-700">实操完成，等待系统自动评分…</div>
            <Progress :percent="100" status="active" class="mb-3" />
            <div class="text-right">
              <Button type="primary" :loading="saving" @click="finishPractice">提交成绩</Button>
            </div>
          </div>
        </template>
      </template>
      <template v-else>
        <div class="rounded p-4" :class="practiceResult.qualified ? 'border border-green-200 bg-green-50' : 'border border-red-200 bg-red-50'">
          <div class="text-sm font-medium" :class="practiceResult.qualified ? 'text-green-700' : 'text-red-600'">
            {{ practiceResult.qualified ? '实操合格' : '实操未合格（得分低于合格线）' }}
          </div>
          <div class="mt-3 grid grid-cols-3 gap-3">
            <div class="text-center">
              <div class="text-xs text-gray-500">本次得分</div>
              <div class="text-xl font-bold text-blue-600">{{ practiceResult.score }}<span class="text-sm">/100</span></div>
            </div>
            <div class="text-center">
              <div class="text-xs text-gray-500">判定方式</div>
              <div class="mt-1">{{ VR_ASSESS_METHOD_TEXT[practiceResult.method] || '-' }}</div>
            </div>
            <div class="text-center">
              <div class="text-xs text-gray-500">获得学时</div>
              <div class="mt-1 text-lg font-bold text-green-600">{{ practiceResult.gainedHours }}h</div>
            </div>
          </div>
          <div class="mt-2 text-center text-xs text-gray-400">
            {{ practiceResult.qualified ? '学时已自动累加至个人档案' : '不合格支持重练重考，可在考核中心查看并重新练习' }}
          </div>
          <div class="mt-4 text-center">
            <Button type="primary" @click="closePractice">知道了</Button>
          </div>
        </div>
      </template>
    </Modal>
  </Page>
</template>
