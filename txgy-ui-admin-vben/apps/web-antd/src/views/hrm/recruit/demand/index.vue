<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitDemandApi } from '#/api/hrm/recruit/demand';

import { confirm, Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteRecruitDemand,
  getRecruitDemandPage,
  HrmRecruitDemandStatus,
  updateRecruitDemandStatus,
} from '#/api/hrm/recruit/demand';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'HrmRecruitDemand' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  formModalApi.setData(null).open();
}

function handleEdit(row: HrmRecruitDemandApi.RecruitDemand) {
  formModalApi.setData(row).open();
}

async function handleDelete(row: HrmRecruitDemandApi.RecruitDemand) {
  await confirm(`确认删除用工需求单"${row.demandName}"吗？`);
  await deleteRecruitDemand(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleRefresh();
}

/** 取消需求：不再招聘，但保留历史与已到岗记录 */
async function handleCancel(row: HrmRecruitDemandApi.RecruitDemand) {
  await confirm(`确认取消需求单"${row.demandName}"吗？取消后不再用于招聘。`);
  await updateRecruitDemandStatus(row.id!, HrmRecruitDemandStatus.CANCELED);
  message.success('已取消');
  handleRefresh();
}

const STATUS_COLOR: Record<number, string> = {
  [HrmRecruitDemandStatus.PENDING]: 'warning',
  [HrmRecruitDemandStatus.RECRUITING]: 'processing',
  [HrmRecruitDemandStatus.FINISHED]: 'success',
  [HrmRecruitDemandStatus.CANCELED]: 'default',
};

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useGridFormSchema() },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getRecruitDemandPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<HrmRecruitDemandApi.RecruitDemand>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增用工需求',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:recruit:demand:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #progress="{ row }">
        <span>
          {{ row.headcount ?? 0 }} / {{ row.onboardCount ?? 0 }} /
          <span
            :class="
              (row.gapCount ?? 0) > 0 ? 'text-red-500' : 'text-green-600'
            "
          >
            {{ row.gapCount ?? 0 }}
          </span>
        </span>
      </template>
      <template #urgency="{ row }">
        <Tag
          :color="
            row.urgency === 3 ? 'error' : row.urgency === 2 ? 'warning' : 'default'
          "
        >
          {{ row.urgencyName ?? '-' }}
        </Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="STATUS_COLOR[row.status as number] ?? 'default'">
          {{ row.statusName ?? '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:recruit:demand:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '取消需求',
              type: 'link',
              auth: ['hrm:recruit:demand:update'],
              disabled: row.status === HrmRecruitDemandStatus.FINISHED
                || row.status === HrmRecruitDemandStatus.CANCELED,
              onClick: handleCancel.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              auth: ['hrm:recruit:demand:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.demandName]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
