<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Form, FormItem, Input, message, Modal, Select, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import {
  auditSchool,
  createSchool,
  deleteSchool,
  getSchoolPage,
  toggleSchoolStatus,
  updateSchool,
} from '../api';
import {
  COOP_MODE_OPTIONS,
  SCHOOL_CATEGORY_OPTIONS,
  SCHOOL_STATUS_OPTIONS,
  schoolStatusColor,
  schoolStatusText,
} from '../constants';

defineOptions({ name: 'TrainingCoopSchool' });

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '请输入院校名称' },
        fieldName: 'name',
        label: '院校名称',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: SCHOOL_CATEGORY_OPTIONS },
        fieldName: 'category',
        label: '院校类别',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: SCHOOL_STATUS_OPTIONS },
        fieldName: 'status',
        label: '状态',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'code', title: '院校代码', width: 90 },
      { field: 'name', title: '院校名称', minWidth: 200, showOverflow: true },
      { field: 'category', title: '类别', width: 110, slots: { default: 'category' } },
      { field: 'coopMode', title: '合作模式', width: 130, slots: { default: 'coopMode' } },
      { field: 'levelTag', title: '合作层级', width: 110, formatter: ({ cellValue }) => cellValue || '-' },
      { field: 'contactPerson', title: '联系人', width: 90 },
      { field: 'studentCount', title: '在培学员', width: 90, align: 'center' },
      { field: 'openClassCount', title: '在开班级', width: 90, align: 'center' },
      { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
      { field: 'applyTime', title: '申请时间', width: 160, formatter: 'formatDateTime' },
      { title: '操作', width: 210, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getSchoolPage({
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
const formData = ref<TrainingCooperationApi.School>();
const form = reactive<Record<string, any>>({});

function openCreate() {
  formData.value = undefined;
  Object.keys(form).forEach((key) => delete form[key]);
  formOpen.value = true;
}

function openEdit(row: TrainingCooperationApi.School) {
  formData.value = row;
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row);
  formOpen.value = true;
}

async function handleSave() {
  if (!form.name) {
    message.warning('请填写院校名称');
    return;
  }
  if (form.category == null) {
    message.warning('请选择院校类别');
    return;
  }
  if (form.coopMode == null) {
    message.warning('请选择合作模式');
    return;
  }
  saving.value = true;
  try {
    const payload: Partial<TrainingCooperationApi.School> = {
      id: formData.value?.id,
      name: form.name,
      code: form.code || undefined,
      category: form.category,
      coopMode: form.coopMode,
      province: form.province || undefined,
      city: form.city || undefined,
      levelTag: form.levelTag || undefined,
      contactPerson: form.contactPerson || undefined,
      contactMobile: form.contactMobile || undefined,
      remark: form.remark || undefined,
    };
    if (formData.value?.id) {
      await updateSchool(payload);
    } else {
      await createSchool(payload);
    }
    message.success('保存成功');
    formOpen.value = false;
    gridApi.query();
  } finally {
    saving.value = false;
  }
}

// ========== 行操作 ==========
async function handleDelete(row: TrainingCooperationApi.School) {
  await deleteSchool(row.id!);
  message.success('删除成功');
  gridApi.query();
}

async function handleAudit(row: TrainingCooperationApi.School, pass: boolean) {
  await auditSchool(row.id!, pass);
  message.success(pass ? '已通过入驻审核' : '已驳回入驻申请');
  gridApi.query();
}

async function handleToggle(row: TrainingCooperationApi.School) {
  await toggleSchoolStatus(row.id!);
  message.success(row.status === 2 ? '已启用，可下发新培训任务' : '已停用，不再下发新任务');
  gridApi.query();
}

function buildActions(row: TrainingCooperationApi.School) {
  const actions: any[] = [];
  if (row.status === 0) {
    actions.push(
      {
        label: '审核通过',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: { title: '确认通过该院校入驻申请？', confirm: () => handleAudit(row, true) },
      },
      {
        label: '驳回',
        type: 'link',
        danger: true,
        popConfirm: { title: '确认驳回该院校入驻申请？', confirm: () => handleAudit(row, false) },
      },
    );
  } else {
    actions.push(
      {
        label: row.status === 2 ? '启用' : '停用',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: {
          title:
            row.status === 2
              ? '启用后该院校可重新接收培训任务，确认？'
              : '停用后不再下发新培训任务，历史学员档案保留，确认？',
          confirm: () => handleToggle(row),
        },
      },
      {
        label: '编辑',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        onClick: () => openEdit(row),
      },
    );
    if (row.status !== 1) {
      actions.push({
        label: '删除',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: { title: '确认删除该合作院校记录？', confirm: () => handleDelete(row) },
      });
    }
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="合作院校管理（81 校企合作管理 · 院校入驻与维护）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增院校申请',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: openCreate,
            },
          ]"
        />
      </template>
      <template #category="{ row }">
        {{ SCHOOL_CATEGORY_OPTIONS.find((item) => item.value === row.category)?.label || '-' }}
      </template>
      <template #coopMode="{ row }">
        <Tag color="geekblue">
          {{ COOP_MODE_OPTIONS.find((item) => item.value === row.coopMode)?.label || '-' }}
        </Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="schoolStatusColor(row.status) || 'default'">
          {{ schoolStatusText(row.status) || '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>

    <Modal
      v-model:open="formOpen"
      :title="formData?.id ? '编辑合作院校' : '新增合作院校'"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <Form layout="vertical" class="pt-2">
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="院校名称" required>
            <Input v-model:value="form.name" placeholder="请输入院校全称" />
          </FormItem>
          <FormItem label="院校代码">
            <Input v-model:value="form.code" placeholder="如 SDHG" />
          </FormItem>
          <FormItem label="院校类别" required>
            <Select v-model:value="form.category" :options="SCHOOL_CATEGORY_OPTIONS" placeholder="请选择" />
          </FormItem>
          <FormItem label="合作模式" required>
            <Select v-model:value="form.coopMode" :options="COOP_MODE_OPTIONS" placeholder="请选择" />
          </FormItem>
          <FormItem label="所在省市">
            <Input v-model:value="form.province" placeholder="省份" class="!w-full" />
          </FormItem>
          <FormItem label="合作层级标签">
            <Input v-model:value="form.levelTag" placeholder="如 双高院校 / 省示范" />
          </FormItem>
          <FormItem label="联系人">
            <Input v-model:value="form.contactPerson" placeholder="请输入联系人" />
          </FormItem>
          <FormItem label="联系电话">
            <Input v-model:value="form.contactMobile" placeholder="请输入联系电话" />
          </FormItem>
        </div>
        <FormItem label="合作说明（备注）">
          <Input.TextArea v-model:value="form.remark" :rows="3" placeholder="院校合作背景、共建内容等" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
