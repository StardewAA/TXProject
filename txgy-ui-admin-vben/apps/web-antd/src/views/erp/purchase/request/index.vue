<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpPurchaseRequestApi } from '#/api/erp/purchase/request';
import { Page, useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deletePurchaseRequest,
  getPurchaseRequestPage,
  submitPurchaseRequest,
} from '#/api/erp/purchase/request';
import { createInquiryFromRequest } from '#/api/erp/purchase/inquiry';
import { requestGridColumns, requestGridFormSchema } from './data';
import Form from './modules/form.vue';
defineOptions({ name: 'ErpPurchaseRequest' });
const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const refresh = () => gridApi.query();
const create = () => formModalApi.setData({}).open();
const edit = (r: ErpPurchaseRequestApi.Request) =>
  formModalApi.setData({ id: r.id }).open();
async function submit(r: ErpPurchaseRequestApi.Request) {
  await submitPurchaseRequest(r.id!);
  message.success('采购申请已提交审批');
  refresh();
}
async function remove(r: ErpPurchaseRequestApi.Request) {
  await deletePurchaseRequest([r.id!]);
  message.success('删除成功');
  refresh();
}
async function toInquiry(r: ErpPurchaseRequestApi.Request) {
  const id = await createInquiryFromRequest(r.id!);
  message.success(`已生成询价单，编号ID：${id}`);
  refresh();
}
const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: requestGridFormSchema() },
  gridOptions: {
    columns: requestGridColumns(),
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, values) =>
          getPurchaseRequestPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...values,
          }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<ErpPurchaseRequestApi.Request>,
});
</script>
<template>
  <Page auto-content-height
    ><FormModal @success="refresh" /><Grid table-title="采购申请列表"
      ><template #toolbar-tools
        ><TableAction
          :actions="[
            {
              label: '新增采购申请',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:purchase-request:create'],
              onClick: create,
            },
          ]" /></template
      ><template #actions="{ row }"
        ><TableAction
          :actions="[
            {
              label: '编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:purchase-request:update'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              onClick: edit.bind(null, row),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-request:submit'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              popConfirm: {
                title: `确认提交${row.no}？`,
                confirm: submit.bind(null, row),
              },
            },
            {
              label: '转询价',
              type: 'link',
              icon: ACTION_ICON.ADD,
              auth: ['erp:purchase-request:convert'],
              ifShow: () => row.status === 20,
              popConfirm: {
                title: `按${row.no}生成询价单？`,
                confirm: toInquiry.bind(null, row),
              },
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:purchase-request:delete'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              popConfirm: {
                title: `确认删除${row.no}？`,
                confirm: remove.bind(null, row),
              },
            },
          ]" /></template></Grid
  ></Page>
</template>
