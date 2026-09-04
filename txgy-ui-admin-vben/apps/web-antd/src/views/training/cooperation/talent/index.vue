<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { getTalentPage, updateTalentStatus } from '../api';
import { SKILL_LEVEL_OPTIONS, TALENT_STATUS_OPTIONS, talentStatusColor } from '../constants';

defineOptions({ name: 'TrainingCoopTalent' });

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '请输入姓名' },
        fieldName: 'studentName',
        label: '姓名',
      },
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '请输入院校名称' },
        fieldName: 'schoolName',
        label: '院校',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: SKILL_LEVEL_OPTIONS },
        fieldName: 'skillLevel',
        label: '技能等级',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: TALENT_STATUS_OPTIONS },
        fieldName: 'status',
        label: '对接状态',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'studentNo', title: '学号', width: 120 },
      { field: 'studentName', title: '姓名', width: 90 },
      { field: 'schoolName', title: '院校', minWidth: 160, showOverflow: true },
      { field: 'major', title: '专业', minWidth: 140, showOverflow: true },
      { field: 'skillLevel', title: '技能等级', width: 100, slots: { default: 'skill' } },
      { field: 'trainedCourseCount', title: '培训课程', width: 90, align: 'center' },
      { field: 'score', title: '综合评分', width: 90, align: 'center' },
      { field: 'expectPost', title: '期望岗位', minWidth: 130, formatter: ({ cellValue }) => cellValue || '-' },
      { field: 'status', title: '对接状态', width: 100, slots: { default: 'status' } },
      { title: '操作', width: 160, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getTalentPage({
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

async function handleStatus(row: TrainingCooperationApi.Talent, status: number) {
  await updateTalentStatus(row.id!, status);
  message.success('人才对接状态已更新');
  gridApi.query();
}

function handleExport() {
  message.info('演示环境：人才导出按授权范围脱敏输出，待后端接口接入后开放');
}

function buildActions(row: TrainingCooperationApi.Talent) {
  const actions: any[] = [];
  if (row.status === 0) {
    actions.push({
      label: '标记洽谈中',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      popConfirm: { title: '确认将该人才标记为“洽谈中”？', confirm: () => handleStatus(row, 1) },
    });
  } else if (row.status === 1) {
    actions.push({
      label: '确认录用',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      popConfirm: { title: '确认已录用该人才？', confirm: () => handleStatus(row, 2) },
    });
    actions.push({
      label: '退回可对接',
      type: 'link',
      danger: true,
      onClick: () => handleStatus(row, 0),
    });
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="校企人才输出 · 院校人才库（81 校企合作管理 · 企业筛选实习/就业人才）">
      <template #toolbar-tools>
        <Button type="primary" @click="handleExport">
          <template #icon><IconifyIcon icon="lucide:download" /></template>
          导出候选人
        </Button>
      </template>
      <template #skill="{ row }">
        {{ SKILL_LEVEL_OPTIONS.find((item) => item.value === row.skillLevel)?.label || '-' }}
      </template>
      <template #status="{ row }">
        <Tag :color="talentStatusColor(row.status) || 'default'">
          {{ TALENT_STATUS_OPTIONS.find((item) => item.value === row.status)?.label || '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>
  </Page>
</template>
