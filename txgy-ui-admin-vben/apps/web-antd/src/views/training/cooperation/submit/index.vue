<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Descriptions, DescriptionsItem, message, Modal, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import {
  auditSubmission,
  getSubmissionDetail,
  getSubmissionPage,
  rejectSubmission,
} from '../api';
import { SUBMIT_STATUS_OPTIONS, submitStatusColor } from '../constants';

defineOptions({ name: 'TrainingCoopSubmit' });

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: (): VbenFormSchema[] =>
      [
        {
          component: 'Input',
          componentProps: { allowClear: true, placeholder: '请输入大赛名称' },
          fieldName: 'contestName',
          label: '大赛名称',
        },
        {
          component: 'Input',
          componentProps: { allowClear: true, placeholder: '请输入学员姓名' },
          fieldName: 'studentName',
          label: '学员姓名',
        },
        {
          component: 'Select',
          componentProps: { allowClear: true, options: SUBMIT_STATUS_OPTIONS },
          fieldName: 'status',
          label: '状态',
        },
      ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'contestName', title: '大赛名称', minWidth: 220, showOverflow: true },
      { field: 'studentName', title: '姓名', width: 90 },
      { field: 'schoolName', title: '院校', minWidth: 150, showOverflow: true },
      { field: 'workTitle', title: '作品/项目名称', minWidth: 200, showOverflow: true },
      { field: 'workType', title: '作品类型', width: 100, formatter: ({ cellValue }) => cellValue || '-' },
      { field: 'status', title: '状态', width: 110, slots: { default: 'status' } },
      { field: 'submitTime', title: '提交时间', width: 160, formatter: 'formatDateTime' },
      { title: '操作', width: 170, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getSubmissionPage({
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

// ========== 详情弹窗 ==========
const detailOpen = ref(false);
const detail = ref<TrainingCooperationApi.Submission>();

async function openDetail(row: TrainingCooperationApi.Submission) {
  detail.value = await getSubmissionDetail(row.id!);
  detailOpen.value = true;
}

// ========== 报名/作品审核 ==========
async function handleAudit(row: TrainingCooperationApi.Submission, pass: boolean) {
  await auditSubmission(row.id!, pass);
  message.success(pass ? '作品已入围，将进入评审环节' : '作品未入围');
  gridApi.query();
}

async function handleReject(row: TrainingCooperationApi.Submission) {
  await rejectSubmission(row.id!);
  message.success('报名已驳回，学员可修改报名信息后重新提交');
  gridApi.query();
}

function buildActions(row: TrainingCooperationApi.Submission) {
  const actions: any[] = [
    {
      label: '查看',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      onClick: () => openDetail(row),
    },
  ];
  if (row.status === 0) {
    actions.push({
      label: '驳回报名',
      type: 'link',
      danger: true,
      popConfirm: { title: '确认驳回该学员报名？', confirm: () => handleReject(row) },
    });
  } else if (row.status === 1) {
    actions.push(
      {
        label: '确认入围',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: { title: '确认该作品入围？', confirm: () => handleAudit(row, true) },
      },
      {
        label: '未入围',
        type: 'link',
        danger: true,
        popConfirm: { title: '确认该作品未入围？', confirm: () => handleAudit(row, false) },
      },
    );
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="报名与作品提交（82 创新大赛专区 · 报名审核与作品筛选）">
      <template #status="{ row }">
        <Tag :color="submitStatusColor(row.status) || 'default'">
          {{ SUBMIT_STATUS_OPTIONS.find((item) => item.value === row.status)?.label || '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>

    <Modal v-model:open="detailOpen" title="报名与作品详情" :footer="null" width="680px">
      <Descriptions v-if="detail" bordered :column="2" size="small" class="pt-2">
        <DescriptionsItem label="学员" :span="2">{{ detail.studentName }}（{{ detail.studentNo }}）</DescriptionsItem>
        <DescriptionsItem label="院校" :span="2">{{ detail.schoolName || '-' }}</DescriptionsItem>
        <DescriptionsItem label="参赛形式">
          {{ detail.isTeam ? `团队（${detail.teamName || '-'}）` : '个人' }}
        </DescriptionsItem>
        <DescriptionsItem label="作品类型">{{ detail.workType || '-' }}</DescriptionsItem>
        <DescriptionsItem label="作品名称" :span="2">{{ detail.workTitle || '-' }}</DescriptionsItem>
        <DescriptionsItem label="作品说明" :span="2">{{ detail.workDesc || '暂无' }}</DescriptionsItem>
        <DescriptionsItem label="提交时间" :span="2">{{ detail.submitTime || '-' }}</DescriptionsItem>
      </Descriptions>
    </Modal>
  </Page>
</template>
