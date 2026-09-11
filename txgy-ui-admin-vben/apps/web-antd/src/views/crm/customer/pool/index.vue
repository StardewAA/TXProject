<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmCustomerApi } from '#/api/crm/customer';

import { useRouter } from 'vue-router';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { exportCustomer, getCustomerPage } from '#/api/crm/customer';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import ClueQuickParse from '../../clue/modules/ClueQuickParse.vue';
import ImportForm from './modules/import-form.vue';
import type { ParsedClue } from '#/utils/crm/clue-parse';

const { push } = useRouter();

const [ImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportForm,
  destroyOnClose: true,
});

/** 批量导入客户到公海（Excel） */
function handleImport() {
  importModalApi.open();
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportCustomer(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '客户公海.xls', source: data });
}

/** 查看客户详情 */
function handleDetail(row: CrmCustomerApi.Customer) {
  push({ name: 'CrmCustomerDetail', params: { id: row.id } });
}

/** 识别结果回填到公海搜索表单并触发搜索 */
async function onQuickParse(payload: { fields: ParsedClue }) {
  const { fields } = payload;
  const apply: Record<string, any> = {};
  for (const key of [
    'name',
    'mobile',
    'industryId',
    'level',
    'source',
  ] as const) {
    const v = fields[key];
    if (v !== undefined && v !== null && v !== '') {
      apply[key] = v;
    }
  }
  await gridApi.formApi.setValues(apply);
  await gridApi.query();
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
          return await getCustomerPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            pool: true,
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
  } as VxeTableGridOptions<CrmCustomerApi.Customer>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【客户】客户管理、公海客户"
        url="https://doc.iocoder.cn/crm/customer/"
      />
      <DocAlert
        title="【通用】数据权限"
        url="https://doc.iocoder.cn/crm/permission/"
      />
    </template>

    <ClueQuickParse class="mb-4" @apply="onQuickParse" />

    <Grid>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['crm:customer:export'],
              onClick: handleExport,
            },
            {
              label: '批量导入',
              type: 'primary',
              auth: ['crm:customer:import'],
              onClick: handleImport,
            },
          ]"
        />
      </template>
      <template #name="{ row }">
        <Button type="link" @click="handleDetail(row)">
          {{ row.name }}
        </Button>
      </template>
    </Grid>
    <ImportModal @success="handleRefresh" />
  </Page>
</template>
