<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { ref } from 'vue';
import { Page, useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  confirmAward,
  getInquiryPage,
  reverseApproveAward,
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
/** 反审批弹窗 */
const reverseVisible = ref(false);
const reverseLoading = ref(false);
const reverseRow = ref<any>();
const reverseReason = ref('');
function handleReverseApprove(row: any) {
  reverseRow.value = row;
  reverseReason.value = '';
  reverseVisible.value = true;
}
/** 确认反审批 */
async function confirmReverseApprove() {
  if (!reverseReason.value.trim()) {
    message.warning('请填写反审批原因');
    return;
  }
  reverseLoading.value = true;
  try {
    await reverseApproveAward(reverseRow.value.id, reverseReason.value.trim());
    message.success('已反审批');
    reverseVisible.value = false;
    refresh();
  } finally {
    reverseLoading.value = false;
  }
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
    ><AwardModal @success="refresh" />
    <a-modal
      v-model:open="reverseVisible"
      title="定标反审批"
      :confirm-loading="reverseLoading"
      :destroy-on-close="true"
      :mask-closable="false"
      ok-text="确认反审批"
      cancel-text="取消"
      @ok="confirmReverseApprove"
    >
      <p style="margin-bottom: 8px">
        确定反审批定标结果 <b>{{ reverseRow?.no }}</b>
        吗？反审批后定标退回未定标状态，审批中的流程实例将同步作废。
      </p>
      <a-textarea
        v-model:value="reverseReason"
        placeholder="请填写反审批原因（必填，将记入审批历史）"
        :rows="3"
        :maxlength="200"
        show-count
      />
    </a-modal>
    <Grid table-title="定标管理"
      ><template #actions="{ row }"
        ><TableAction
          :actions="[
            {
              label: '反审批原因',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              ifShow: () => !!row.awardReverseApproveReason,
              tooltip: row.awardReverseApproveReason,
            },
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
              label: '反审批',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:award:reverse-approve'],
              ifShow: () =>
                [10, 20].includes(row.awardStatus ?? 0) &&
                (row.businessStatus ?? 0) < 60,
              onClick: handleReverseApprove.bind(null, row),
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
