<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { Page, useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  confirmAward,
  getInquiryPage,
  submitAward,
} from '#/api/erp/purchase/inquiry';
import { inquiryColumns, inquiryFormSchema } from '../inquiry/data';
import Award from './modules/award.vue';
defineOptions({ name: 'ErpAwardManagement' });
const [AwardModal, awardApi] = useVbenModal({
  connectedComponent: Award,
  destroyOnClose: true,
});
const refresh = () => gridApi.query();
async function submit(row: any) {
  await submitAward(row.id);
  message.success('定标结果已提交审批');
  refresh();
}
async function confirm(row: any) {
  const ids = await confirmAward(row.id);
  message.success(`已生成 ${ids.length} 张采购订单`);
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
            awardOnly: true,
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
    ><AwardModal @success="refresh" /><Grid table-title="定标管理"
      ><template #actions="{ row }"
        ><TableAction
          :actions="[
            {
              label: '选择中标报价',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:award:update'],
              ifShow: () => row.businessStatus === 40 && row.awardStatus !== 10,
              onClick: () => awardApi.setData({ id: row.id }).open(),
            },
            {
              label: '提交定标审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:award:submit'],
              ifShow: () => row.businessStatus === 40 && row.awardStatus === 0,
              popConfirm: {
                title: '确认提交定标审批？',
                confirm: submit.bind(null, row),
              },
            },
            {
              label: '生成采购订单',
              type: 'link',
              icon: ACTION_ICON.ADD,
              auth: ['erp:award:confirm'],
              ifShow: () => row.awardStatus === 20 && row.businessStatus === 50,
              popConfirm: {
                title: '将按中标供应商生成采购订单，是否继续？',
                confirm: confirm.bind(null, row),
              },
            },
          ]" /></template></Grid
  ></Page>
</template>
