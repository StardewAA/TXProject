<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateTypeApi } from '#/api/hrm/certificate/type';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteCertificateType,
  getCertificateTypePage,
  updateCertificateType,
} from '#/api/hrm/certificate/type';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'HrmCertificateType' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新增 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑 */
function handleEdit(row: HrmCertificateTypeApi.CertificateType) {
  formModalApi.setData(row).open();
}

/** 删除 */
async function handleDelete(row: HrmCertificateTypeApi.CertificateType) {
  await confirm(`确认删除证件类型“${row.name}”吗？已被证件台账使用的类型不能删除。`);
  await deleteCertificateType(row.id!);
  message.success($t('ui.actionMessage.operationSuccess'));
  handleRefresh();
}

/** 状态切换 */
async function handleStatusChange(
  newStatus: number,
  row: HrmCertificateTypeApi.CertificateType,
): Promise<boolean | undefined> {
  try {
    await confirm(
      `确认要${getDictLabel(DICT_TYPE.COMMON_STATUS, newStatus)}证件类型“${row.name}”吗？`,
    );
  } catch {
    return false;
  }
  await updateCertificateType({ ...row, status: newStatus });
  message.success($t('ui.actionMessage.operationSuccess'));
  return true;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(handleStatusChange),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getCertificateTypePage({
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
  } as VxeTableGridOptions<HrmCertificateTypeApi.CertificateType>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />

    <Grid>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:certificate:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #specialOperation="{ row }">
        <Tag :color="row.specialOperation ? 'error' : 'default'">
          {{ row.specialOperation ? '是' : '否' }}
        </Tag>
      </template>
      <template #needReview="{ row }">
        <Tag :color="row.needReview ? 'warning' : 'default'">
          {{ row.needReview ? '是' : '否' }}
        </Tag>
      </template>
      <template #requireAttachment="{ row }">
        <Tag :color="row.requireAttachment ? 'processing' : 'default'">
          {{ row.requireAttachment ? '是' : '否' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:certificate:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['hrm:certificate:delete'],
              onClick: handleDelete.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
