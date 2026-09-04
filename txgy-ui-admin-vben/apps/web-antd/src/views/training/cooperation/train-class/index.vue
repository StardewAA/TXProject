<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Form, FormItem, Input, message, Modal, Select, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import {
  cancelTrainClass,
  createTrainClass,
  deleteTrainClass,
  finishTrainClass,
  getSchoolPage,
  getTrainClassPage,
  startTrainClass,
  updateTrainClass,
} from '../api';
import {
  TRAIN_CLASS_STATUS_OPTIONS,
  TRAIN_CLASS_TYPE_OPTIONS,
  trainClassStatusColor,
  trainClassStatusText,
} from '../constants';

defineOptions({ name: 'TrainingCoopTrainClass' });

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '请输入班级名称' },
        fieldName: 'name',
        label: '班级名称',
      },
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '请输入合作院校' },
        fieldName: 'schoolName',
        label: '合作院校',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: TRAIN_CLASS_TYPE_OPTIONS },
        fieldName: 'type',
        label: '班级类型',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: TRAIN_CLASS_STATUS_OPTIONS },
        fieldName: 'status',
        label: '状态',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'code', title: '班级编码', width: 180, showOverflow: true },
      { field: 'name', title: '班级名称', minWidth: 200, showOverflow: true },
      { field: 'type', title: '类型', width: 120, slots: { default: 'type' } },
      { field: 'schoolName', title: '合作院校', minWidth: 170, showOverflow: true },
      { field: 'courseCount', title: '课程', width: 70, align: 'center' },
      { field: 'studentCount', title: '学员', width: 70, align: 'center' },
      { field: 'planStart', title: '开始时间', width: 120 },
      { field: 'planEnd', title: '结束时间', width: 120 },
      { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
      { title: '操作', width: 200, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getTrainClassPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  },
});

// ========== 新增 / 编辑弹窗 ==========
const formOpen = ref(false);
const saving = ref(false);
const formData = ref<TrainingCooperationApi.TrainClass>();
const schoolOptions = ref<{ label: string; value: number }[]>([]);
const form = reactive<Record<string, any>>({});

async function loadSchoolOptions() {
  const data = await getSchoolPage({ pageNo: 1, pageSize: 200 });
  schoolOptions.value = (data?.list ?? []).map((item) => ({
    value: item.id!,
    label: `${item.code ? `[${item.code}] ` : ''}${item.name}`,
  }));
}

function openCreate() {
  formData.value = undefined;
  Object.keys(form).forEach((key) => delete form[key]);
  formOpen.value = true;
  loadSchoolOptions();
}

function openEdit(row: TrainingCooperationApi.TrainClass) {
  formData.value = row;
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row);
  formOpen.value = true;
  loadSchoolOptions();
}

async function handleSave() {
  if (!form.name) {
    message.warning('请填写班级名称');
    return;
  }
  if (form.schoolId == null) {
    message.warning('请选择合作院校');
    return;
  }
  if (form.type == null) {
    message.warning('请选择班级类型');
    return;
  }
  saving.value = true;
  try {
    const payload: Partial<TrainingCooperationApi.TrainClass> = {
      id: formData.value?.id,
      name: form.name,
      code: form.code || undefined,
      schoolId: form.schoolId,
      schoolName: schoolOptions.value.find((item) => item.value === form.schoolId)?.label,
      type: form.type,
      coursePackage: form.coursePackage || undefined,
      headTeacher: form.headTeacher || undefined,
      planStart: form.planStart || undefined,
      planEnd: form.planEnd || undefined,
      remark: form.remark || undefined,
    };
    if (formData.value?.id) {
      await updateTrainClass(payload);
    } else {
      await createTrainClass(payload);
    }
    message.success('保存成功');
    formOpen.value = false;
    gridApi.query();
  } finally {
    saving.value = false;
  }
}

// ========== 行操作 ==========
async function handleStart(row: TrainingCooperationApi.TrainClass) {
  await startTrainClass(row.id!);
  message.success('已开班，课程包已下发并生成 VR 实操任务');
  gridApi.query();
}

async function handleFinish(row: TrainingCooperationApi.TrainClass) {
  await finishTrainClass(row.id!);
  message.success('班级已结业，学员档案已归档互通');
  gridApi.query();
}

async function handleCancel(row: TrainingCooperationApi.TrainClass) {
  await cancelTrainClass(row.id!);
  message.success('班级已取消');
  gridApi.query();
}

async function handleDelete(row: TrainingCooperationApi.TrainClass) {
  await deleteTrainClass(row.id!);
  message.success('删除成功');
  gridApi.query();
}

function buildActions(row: TrainingCooperationApi.TrainClass) {
  const actions: any[] = [];
  if (row.status === 0) {
    actions.push(
      {
        label: '开班',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: {
          title: '确认开班？开班后课程包锁定并下发至学员',
          confirm: () => handleStart(row),
        },
      },
      {
        label: '编辑',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        onClick: () => openEdit(row),
      },
      {
        label: '删除',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: { title: '确认删除该班级？', confirm: () => handleDelete(row) },
      },
    );
  } else if (row.status === 1) {
    actions.push(
      {
        label: '结业',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        popConfirm: { title: '确认结业？结业后自动归档学员档案', confirm: () => handleFinish(row) },
      },
      {
        label: '取消',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: { title: '确认取消该班级？', confirm: () => handleCancel(row) },
      },
    );
  } else {
    actions.push({
      label: '删除',
      type: 'link',
      danger: true,
      icon: ACTION_ICON.DELETE,
      popConfirm: { title: '确认删除该班级记录？', confirm: () => handleDelete(row) },
    });
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="校企定向培训班管理（81 校企合作管理 · 定向班/订单班与实习任务）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增定向班',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: openCreate,
            },
          ]"
        />
      </template>
      <template #type="{ row }">
        <Tag color="geekblue">
          {{ TRAIN_CLASS_TYPE_OPTIONS.find((item) => item.value === row.type)?.label || '-' }}
        </Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="trainClassStatusColor(row.status) || 'default'">
          {{ trainClassStatusText(row.status) || '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>

    <Modal
      v-model:open="formOpen"
      :title="formData?.id ? '编辑定向班' : '新增定向班'"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <Form layout="vertical" class="pt-2">
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="班级名称" required>
            <Input v-model:value="form.name" placeholder="如 防腐保温定向培养班（2026 届）" />
          </FormItem>
          <FormItem label="班级编码">
            <Input v-model:value="form.code" placeholder="自动生成时可留空" />
          </FormItem>
          <FormItem label="合作院校" required>
            <Select
              v-model:value="form.schoolId"
              :options="schoolOptions"
              placeholder="请选择合作院校"
              show-search
              option-filter-prop="label"
            />
          </FormItem>
          <FormItem label="班级类型" required>
            <Select v-model:value="form.type" :options="TRAIN_CLASS_TYPE_OPTIONS" placeholder="请选择" />
          </FormItem>
          <FormItem label="班主任/带队老师">
            <Input v-model:value="form.headTeacher" placeholder="请输入姓名" />
          </FormItem>
          <FormItem label="培训起止时间">
            <div class="flex gap-2">
              <Input v-model:value="form.planStart" type="date" class="!w-full" />
              <Input v-model:value="form.planEnd" type="date" class="!w-full" />
            </div>
          </FormItem>
        </div>
        <FormItem label="课程包（下发内容说明）">
          <Input.TextArea v-model:value="form.coursePackage" :rows="3" placeholder="如 防腐保温基础 + 喷砂除锈实操 + 保温层施工，开班后按课程包下发学习与 VR 实训任务" />
        </FormItem>
        <FormItem label="备注">
          <Input.TextArea v-model:value="form.remark" :rows="2" placeholder="请输入备注" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
