<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Form, FormItem, Input, message, Modal, Select, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import {
  createContest,
  deleteContest,
  getContestPage,
  transitionContest,
  updateContest,
} from '../api';
import {
  CONTEST_STATUS_OPTIONS,
  CONTEST_TYPE_OPTIONS,
  contestStatusColor,
  contestStatusText,
} from '../constants';

defineOptions({ name: 'TrainingCoopContest' });

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: (): VbenFormSchema[] =>
      [
        {
          component: 'Input',
          componentProps: { allowClear: true, placeholder: '请输入大赛名称' },
          fieldName: 'name',
          label: '大赛名称',
        },
        {
          component: 'Select',
          componentProps: { allowClear: true, options: CONTEST_TYPE_OPTIONS },
          fieldName: 'type',
          label: '大赛类型',
        },
        {
          component: 'Select',
          componentProps: { allowClear: true, options: CONTEST_STATUS_OPTIONS },
          fieldName: 'status',
          label: '状态',
        },
      ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'code', title: '大赛编码', width: 130 },
      { field: 'name', title: '大赛名称', minWidth: 220, showOverflow: true },
      { field: 'type', title: '类型', width: 100, slots: { default: 'type' } },
      { field: 'contestDate', title: '大赛日期', width: 120 },
      { field: 'signStart', title: '报名开始', width: 120 },
      { field: 'signEnd', title: '报名截止', width: 120 },
      { field: 'orgName', title: '主办方', minWidth: 150, formatter: ({ cellValue }) => cellValue || '-' },
      { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
      { field: 'createTime', title: '创建时间', width: 160, formatter: 'formatDateTime' },
      { title: '操作', width: 170, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getContestPage({
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
const formData = ref<TrainingCooperationApi.Contest>();
const form = reactive<Record<string, any>>({});

function openCreate() {
  formData.value = undefined;
  Object.keys(form).forEach((key) => delete form[key]);
  formOpen.value = true;
}

function openEdit(row: TrainingCooperationApi.Contest) {
  formData.value = row;
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row);
  formOpen.value = true;
}

async function handleSave() {
  if (!form.name) {
    message.warning('请填写大赛名称');
    return;
  }
  if (form.type == null) {
    message.warning('请选择大赛类型');
    return;
  }
  if (!form.signStart || !form.signEnd) {
    message.warning('请配置报名起止时间');
    return;
  }
  saving.value = true;
  try {
    const payload: Partial<TrainingCooperationApi.Contest> = {
      id: formData.value?.id,
      name: form.name,
      code: form.code || undefined,
      type: form.type,
      theme: form.theme || undefined,
      orgName: form.orgName || undefined,
      contestDate: form.contestDate || undefined,
      signStart: form.signStart || undefined,
      signEnd: form.signEnd || undefined,
      targetRange: form.targetRange || undefined,
      judges: form.judges || undefined,
      note: form.note || undefined,
    };
    if (formData.value?.id) {
      await updateContest(payload);
    } else {
      await createContest(payload);
    }
    message.success('保存成功');
    formOpen.value = false;
    gridApi.query();
  } finally {
    saving.value = false;
  }
}

// ========== 大赛状态机 ==========
async function handleTransition(row: TrainingCooperationApi.Contest, action: string) {
  const textMap: Record<string, string> = {
    start: '已开启报名，学员端可在线报名',
    signOff: '报名已截止，已进入评审阶段',
    publish: '获奖名单已公示，进入公示期',
    finish: '大赛已结束，成果已归档成果库',
    cancel: '大赛已取消',
  };
  await transitionContest(row.id!, action);
  message.success(textMap[action]);
  gridApi.query();
}

async function handleDelete(row: TrainingCooperationApi.Contest) {
  await deleteContest(row.id!);
  message.success('删除成功');
  gridApi.query();
}

function buildActions(row: TrainingCooperationApi.Contest) {
  const actions: any[] = [];
  if (row.status === 0) {
    actions.push(
      {
        label: '开启报名',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: { title: '确认开启报名开关？开启后学员可在线报名', confirm: () => handleTransition(row, 'start') },
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
        popConfirm: { title: '确认删除该大赛？', confirm: () => handleDelete(row) },
      },
    );
  } else if (row.status === 1) {
    actions.push(
      {
        label: '截止报名',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        popConfirm: { title: '确认截止报名并进入评审阶段？', confirm: () => handleTransition(row, 'signOff') },
      },
      {
        label: '取消大赛',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: { title: '确认取消该大赛？', confirm: () => handleTransition(row, 'cancel') },
      },
    );
  } else if (row.status === 2) {
    actions.push(
      {
        label: '发布公示',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: {
          title: '确认发布获奖名单公示？公示后不可撤回',
          confirm: () => handleTransition(row, 'publish'),
        },
      },
    );
  } else if (row.status === 3) {
    actions.push({
      label: '结束大赛',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      popConfirm: { title: '确认结束大赛？优秀作品将归档至成果库', confirm: () => handleTransition(row, 'finish') },
    });
  } else if (row.status === 5) {
    actions.push({
      label: '删除',
      type: 'link',
      danger: true,
      icon: ACTION_ICON.DELETE,
      popConfirm: { title: '确认删除该大赛记录？', confirm: () => handleDelete(row) },
    });
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="大赛基础管理（82 创新大赛专区 · 赛程配置与报名开关）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新建大赛',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: openCreate,
            },
          ]"
        />
      </template>
      <template #type="{ row }">
        <Tag color="geekblue">
          {{ CONTEST_TYPE_OPTIONS.find((item) => item.value === row.type)?.label || '-' }}
        </Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="contestStatusColor(row.status) || 'default'">
          {{ contestStatusText(row.status) || '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>

    <Modal
      v-model:open="formOpen"
      :title="formData?.id ? '编辑大赛' : '新建大赛'"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <Form layout="vertical" class="pt-2">
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="大赛名称" required>
            <Input v-model:value="form.name" placeholder="如 全国防腐保温施工技术创新大赛 2026" />
          </FormItem>
          <FormItem label="大赛编码">
            <Input v-model:value="form.code" placeholder="自动生成时可留空" />
          </FormItem>
          <FormItem label="大赛类型" required>
            <Select v-model:value="form.type" :options="CONTEST_TYPE_OPTIONS" placeholder="请选择" />
          </FormItem>
          <FormItem label="主办方">
            <Input v-model:value="form.orgName" placeholder="请输入主办/承办单位" />
          </FormItem>
          <FormItem label="赛题主题">
            <Input v-model:value="form.theme" placeholder="请输入大赛主题" />
          </FormItem>
          <FormItem label="大赛日期">
            <Input v-model:value="form.contestDate" type="date" class="!w-full" />
          </FormItem>
          <FormItem label="报名开始" required>
            <Input v-model:value="form.signStart" type="date" class="!w-full" />
          </FormItem>
          <FormItem label="报名截止" required>
            <Input v-model:value="form.signEnd" type="date" class="!w-full" />
          </FormItem>
        </div>
        <FormItem label="参赛对象范围">
          <Input v-model:value="form.targetRange" placeholder="如 合作院校在校生与企业人员，个人/团队均可" />
        </FormItem>
        <FormItem label="评委名单（逗号分隔）">
          <Input v-model:value="form.judges" placeholder="如 韩工、陈教授、刘工" />
        </FormItem>
        <FormItem label="备注">
          <Input.TextArea v-model:value="form.note" :rows="2" placeholder="请输入备注" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
