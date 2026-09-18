<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { ref } from 'vue';
import { Page, useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  convertCostResult,
  deleteCostResult,
  getCostResultPage,
  reverseApproveCostResult,
  submitCostResult,
} from '#/api/erp/sale/cost';
import { costColumns, costFormSchema } from './data';
import Convert from './modules/convert.vue';
import Form from './modules/form.vue';
defineOptions({ name: 'ErpCostResult' });
const [FormModal, api] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [ConvertModal, convertApi] = useVbenModal({
  connectedComponent: Convert,
  destroyOnClose: true,
});
/** 转销售订单：后端会真实创建销售订单并回填 convertedId */
function handleConvert(row: any) {
  convertApi.setData({ id: row.id }).open();
}
const refresh = () => gridApi.query();
async function submit(r: any) {
  await submitCostResult(r.id);
  message.success('造价结果已提交审批');
  refresh();
}
async function archive(r: any) {
  await convertCostResult({ id: r.id, convertedType: 4 });
  message.success('造价结果已归档');
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
    await reverseApproveCostResult(reverseRow.value.id, reverseReason.value.trim());
    message.success('已反审批');
    reverseVisible.value = false;
    refresh();
  } finally {
    reverseLoading.value = false;
  }
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
    ><FormModal @success="refresh" />
    <ConvertModal @success="refresh" />
    <a-modal
      v-model:open="reverseVisible"
      title="造价结果反审批"
      :confirm-loading="reverseLoading"
      :destroy-on-close="true"
      :mask-closable="false"
      ok-text="确认反审批"
      cancel-text="取消"
      @ok="confirmReverseApprove"
    >
      <p style="margin-bottom: 8px">
        确定反审批造价结果 <b>{{ reverseRow?.no }}</b>
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
    <Grid table-title="造价管理"
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
              label: '反审批原因',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              ifShow: () => !!row.reverseApproveReason,
              tooltip: row.reverseApproveReason,
            },
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
              label: '转销售订单',
              type: 'link',
              auth: ['erp:cost-result:convert'],
              ifShow: () => row.status === 20 && !row.convertedType,
              onClick: handleConvert.bind(null, row),
            },
            {
              label: '反审批',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:cost-result:reverse-approve'],
              ifShow: () =>
                [10, 20].includes(row.status ?? 0) &&
                (row.businessStatus ?? 0) < 30,
              onClick: handleReverseApprove.bind(null, row),
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
