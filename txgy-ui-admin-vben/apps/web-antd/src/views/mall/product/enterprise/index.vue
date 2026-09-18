<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MallEnterpriseApi } from '#/api/mall/product/enterprise';

import { useVbenModal, Page } from '@vben/common-ui';
import { CommonStatusEnum } from '@vben/constants';
import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { createTenant, getTenant, getTenantIdByName, updateTenant } from '#/api/system/tenant';
import { getTenantPackageList } from '#/api/system/tenant-package';
import {
  auditEnterprise,
  deleteEnterprise,
  getEnterprisePage,
} from '#/api/mall/product/enterprise';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import MemberApplications from './modules/member-applications.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

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
        query: async ({ page }, values) => {
          return getEnterprisePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...values,
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
  } as VxeTableGridOptions<MallEnterpriseApi.Enterprise>,
});

const defaultTenantExpireTime = new Date('2099-12-31T23:59:59').getTime();

function refresh() {
  gridApi.query();
}

function create() {
  formModalApi.setData(null).open();
}

function edit(row: MallEnterpriseApi.Enterprise) {
  formModalApi.setData(row).open();
}

async function ensureTenantForEnterprise(row: MallEnterpriseApi.Enterprise) {
  if (!row.name) {
    return;
  }

  const tenantId = await getTenantIdByName(row.name);
  const tenantName = row.name;
  const contactName = row.contactName || row.name;
  const contactMobile = row.contactMobile || '';
  const username = row.contactMobile || row.creditCode;

  if (!tenantId) {
    const packages = await getTenantPackageList();
    const packageId =
      packages.find((item) => item.status === CommonStatusEnum.ENABLE)?.id ??
      packages[0]?.id ??
      1;

    await createTenant({
      name: tenantName,
      packageId,
      contactName,
      contactMobile,
      accountCount: 1,
      expireTime: defaultTenantExpireTime as any,
      websites: [],
      status: CommonStatusEnum.ENABLE,
      username,
      password: '123456',
    } as any);
    return;
  }

  const tenant = await getTenant(tenantId);
  const needUpdate =
    tenant.status !== CommonStatusEnum.ENABLE ||
    tenant.name !== tenantName ||
    tenant.contactName !== contactName ||
    tenant.contactMobile !== contactMobile;

  if (!needUpdate) {
    return;
  }

  await updateTenant({
    ...tenant,
    name: tenantName,
    contactName,
    contactMobile,
    status: CommonStatusEnum.ENABLE,
  } as any);
}

async function audit(row: MallEnterpriseApi.Enterprise, status: number) {
  await auditEnterprise(
    row.id!,
    status,
    status === 1 ? '审核通过' : '资料不完整，请补充后重新提交',
  );

  if (status === 1) {
    try {
      await ensureTenantForEnterprise(row);
    } catch (error) {
      console.error('[enterprise][syncTenant] failed', error);
      message.warning('企业已通过，但租户同步失败，请到系统租户中补录');
    }
  }

  message.success('操作成功');
  refresh();
}

async function del(row: MallEnterpriseApi.Enterprise) {
  const h = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteEnterprise(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    refresh();
  } finally {
    h();
  }
}
</script>

<template>
  <Page auto-content-height>
    <MemberApplications />
    <FormModal @success="refresh" />
    <Grid table-title="企业入驻审核">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['企业档案']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['product:enterprise:create'],
              onClick: create,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['product:enterprise:update'],
              onClick: edit.bind(null, row),
            },
            {
              label: '通过',
              type: 'link',
              auth: ['product:enterprise:audit'],
              ifShow: () => row.auditStatus === 0,
              onClick: audit.bind(null, row, 1),
            },
            {
              label: '驳回',
              type: 'link',
              danger: true,
              auth: ['product:enterprise:audit'],
              ifShow: () => row.auditStatus === 0,
              onClick: audit.bind(null, row, 2),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['product:enterprise:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: del.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
