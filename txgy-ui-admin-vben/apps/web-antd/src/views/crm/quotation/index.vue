<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmQuotationApi } from '#/api/crm/quotation';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, Tabs } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  createContractByQuotation,
  deleteQuotation,
  exportQuotation,
  getQuotationPage,
  submitQuotation,
} from '#/api/crm/quotation';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const { push } = useRouter();
const sceneType = ref('1');

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 处理场景类型的切换 */
function handleChangeSceneType(key: number | string) {
  sceneType.value = key.toString();
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const formValues = await gridApi.formApi.getValues();
  const data = await exportQuotation({
    sceneType: sceneType.value,
    ...formValues,
  });
  downloadFileFromBlobPart({ fileName: '报价单.xls', source: data });
}

/** 创建报价单 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑报价单 */
function handleEdit(row: CrmQuotationApi.Quotation) {
  formModalApi.setData(row).open();
}

/** 删除报价单 */
async function handleDelete(row: CrmQuotationApi.Quotation) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteQuotation(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 提交审批 */
async function handleSubmit(row: CrmQuotationApi.Quotation) {
  const hideLoading = message.loading({
    content: '提交审批中...',
    duration: 0,
  });
  try {
    await submitQuotation(row.id!);
    message.success('提交审批成功');
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 转为合同 */
async function handleCreateContract(row: CrmQuotationApi.Quotation) {
  const hideLoading = message.loading({
    content: '生成合同中...',
    duration: 0,
  });
  try {
    const contractId = await createContractByQuotation(row.id!);
    message.success('转为合同成功');
    push({ name: 'CrmContractDetail', params: { id: contractId } });
  } finally {
    hideLoading();
  }
}

/** 查看报价单详情 */
function handleDetail(row: CrmQuotationApi.Quotation) {
  push({ name: 'CrmQuotationDetail', params: { id: row.id } });
}

/** 查看客户详情 */
function handleCustomerDetail(row: CrmQuotationApi.Quotation) {
  push({ name: 'CrmCustomerDetail', params: { id: row.customerId } });
}

/** 查看商机详情 */
function handleBusinessDetail(row: CrmQuotationApi.Quotation) {
  if (!row.businessId) {
    return;
  }
  push({ name: 'CrmBusinessDetail', params: { id: row.businessId } });
}

/** 查看审批详情 */
function handleProcessDetail(row: CrmQuotationApi.Quotation) {
  push({
    name: 'BpmProcessInstanceDetail',
    query: { id: row.processInstanceId },
  });
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
          return await getQuotationPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            sceneType: sceneType.value,
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
  } as VxeTableGridOptions<CrmQuotationApi.Quotation>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid>
      <template #toolbar-actions>
        <Tabs class="w-full" @change="handleChangeSceneType">
          <Tabs.TabPane tab="我负责的" key="1" />
          <Tabs.TabPane tab="我参与的" key="2" />
          <Tabs.TabPane tab="下属负责的" key="3" />
        </Tabs>
      </template>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['报价单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:quotation:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['crm:quotation:export'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #name="{ row }">
        <Button type="link" @click="handleDetail(row)">
          {{ row.name }}
        </Button>
      </template>
      <template #customerName="{ row }">
        <Button type="link" @click="handleCustomerDetail(row)">
          {{ row.customerName }}
        </Button>
      </template>
      <template #businessName="{ row }">
        <Button type="link" @click="handleBusinessDetail(row)">
          {{ row.businessName }}
        </Button>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:quotation:update'],
              onClick: handleEdit.bind(null, row),
              ifShow: row.auditStatus === 0,
            },
            {
              label: '提交审批',
              type: 'link',
              auth: ['crm:quotation:update'],
              onClick: handleSubmit.bind(null, row),
              ifShow: row.auditStatus === 0,
            },
            {
              label: '转为合同',
              type: 'link',
              auth: ['crm:quotation:update'],
              popConfirm: {
                title: '确认将报价单【' + row.name + '】转为合同？',
                confirm: handleCreateContract.bind(null, row),
              },
              ifShow: row.auditStatus === 20 && !row.contractId,
            },
            {
              label: '查看审批',
              type: 'link',
              auth: ['crm:quotation:update'],
              onClick: handleProcessDetail.bind(null, row),
              ifShow: row.auditStatus !== 0 && row.processInstanceId,
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              auth: ['crm:quotation:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
