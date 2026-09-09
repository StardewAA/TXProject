<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { Page, useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  convertCostResult,
  deleteCostResult,
  getCostResultPage,
  submitCostResult,
} from '#/api/erp/sale/cost';
import { costColumns, costFormSchema } from './data';
import Form from './modules/form.vue';
defineOptions({ name: 'ErpCostResult' });
const [FormModal, api] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const refresh = () => gridApi.query();
async function submit(r: any) {
  await submitCostResult(r.id);
  message.success('造价结果已提交审批');
  refresh();
}
async function archive(r: any) {
  await convertCostResult(r.id, 4);
  message.success('造价结果已归档');
  refresh();
}
const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: costFormSchema() },
  gridOptions: {
    columns: costColumns(),
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, v) =>
          getCostResultPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...v,
          }),
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions,
});
</script>
<template>
  <Page auto-content-height
    ><FormModal @success="refresh" /><Grid table-title="造价管理"
      ><template #toolbar-tools
        ><TableAction
          :actions="[
            {
              label: '新增造价结果',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:cost-result:create'],
              onClick: () => api.setData({}).open(),
            },
          ]" /></template
      ><template #actions="{ row }"
        ><TableAction
          :actions="[
            {
              label: '查看/编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:cost-result:update'],
              onClick: () =>
                api
                  .setData({
                    id: row.id,
                    detail: ![0, 30].includes(row.status ?? 0),
                  })
                  .open(),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:cost-result:submit'],
              ifShow: () => [0, 30].includes(row.status ?? 0),
              popConfirm: {
                title: '仅全部完成物料映射后可提交，是否继续？',
                confirm: submit.bind(null, row),
              },
            },
            {
              label: '归档',
              type: 'link',
              auth: ['erp:cost-result:convert'],
              ifShow: () => row.status === 20 && !row.convertedType,
              popConfirm: {
                title: '确认归档该造价结果？',
                confirm: archive.bind(null, row),
              },
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:cost-result:delete'],
              ifShow: () => [0, 30].includes(row.status ?? 0),
              popConfirm: {
                title: '确认删除？',
                confirm: async () => {
                  await deleteCostResult([row.id]);
                  refresh();
                },
              },
            },
          ]" /></template></Grid
  ></Page>
</template>
