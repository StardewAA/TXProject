<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmCustomerApi } from '#/api/crm/customer';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, Modal, Select } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  autoPutCustomerPool,
  distributeCustomer,
  exportCustomer,
  getCustomerPage,
  receiveCustomer,
} from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import ImportForm from './modules/import-form.vue';

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

/** 立即执行一次「客户自动掉入公海」（按公海规则：未成交天数 / 未跟进天数） */
async function handleAutoPutPool() {
  const hideLoading = message.loading({
    content: '正在按公海规则执行自动掉入...',
    duration: 0,
  });
  try {
    const count = await autoPutCustomerPool();
    if (count > 0) {
      message.success(`执行完成：本次有 ${count} 个客户掉入公海`);
    } else {
      message.info('执行完成：没有符合条件的客户需要掉入公海');
    }
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** ==================== 公海领取 / 分配 ==================== */

const distributeVisible = ref(false);
const distributeUserId = ref<number>();
const distributeRow = ref<CrmCustomerApi.Customer>();
const userOptions = ref<{ label: string; value: number }[]>([]);

/** 领取单个客户（领到当前登录用户名下） */
async function handleReceive(row: CrmCustomerApi.Customer) {
  const hideLoading = message.loading({
    content: `正在领取【${row.name}】...`,
    duration: 0,
  });
  try {
    await receiveCustomer([row.id!]);
    message.success(`已领取客户【${row.name}】`);
    handleRefresh();
  } catch (e: any) {
    message.error(
      e?.message ?? '领取失败：请检查是否已达客户数量上限，或该客户已被他人领取',
    );
  } finally {
    hideLoading();
  }
}

/** 批量领取勾选的客户 */
async function handleBatchReceive() {
  const rows = (gridApi.grid?.getCheckboxRecords?.() ??
    []) as CrmCustomerApi.Customer[];
  if (rows.length === 0) {
    message.warning('请先勾选要领取的客户');
    return;
  }
  const hideLoading = message.loading({
    content: `正在领取 ${rows.length} 个客户...`,
    duration: 0,
  });
  try {
    await receiveCustomer(rows.map((r) => r.id!));
    message.success(`已领取 ${rows.length} 个客户`);
    handleRefresh();
  } catch (e: any) {
    message.error(e?.message ?? '领取失败：请检查是否已达客户数量上限');
  } finally {
    hideLoading();
  }
}

/** 打开「分配」弹窗 */
async function openDistribute(row: CrmCustomerApi.Customer) {
  distributeRow.value = row;
  distributeUserId.value = undefined;
  if (userOptions.value.length === 0) {
    try {
      const list: any[] = await getSimpleUserList();
      userOptions.value = (list ?? []).map((u: any) => ({
        label: u.nickname,
        value: u.id,
      }));
    } catch {
      message.error('负责人列表加载失败，请重试');
      return;
    }
  }
  distributeVisible.value = true;
}

/** 确认分配 */
async function handleDistributeOk() {
  if (!distributeUserId.value) {
    message.warning('请选择要分配的负责人');
    return;
  }
  const hideLoading = message.loading({ content: '正在分配...', duration: 0 });
  try {
    await distributeCustomer([distributeRow.value!.id!], distributeUserId.value);
    message.success('分配成功');
    distributeVisible.value = false;
    handleRefresh();
  } catch (e: any) {
    message.error(
      e?.message ?? '分配失败：请检查该负责人是否已达客户数量上限',
    );
  } finally {
    hideLoading();
  }
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
            {
              label: '批量领取',
              type: 'primary',
              auth: ['crm:customer:receive'],
              onClick: handleBatchReceive,
            },
            {
              label: '执行自动掉入',
              type: 'primary',
              auth: ['crm:customer:update'],
              popConfirm: {
                title:
                  '按公海规则（未成交天数 / 未跟进天数）把符合条件的客户放入公海，确认执行？',
                confirm: handleAutoPutPool,
              },
            },
          ]"
        />
      </template>
      <template #name="{ row }">
        <Button type="link" @click="handleDetail(row)">
          {{ row.name }}
        </Button>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '领取',
              type: 'link',
              auth: ['crm:customer:receive'],
              popConfirm: {
                title: `确认领取客户【${row.name}】？领取后归你负责`,
                confirm: handleReceive.bind(null, row),
              },
            },
            {
              label: '分配',
              type: 'link',
              auth: ['crm:customer:distribute'],
              onClick: openDistribute.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
    <ImportModal @success="handleRefresh" />

    <!-- 分配公海客户给指定负责人 -->
    <Modal
      v-model:open="distributeVisible"
      title="分配公海客户"
      @ok="handleDistributeOk"
    >
      <p class="mb-3">将客户【{{ distributeRow?.name }}】分配给：</p>
      <Select
        v-model:value="distributeUserId"
        :options="userOptions"
        :filter-option="
          (input: string, option: any) =>
            String(option?.label ?? '')
              .toLowerCase()
              .includes(String(input).toLowerCase())
        "
        show-search
        placeholder="请选择负责人"
        style="width: 100%"
      />
    </Modal>
  </Page>
</template>
