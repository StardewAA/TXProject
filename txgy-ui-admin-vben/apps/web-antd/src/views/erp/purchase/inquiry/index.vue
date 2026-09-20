<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpInquiryApi } from '#/api/erp/purchase/inquiry';
import { ref } from 'vue';
import { Page, useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  closeInquiry,
  deleteInquiry,
  getInquiryPage,
  publishInquiry,
  reverseApproveInquiry,
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
/** 反审批弹窗 */
const reverseVisible = ref(false);
const reverseLoading = ref(false);
const reverseRow = ref<ErpInquiryApi.Inquiry>();
const reverseReason = ref('');
function handleReverseApprove(row: ErpInquiryApi.Inquiry) {
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
    await reverseApproveInquiry(reverseRow.value!.id!, reverseReason.value.trim());
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
    ><FormModal @success="refresh" /><DetailModal @success="refresh" />
    <a-modal
      v-model:open="reverseVisible"
      title="询价单反审批"
      :confirm-loading="reverseLoading"
      :destroy-on-close="true"
      :mask-closable="false"
      ok-text="确认反审批"
      cancel-text="取消"
      @ok="confirmReverseApprove"
    >
      <p style="margin-bottom: 8px">
        确定反审批询价单 <b>{{ reverseRow?.no }}</b>
        吗？反审批后单据退回草稿，审批中的流程实例将同步作废。
      </p>
      <a-textarea
        v-model:value="reverseReason"
        placeholder="请填写反审批原因（必填，将记入审批历史）"
        :rows="3"
        :maxlength="200"
        show-count
      />
    </a-modal>
    <Grid
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
              label: '反审批原因',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              ifShow: () => !!row.reverseApproveReason || !!row.awardReverseApproveReason,
              tooltip: row.reverseApproveReason || row.awardReverseApproveReason,
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
              label: '反审批',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:inquiry:reverse-approve'],
              ifShow: () =>
                [10, 20].includes(row.status ?? 0) &&
                (row.businessStatus ?? 0) < 20,
              onClick: handleReverseApprove.bind(null, row),
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
