<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MallShopApi } from '#/api/mall/product/shop';
import { Page, useVbenModal } from '@vben/common-ui';
import { ElMessage } from 'element-plus';
import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteShop, getShopList } from '#/api/mall/product/shop';
import { $t } from '#/locales';
import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
const [FormModal, formModalApi] = useVbenModal({ connectedComponent: Form, destroyOnClose: true });
function handleRefresh() { gridApi.query(); }
function handleCreate() { formModalApi.setData(null).open(); }
function handleEdit(row: MallShopApi.Shop) { formModalApi.setData(row).open(); }
async function handleDelete(row: MallShopApi.Shop) {
  await deleteShop(row.id!);
  ElMessage.success($t('ui.actionMessage.deleteSuccess', [row.name]));
  handleRefresh();
}
const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useGridFormSchema() },
  gridOptions: {
    columns: useGridColumns(), height: 'auto', keepSource: true,
    proxyConfig: { ajax: { query: async (_page, formValues) => {
      const items = await getShopList();
      const filtered = items.filter((item) => (!formValues.name || item.name?.includes(formValues.name)) && (!formValues.enterpriseId || item.enterpriseId === formValues.enterpriseId) && (formValues.status === undefined || formValues.status === null || formValues.status === '' || item.status === formValues.status));
      return { list: filtered, total: filtered.length };
    } } },
    rowConfig: { keyField: 'id', isHover: true }, toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<MallShopApi.Shop>,
});
</script>
<template><Page auto-content-height><FormModal @success="handleRefresh" /><Grid table-title="企业店铺列表"><template #toolbar-tools><TableAction :actions="[{ label: $t('ui.actionTitle.create', ['企业店铺']), type: 'primary', icon: ACTION_ICON.ADD, auth: ['product:shop:create'], onClick: handleCreate } ]" /></template><template #actions="{ row }"><TableAction :actions="[{ label: $t('common.edit'), type: 'link', icon: ACTION_ICON.EDIT, auth: ['product:shop:update'], onClick: handleEdit.bind(null, row) }, { label: $t('common.delete'), type: 'link', danger: true, icon: ACTION_ICON.DELETE, auth: ['product:shop:delete'], popConfirm: { title: $t('ui.actionMessage.deleteConfirm', [row.name]), confirm: handleDelete.bind(null, row) } }]" /></template></Grid></Page></template>
