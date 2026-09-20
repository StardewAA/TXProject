<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpSaleOrderApi } from '#/api/erp/sale/order';

import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteSaleOrder,
  exportSaleOrder,
  exportSaleOrderPdf,
  getSaleOrderPage,
  reverseApproveSaleOrder,
  submitSaleOrder,
} from '#/api/erp/sale/order';
import ErpExportButton from '#/components/erp-export-button/index.vue';
import { $t } from '#/locales';

import { assistantDemoData } from '../../home/modules/assistant-demo-data';
import RecognitionImport from '../../purchase/order/modules/recognition-import.vue';
import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import SaleOrderPrint from './modules/print.vue';

/** ERP 销售订单列表 */
defineOptions({ name: 'ErpSaleOrder' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const recognitionVisible = ref(false);
const printRef = ref<InstanceType<typeof SaleOrderPrint>>();
const route = useRoute();

function handleImport() {
  recognitionVisible.value = true;
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport(format: 'excel' | 'pdf') {
  const params = await gridApi.formApi.getValues();
  // 勾选了行则只导出选中单据（后端按 ids 过滤）
  if (checkedIds.value.length > 0) {
    params.ids = checkedIds.value.join(',');
  }
  const data =
    format === 'pdf'
      ? await exportSaleOrderPdf(params)
      : await exportSaleOrder(params);
  downloadFileFromBlobPart({
    fileName: format === 'pdf' ? '销售订单.pdf' : '销售订单.xls',
    source: data,
  });
}

/** 新增销售订单 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 编辑销售订单 */
function handleEdit(row: ErpSaleOrderApi.SaleOrder) {
  formModalApi.setData({ formType: 'edit', id: row.id }).open();
}

/** 删除销售订单 */
async function handleDelete(ids: number[]) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting'),
    duration: 0,
  });
  try {
    await deleteSaleOrder(ids);
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 提交到审批流程 */
async function handleSubmit(row: ErpSaleOrderApi.SaleOrder) {
  const hideLoading = message.loading({
    content: '正在提交审批...',
    duration: 0,
  });
  try {
    await submitSaleOrder(row.id!);
    message.success('已提交审批');
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 反审批弹窗 */
const reverseVisible = ref(false);
const reverseLoading = ref(false);
const reverseRow = ref<ErpSaleOrderApi.SaleOrder>();
const reverseReason = ref('');

function handleReverseApprove(row: ErpSaleOrderApi.SaleOrder) {
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
    await reverseApproveSaleOrder(reverseRow.value!.id!, reverseReason.value.trim());
    message.success('已反审批');
    reverseVisible.value = false;
    handleRefresh();
  } finally {
    reverseLoading.value = false;
  }
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: ErpSaleOrderApi.SaleOrder[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

/** 查看详情 */
function handleDetail(row: ErpSaleOrderApi.SaleOrder) {
  formModalApi.setData({ formType: 'detail', id: row.id }).open();
}

/** 导出单行销售订单 PDF */
function handleExportPdf(row: ErpSaleOrderApi.SaleOrder) {
  printRef.value?.print(row);
}

function getDemoSaleOrderPage(formValues: Record<string, any>) {
  const list = assistantDemoData.sales
    .filter((item) => !formValues.no || item.no.includes(formValues.no))
    .filter((item) => !formValues.customerId || item.customerId === formValues.customerId)
    .filter((item) => formValues.status === undefined || formValues.status === null || item.statusCode === formValues.status)
    .map((item) => ({
      id: item.id,
      no: item.no,
      customerId: item.customerId,
      customerName: item.customer,
      orderTime: item.orderTime,
      totalCount: item.quantity,
      totalPrice: item.amount,
      totalProductPrice: item.amount,
      totalTaxPrice: 0,
      discountPercent: 0,
      discountPrice: 0,
      depositPrice: 0,
      status: item.statusCode,
      productNames: item.product,
      creator: 'demo',
      creatorName: '脱敏演示账号',
      createTime: item.orderTime,
      remark: '脱敏联调订单，仅用于 AI 查询和销售订单页面演示',
      outCount: 0,
      returnCount: 0,
      demo: true,
      items: [{
        id: item.id * 10,
        orderId: item.id,
        productId: item.productId,
        productName: item.product,
        productPrice: item.amount / item.quantity,
        count: item.quantity,
        totalPrice: item.amount,
        taxPercent: 0,
        taxPrice: 0,
        productUnitName: item.unit,
      }],
    })) as ErpSaleOrderApi.SaleOrder[];
  return { list, total: list.length };
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const result = await getSaleOrderPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
          if (result.total === 0) {
            return getDemoSaleOrderPage(formValues);
          }
          return result;
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<ErpSaleOrderApi.SaleOrder>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});

/** AI 查询指定订单时，进入页面后直接打开该订单详情。 */
onMounted(async () => {
  const orderNo = String(route.query.orderNo || '');
  if (!orderNo) return;
  await gridApi.query();
  const row =
    (gridApi.grid.getData() as ErpSaleOrderApi.SaleOrder[]).find(
      (item) => item.no === orderNo,
    ) || getDemoSaleOrderPage({ no: orderNo }).list[0];
  if (row) handleDetail(row);
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【销售】销售订单、出库、退货"
        url="https://doc.iocoder.cn/erp/sale/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <SaleOrderPrint ref="printRef" />
    <a-modal
      v-model:open="recognitionVisible"
      title="销售订单导入"
      width="95%"
      :footer="null"
      :destroy-on-close="true"
      :body-style="{ height: 'min(78vh, 760px)', padding: '8px' }"
    >
      <RecognitionImport
        biz-type="SALE_ORDER"
        title="销售订单图片识别"
        @success="handleRefresh"
      />
    </a-modal>
    <a-modal
      v-model:open="reverseVisible"
      title="销售订单反审批"
      :confirm-loading="reverseLoading"
      :destroy-on-close="true"
      :mask-closable="false"
      ok-text="确认反审批"
      cancel-text="取消"
      @ok="confirmReverseApprove"
    >
      <p style="margin-bottom: 8px">
        确定反审批订单 <b>{{ reverseRow?.no }}</b>
        吗？反审批后订单退回草稿，审批中的流程实例将同步作废。
      </p>
      <a-textarea
        v-model:value="reverseReason"
        placeholder="请填写反审批原因（必填，将记入审批历史）"
        :rows="3"
        :maxlength="200"
        show-count
      />
    </a-modal>
    <Grid table-title="销售订单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['销售订单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:sale-order:create'],
              onClick: handleCreate,
            },
            {
              label: '导入',
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              auth: ['erp:sale-order:create'],
              onClick: handleImport,
            },
            {
              label: '批量删除',
              type: 'primary',
              danger: true,
              disabled: isEmpty(checkedIds),
              icon: ACTION_ICON.DELETE,
              auth: ['erp:sale-order:delete'],
              popConfirm: {
                title: `是否删除所选中数据？`,
                confirm: handleDelete.bind(null, checkedIds),
              },
            },
          ]"
        />
        <ErpExportButton
          :auth="['erp:sale-order:export']"
          :export-handler="handleExport"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['erp:sale-order:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '导出 PDF',
              type: 'link',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['erp:sale-order:query'],
              onClick: handleExportPdf.bind(null, row),
            },
            {
              label: '反审批原因',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              ifShow: () => !!row.reverseApproveReason,
              tooltip: row.reverseApproveReason,
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:sale-order:update'],
              ifShow: () => ![10, 20].includes(row.status),
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              // TODO 联调临时放开权限校验（erp:approval:submit），联调结束后恢复
              ifShow: () => [0, 30, 40].includes(row.status),
              popConfirm: {
                title: `确认将${row.no}提交到审批管理吗？`,
                confirm: handleSubmit.bind(null, row),
              },
            },
            {
              label: '反审批',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:sale-order:reverse-approve'],
              ifShow: () => [10, 20].includes(row.status),
              onClick: handleReverseApprove.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:sale-order:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.no]),
                confirm: handleDelete.bind(null, [row.id!]),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
