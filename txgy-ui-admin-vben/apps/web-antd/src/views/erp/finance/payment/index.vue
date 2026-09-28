<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpFinancePaymentApi } from '#/api/erp/finance/payment';

import { ref } from 'vue';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteFinancePayment,
  exportFinancePayment,
  exportFinancePaymentPdf,
  getFinancePaymentPage,
  updateFinancePaymentStatus,
} from '#/api/erp/finance/payment';
import ErpExportButton from '#/components/erp-export-button/index.vue';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

/** ERP 付款单列表 */
defineOptions({ name: 'ErpFinancePayment' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

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
      ? await exportFinancePaymentPdf(params)
      : await exportFinancePayment(params);
  downloadFileFromBlobPart({
    fileName: format === 'pdf' ? '付款单.pdf' : '付款单.xls',
    source: data,
  });
}

/** 导出付款单 PDF */
async function handleExportPdf(row: ErpFinancePaymentApi.FinancePayment) {
  const data = await exportFinancePaymentPdf({ id: row.id });
  downloadFileFromBlobPart({
    fileName: `付款单-${row.no || row.id}.pdf`,
    source: data,
  });
}

/** 新增付款单 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 编辑付款单 */
function handleEdit(row: ErpFinancePaymentApi.FinancePayment) {
  formModalApi.setData({ formType: 'edit', id: row.id }).open();
}

/** 删除付款单 */
async function handleDelete(ids: number[]) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting'),
    duration: 0,
  });
  try {
    await deleteFinancePayment(ids);
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 审批/反审批操作 */
async function handleUpdateStatus(
  row: ErpFinancePaymentApi.FinancePayment,
  status: number,
) {
  const hideLoading = message.loading({
    content: `确定${status === 20 ? '审批' : '反审批'}该付款单吗？`,
    duration: 0,
  });
  try {
    await updateFinancePaymentStatus(row.id!, status);
    message.success(
      status === 20 ? '审批成功，付款已自动出账' : '反审批成功，已生成冲销流水',
    );
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: ErpFinancePaymentApi.FinancePayment[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

/** 查看详情 */
function handleDetail(row: ErpFinancePaymentApi.FinancePayment) {
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
          return await getFinancePaymentPage({
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
  } as VxeTableGridOptions<ErpFinancePaymentApi.FinancePayment>,
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
        title="【财务】采购付款、销售收款"
        url="https://doc.iocoder.cn/sale/finance-payment-receipt/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <Grid table-title="付款单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['付款单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:finance-payment:create'],
              onClick: handleCreate,
            },
            {
              label: '批量删除',
              type: 'primary',
              danger: true,
              disabled: isEmpty(checkedIds),
              icon: ACTION_ICON.DELETE,
              auth: ['erp:finance-payment:delete'],
              popConfirm: {
                title: `是否删除所选中数据？`,
                confirm: handleDelete.bind(null, checkedIds),
              },
            },
          ]"
        />
        <ErpExportButton
          :auth="['erp:finance-payment:export']"
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
              auth: ['erp:finance-payment:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '导出 PDF',
              type: 'link',
              icon: 'lucide:file-down',
              auth: ['erp:finance-payment:export'],
              onClick: handleExportPdf.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:finance-payment:update'],
              ifShow: () => row.status !== 20,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: row.status === 10 ? '审批' : '反审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:finance-payment:update-status'],
              popConfirm: {
                title:
                  row.status === 10
                    ? `确认审批${row.no}吗？审批后将自动扣减账户余额。`
                    : `确认反审批${row.no}吗？系统将自动冲销原付款。`,
                confirm: handleUpdateStatus.bind(
                  null,
                  row,
                  row.status === 10 ? 20 : 10,
                ),
              },
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:finance-payment:delete'],
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
