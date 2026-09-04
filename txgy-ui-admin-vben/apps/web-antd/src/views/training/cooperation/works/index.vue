<script lang="ts" setup>
import type { TrainingCooperationApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Descriptions, DescriptionsItem, message, Modal, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { getWorkDetail, getWorkPage } from '../api';
import { AWARD_LEVEL_TEXT, WORK_TYPE_OPTIONS, WORK_TYPE_TEXT } from '../constants';

defineOptions({ name: 'TrainingCoopWorks' });

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: (): VbenFormSchema[] =>
      [
        {
          component: 'Input',
          componentProps: { allowClear: true, placeholder: '请输入成果名称' },
          fieldName: 'title',
          label: '成果名称',
        },
        {
          component: 'Input',
          componentProps: { allowClear: true, placeholder: '请输入院校名称' },
          fieldName: 'schoolName',
          label: '院校',
        },
        {
          component: 'Select',
          componentProps: { allowClear: true, options: WORK_TYPE_OPTIONS },
          fieldName: 'type',
          label: '成果形态',
        },
      ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'title', title: '成果名称', minWidth: 220, showOverflow: true },
      { field: 'type', title: '形态', width: 100, slots: { default: 'type' } },
      { field: 'contestName', title: '来源大赛', minWidth: 200, showOverflow: true, formatter: ({ cellValue }) => cellValue || '-' },
      { field: 'owner', title: '完成人', width: 160, showOverflow: true },
      { field: 'schoolName', title: '院校', minWidth: 160, showOverflow: true },
      { field: 'awardLevel', title: '获奖', width: 90, slots: { default: 'level' } },
      { field: 'year', title: '年度', width: 90, align: 'center' },
      { field: 'views', title: '浏览', width: 80, align: 'center' },
      { field: 'sizeText', title: '附件', width: 90, align: 'center' },
      { title: '操作', width: 100, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getWorkPage({
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

// ========== 成果详情 ==========
const detailOpen = ref(false);
const detail = ref<TrainingCooperationApi.Work>();

async function openDetail(row: TrainingCooperationApi.Work) {
  detail.value = await getWorkDetail(row.id!);
  detailOpen.value = true;
}

function handleExport() {
  message.info('演示环境：成果导出待后端接口接入后开放');
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="大赛成果库（82 创新大赛专区 · 优秀作品归档与全角色浏览）">
      <template #toolbar-tools>
        <Button type="primary" @click="handleExport">
          <template #icon><IconifyIcon icon="lucide:download" /></template>
          导出成果清单
        </Button>
      </template>
      <template #type="{ row }">
        <Tag color="geekblue">{{ WORK_TYPE_TEXT[row.type ?? 1] || '-' }}</Tag>
      </template>
      <template #level="{ row }">
        <Tag v-if="row.awardLevel" color="gold">{{ AWARD_LEVEL_TEXT[row.awardLevel] || '-' }}</Tag>
        <span v-else>-</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              onClick: () => openDetail(row),
            },
          ]"
        />
      </template>
    </Grid>

    <Modal v-model:open="detailOpen" title="成果详情" :footer="null" width="680px">
      <Descriptions v-if="detail" bordered :column="2" size="small" class="pt-2">
        <DescriptionsItem label="成果名称" :span="2">{{ detail.title }}</DescriptionsItem>
        <DescriptionsItem label="形态">{{ WORK_TYPE_TEXT[detail.type ?? 1] }}</DescriptionsItem>
        <DescriptionsItem label="来源大赛">{{ detail.contestName || '-' }}</DescriptionsItem>
        <DescriptionsItem label="完成人" :span="2">{{ detail.owner || '-' }}</DescriptionsItem>
        <DescriptionsItem label="院校">{{ detail.schoolName || '-' }}</DescriptionsItem>
        <DescriptionsItem label="获奖等级">
          <Tag v-if="detail.awardLevel" color="gold">{{ AWARD_LEVEL_TEXT[detail.awardLevel] }}</Tag>
          <span v-else>-</span>
        </DescriptionsItem>
        <DescriptionsItem label="年度">{{ detail.year || '-' }}</DescriptionsItem>
        <DescriptionsItem label="浏览量">{{ detail.views ?? 0 }}</DescriptionsItem>
        <DescriptionsItem label="成果简介" :span="2">{{ detail.summary || '暂无' }}</DescriptionsItem>
        <DescriptionsItem label="归档时间" :span="2">{{ detail.archivedTime || '-' }}</DescriptionsItem>
      </Descriptions>
    </Modal>
  </Page>
</template>
