<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingResourceApi } from '../api';

import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

import { getOperationLogPage } from '../api';

defineOptions({ name: 'TrainingResourceLog' });

const ACTION_LABELS: Record<string, { text: string; color: string }> = {
  CREATE: { text: '新增', color: 'green' },
  UPDATE: { text: '修改', color: 'blue' },
  DELETE: { text: '删除', color: 'red' },
  IMPORT: { text: '导入', color: 'cyan' },
  ENABLE: { text: '启用', color: 'green' },
  DISABLE: { text: '停用', color: 'orange' },
  ONLINE: { text: '上线', color: 'green' },
  OFFLINE: { text: '下架', color: 'orange' },
  ARCHIVE: { text: '归档', color: 'purple' },
  DRAFT: { text: '草稿', color: 'default' },
};

const TARGET_LABELS: Record<string, string> = {
  doc: '文档',
  video: '视频',
  question: '试题',
};

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'keyword',
        label: '关键词',
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: '操作人 / 摘要内容',
        },
      },
      {
        fieldName: 'action',
        label: '动作',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部动作',
          options: Object.keys(ACTION_LABELS).map((key) => ({
            label: ACTION_LABELS[key].text,
            value: key,
          })),
        },
      },
      {
        fieldName: 'targetType',
        label: '对象',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部对象',
          options: [
            { label: '文档', value: 'doc' },
            { label: '视频', value: 'video' },
            { label: '试题', value: 'question' },
          ],
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'userName', title: '操作人', width: 130 },
      {
        field: 'action',
        title: '动作',
        width: 90,
        align: 'center',
        slots: { default: 'action' },
      },
      {
        field: 'targetType',
        title: '对象',
        width: 90,
        align: 'center',
        slots: { default: 'targetType' },
      },
      {
        field: 'targetId',
        title: '对象ID',
        width: 90,
        align: 'center',
        slots: { default: 'targetId' },
      },
      { field: 'detail', title: '变更摘要', minWidth: 300, showOverflow: true },
      {
        field: 'createTime',
        title: '操作时间',
        width: 180,
        formatter: 'formatDateTime',
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getOperationLogPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<TrainingResourceApi.OperationLog>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="资源中心操作日志（新增/编辑/状态流转/导入/删除）">
      <template #action="{ row }">
        <Tag :color="(ACTION_LABELS[row.action] || {}).color || 'default'">
          {{ (ACTION_LABELS[row.action] || {}).text || row.action }}
        </Tag>
      </template>
      <template #targetType="{ row }">
        <Tag color="geekblue">
          {{ TARGET_LABELS[row.targetType] || row.targetType }}
        </Tag>
      </template>
      <template #targetId="{ row }">
        <span class="text-gray-500">{{ row.targetId ?? '-' }}</span>
      </template>
    </Grid>
  </Page>
</template>
