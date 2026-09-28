<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MallPortalTenantApi } from '#/api/mall/product/portal-tenant';

import { Page, useVbenModal } from '@vben/common-ui';

import { Alert } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPortalTenantPage } from '#/api/mall/product/portal-tenant';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleEdit(row: MallPortalTenantApi.PortalTenant) {
  formModalApi.setData(row).open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useGridFormSchema() },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getPortalTenantPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { isHover: true, keyField: 'id' },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<MallPortalTenantApi.PortalTenant>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Alert
      class="mb-3"
      message="此页只管理平台门户的公开名录、路由与展示信息，不会创建或修改租户、企业、店铺和商品数据。"
      show-icon
      type="info"
    />
    <Grid table-title="平台租户目录">
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['product:portal-tenant:update'],
              onClick: handleEdit.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
