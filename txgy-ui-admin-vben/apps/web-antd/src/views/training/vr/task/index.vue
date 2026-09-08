<script lang="ts" setup>
import type { TrainingVrApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { DatePicker, Form, FormItem, Input, InputNumber, message, Modal, Select, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import {
  createTask,
  deleteTask,
  disableTask,
  getPublishProjectOptions,
  getTaskPage,
} from '../api';
import {
  VR_TASK_BIND_OPTIONS,
  VR_TASK_BIND_TEXT,
  vrTaskStatusColor,
  vrTaskStatusText,
} from '../constants';

defineOptions({ name: 'TrainingVrTask' });

const [TaskGrid, taskGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '任务编号 / 项目名称' },
        fieldName: 'keyword',
        label: '关键字',
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: [
            { label: '未开始', value: 0 },
            { label: '进行中', value: 1 },
            { label: '已完成', value: 2 },
            { label: '已逾期', value: 3 },
            { label: '已停用', value: 4 },
          ],
        },
        fieldName: 'status',
        label: '任务状态',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: VR_TASK_BIND_OPTIONS },
        fieldName: 'bindType',
        label: '绑定对象',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'taskNo', title: '任务编号', width: 150, showOverflow: true },
      { field: 'projectName', title: 'VR 实操项目', minWidth: 190, showOverflow: true },
      { field: 'bindType', title: '绑定对象', width: 90, slots: { default: 'bindType' } },
      { field: 'bindName', title: '课程 / 培训班', minWidth: 190, showOverflow: true },
      { field: 'requireTimes', title: '完成次数', width: 90, align: 'center' },
      { field: 'passScore', title: '合格线', width: 80, align: 'center' },
      { field: 'deadline', title: '截止日期', width: 110 },
      { field: 'status', title: '任务状态', width: 100, slots: { default: 'status' } },
      { title: '完成进度', width: 100, slots: { default: 'progress' } },
      { field: 'creatorName', title: '创建人', width: 90 },
      { field: 'createTime', title: '创建时间', width: 160 },
      { title: '操作', width: 150, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getTaskPage({ pageNo: page.currentPage, pageSize: page.pageSize, ...formValues }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  },
});

const formOpen = ref(false);
const saving = ref(false);
const projectOptions = ref<{ label: string; value: number; passScore?: number }[]>([]);
const formData = ref<TrainingVrApi.VrTask>();
const form = reactive<Record<string, any>>({});

async function openCreate() {
  formData.value = undefined;
  const projects = await getPublishProjectOptions();
  projectOptions.value = projects.map((row) => ({
    label: `${row.name}（${row.code}）`,
    value: row.id!,
    passScore: row.passScore,
  }));
  Object.keys(form).forEach((key) => delete form[key]);
  form.bindType = 2;
  form.requireTimes = 1;
  formOpen.value = true;
}

async function handleSave() {
  if (!form.projectId) {
    message.warning('请选择 VR 实操项目');
    return;
  }
  if (!form.bindName) {
    message.warning('请填写绑定的课程 / 培训班名称');
    return;
  }
  if (!form.deadline) {
    message.warning('请设置任务截止日期（逾期自动标记）');
    return;
  }
  saving.value = true;
  try {
    const payload: Partial<TrainingVrApi.VrTask> = {
      id: formData.value?.id,
      projectId: form.projectId,
      bindType: form.bindType,
      bindName: form.bindName,
      requireTimes: form.requireTimes,
      passScore: form.passScore,
      deadline: form.deadline,
    };
    await createTask(payload);
    message.success('VR 实操任务创建成功，已下发给绑定对象');
    formOpen.value = false;
    taskGridApi.query();
  } finally {
    saving.value = false;
  }
}

function handleProjectChange() {
  const project = projectOptions.value.find((item) => item.value === form.projectId);
  if (project?.passScore != null) {
    form.passScore = project.passScore;
  }
}

function handleDisable(row: TrainingVrApi.VrTask) {
  disableTask(row.id!);
  message.success('任务已停用，不再下发给新学员');
  taskGridApi.query();
}

function handleDelete(row: TrainingVrApi.VrTask) {
  deleteTask(row.id!);
  message.success('删除成功');
  taskGridApi.query();
}

function buildActions(row: TrainingVrApi.VrTask) {
  const actions: any[] = [];
  if (row.status !== 4 && row.status !== 2) {
    actions.push({
      label: '停用',
      type: 'link',
      danger: true,
      icon: ACTION_ICON.VIEW,
      popConfirm: { title: '确认停用该任务？', confirm: () => handleDisable(row) },
    });
  }
  if (row.status === 4) {
    actions.push({
      label: '删除',
      type: 'link',
      danger: true,
      icon: ACTION_ICON.DELETE,
      popConfirm: { title: '确认删除该任务（仅演示删除无记录的停用任务）？', confirm: () => handleDelete(row) },
    });
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <TaskGrid table-title="VR 实操任务管理（72 · 选项目建任务 · 可绑定课程/培训班）">
      <template #toolbar-tools>
        <TableAction
          :actions="[{ label: '新建 VR 实操任务', type: 'primary', icon: ACTION_ICON.ADD, onClick: openCreate }]"
        />
      </template>
      <template #bindType="{ row }">
        <Tag :color="row.bindType === 1 ? 'cyan' : 'blue'">{{ VR_TASK_BIND_TEXT[row.bindType] || '-' }}</Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="vrTaskStatusColor(row.status) || 'default'">{{ vrTaskStatusText(row.status) }}</Tag>
      </template>
      <template #progress="{ row }">
        <span class="text-gray-500">{{ row.doneCount ?? 0 }} / {{ row.studentCount ?? 0 }} 人</span>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </TaskGrid>

    <Modal v-model:open="formOpen" title="新建 VR 实操任务" :confirm-loading="saving" width="620px" @ok="handleSave">
      <Form layout="vertical" class="pt-2">
        <FormItem label="选择 VR 实操项目（仅已上架项目可选）" required>
          <Select v-model:value="form.projectId" :options="projectOptions" placeholder="选择已上架 VR 实操项目" @change="handleProjectChange" />
        </FormItem>
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="绑定对象类型">
            <Select v-model:value="form.bindType" :options="VR_TASK_BIND_OPTIONS" />
          </FormItem>
          <FormItem label="绑定课程 / 培训班" required>
            <Input v-model:value="form.bindName" placeholder="如 2026 防腐作业技能提升班（三期）" />
          </FormItem>
        </div>
        <div class="grid grid-cols-3 gap-x-4">
          <FormItem label="要求完成次数">
            <InputNumber v-model:value="form.requireTimes" :min="1" :max="20" class="!w-full" />
          </FormItem>
          <FormItem label="合格评分标准">
            <InputNumber v-model:value="form.passScore" :min="0" :max="100" class="!w-full" />
          </FormItem>
          <FormItem label="任务截止日期">
            <DatePicker v-model:value="form.deadline" class="!w-full" value-format="YYYY-MM-DD" placeholder="逾期自动标记" />
          </FormItem>
        </div>
        <div class="rounded bg-gray-50 px-3 py-2 text-xs text-gray-500">
          任务将下发给绑定课程/培训班全部学员；学员完成次数达标且合格后自动累计学时；截止日期后未完成自动标记「已逾期」。
        </div>
      </Form>
    </Modal>
  </Page>
</template>
