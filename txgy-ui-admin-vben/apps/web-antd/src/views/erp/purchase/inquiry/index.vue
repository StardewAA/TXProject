<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpInquiryApi } from '#/api/erp/purchase/inquiry';
import { Page, useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  closeInquiry,
  deleteInquiry,
  getInquiryPage,
  publishInquiry,
  submitInquiry,
} from '#/api/erp/purchase/inquiry';
import { inquiryColumns, inquiryFormSchema } from './data';
import Form from './modules/form.vue';
import Detail from './modules/detail.vue';
defineOptions({ name: 'ErpInquiry' });
const [FormModal, formApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [DetailModal, detailApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});
const refresh = () => gridApi.query();
async function action(fn: any, msg: string, row: any) {
  await fn(row.id);
  message.success(msg);
  refresh();
}
const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: inquiryFormSchema() },
  gridOptions: {
    columns: inquiryColumns(),
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, v) =>
          getInquiryPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...v,
          }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<ErpInquiryApi.Inquiry>,
});
</script>
<template>
  <Page auto-content-height
    ><FormModal @success="refresh" /><DetailModal @success="refresh" /><Grid
      table-title="询价管理（比价在详情内完成）"
      ><template #toolbar-tools
        ><TableAction
          :actions="[
            {
              label: '新增询价单',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:inquiry:create'],
              onClick: () => formApi.setData({}).open(),
            },
          ]" /></template
      ><template #actions="{ row }"
        ><TableAction
          :actions="[
            {
              label: '详情/报价',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['erp:inquiry:query'],
              onClick: () => detailApi.setData({ id: row.id }).open(),
            },
            {
              label: '编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:inquiry:update'],
              ifShow: () => [0, 30].includes(row.status ?? 0),
              onClick: () => formApi.setData({ id: row.id }).open(),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:inquiry:submit'],
              ifShow: () => [0, 30].includes(row.status ?? 0),
              popConfirm: {
                title: '确认提交询价审批？',
                confirm: action.bind(null, submitInquiry, '已提交审批', row),
              },
            },
            {
              label: '发布询价',
              type: 'link',
              auth: ['erp:inquiry:publish'],
              ifShow: () => row.businessStatus === 10,
              popConfirm: {
                title: '确认发布询价？',
                confirm: action.bind(null, publishInquiry, '询价已发布', row),
              },
            },
            {
              label: '截止报价',
              type: 'link',
              auth: ['erp:inquiry:close'],
              ifShow: () => row.businessStatus === 20,
              popConfirm: {
                title: '截止后进入比价定标，是否继续？',
                confirm: action.bind(null, closeInquiry, '已截止报价', row),
              },
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:inquiry:delete'],
              ifShow: () => [0, 30].includes(row.status ?? 0),
              popConfirm: {
                title: '确认删除？',
                confirm: async () => {
                  await deleteInquiry([row.id!]);
                  refresh();
                },
              },
            },
          ]" /></template></Grid
  ></Page>
</template>
