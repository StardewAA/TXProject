<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpPurchaseOrderApi } from '#/api/erp/purchase/order';

import { ref } from 'vue';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deletePurchaseOrder,
  exportPurchaseOrder,
  exportPurchaseOrderPdf,
  getPurchaseOrderPage,
  reverseApprovePurchaseOrder,
  submitPurchaseOrder,
} from '#/api/erp/purchase/order';
import ErpExportButton from '#/components/erp-export-button/index.vue';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import RecognitionImport from './modules/recognition-import.vue';
import Trace from './modules/trace.vue';

/** ERP 采购订单列表 */
defineOptions({ name: 'ErpPurchaseOrder' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [TraceModal, traceModalApi] = useVbenModal({
  connectedComponent: Trace,
  destroyOnClose: true,
});

/** 查看订单溯源（询价单 → 中标明细 → 订单 → 入库单） */
function handleTrace(row: ErpPurchaseOrderApi.PurchaseOrder) {
  traceModalApi.setData({ id: row.id }).open();
}

const recognitionVisible = ref(false);

/** 图片识别导入 */
function handleImport() {
  recognitionVisible.value = true;
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
type ExportFormat = 'excel' | 'pdf';

async function handleExport(format: ExportFormat) {
  const params = await gridApi.formApi.getValues();
  // 勾选了行则只导出选中单据（后端按 ids 过滤）
  if (checkedIds.value.length > 0) {
    params.ids = checkedIds.value.join(',');
  }
  const data =
    format === 'pdf'
      ? await exportPurchaseOrderPdf(params)
      : await exportPurchaseOrder(params);
  downloadFileFromBlobPart({
    fileName: format === 'pdf' ? '采购订单.pdf' : '采购订单.xlsx',
    source: data,
  });
}

/** 新增采购订单 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 编辑采购订单 */
function handleEdit(row: ErpPurchaseOrderApi.PurchaseOrder) {
  formModalApi.setData({ formType: 'edit', id: row.id }).open();
}

/** 删除采购订单 */
async function handleDelete(ids: number[]) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting'),
    duration: 0,
  });
  try {
    await deletePurchaseOrder(ids);
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 提交到审批流程 */
async function handleSubmit(row: ErpPurchaseOrderApi.PurchaseOrder) {
  const hideLoading = message.loading({
    content: '正在提交审批...',
    duration: 0,
  });
  try {
    await submitPurchaseOrder(row.id!);
    message.success('已提交审批');
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 反审批弹窗 */
const reverseVisible = ref(false);
const reverseLoading = ref(false);
const reverseRow = ref<ErpPurchaseOrderApi.PurchaseOrder>();
const reverseReason = ref('');

function handleReverseApprove(row: ErpPurchaseOrderApi.PurchaseOrder) {
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
    await reverseApprovePurchaseOrder(reverseRow.value!.id!, reverseReason.value.trim());
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
  records: ErpPurchaseOrderApi.PurchaseOrder[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

/** 查看详情 */
function handleDetail(row: ErpPurchaseOrderApi.PurchaseOrder) {
  formModalApi.setData({ formType: 'detail', id: row.id }).open();
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
          return await getPurchaseOrderPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
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
  } as VxeTableGridOptions<ErpPurchaseOrderApi.PurchaseOrder>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【采购】采购订单、入库、退货"
        url="https://doc.iocoder.cn/erp/purchase/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <TraceModal />
    <a-modal
      v-model:open="recognitionVisible"
      title="采购订单导入"
      width="95%"
      :footer="null"
      :destroy-on-close="true"
      :body-style="{ height: 'min(78vh, 760px)', padding: '8px' }"
    >
      <RecognitionImport @success="handleRefresh" />
    </a-modal>
    <a-modal
      v-model:open="reverseVisible"
      title="采购订单反审批"
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
    <Grid table-title="采购订单列表">
      <template #toolbar-tools>
        <div class="flex shrink-0 flex-nowrap items-center gap-2">
          <TableAction
            :actions="[
              {
                label: $t('ui.actionTitle.create', ['采购订单']),
                type: 'primary',
                icon: ACTION_ICON.ADD,
                auth: ['erp:purchase-order:create'],
                onClick: handleCreate,
              },
              {
                label: '导入',
                type: 'primary',
                icon: ACTION_ICON.UPLOAD,
                auth: ['erp:purchase-order:create'],
                onClick: handleImport,
              },
              {
                label: '批量删除',
                type: 'primary',
                danger: true,
                disabled: isEmpty(checkedIds),
                icon: ACTION_ICON.DELETE,
                auth: ['erp:purchase-order:delete'],
                popConfirm: {
                  title: `是否删除所选中数据？`,
                  confirm: handleDelete.bind(null, checkedIds),
                },
              },
            ]"
          />
          <ErpExportButton
            :auth="['erp:purchase-order:export']"
            :export-handler="handleExport"
          />
        </div>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['erp:purchase-order:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '溯源',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['erp:purchase-order:query'],
              onClick: handleTrace.bind(null, row),
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
              auth: ['erp:purchase-order:update'],
              ifShow: () => ![10, 20].includes(row.status ?? 0),
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:approval:submit'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
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
              auth: ['erp:purchase-order:reverse-approve'],
              ifShow: () => [10, 20].includes(row.status ?? 0),
              onClick: handleReverseApprove.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:purchase-order:delete'],
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
