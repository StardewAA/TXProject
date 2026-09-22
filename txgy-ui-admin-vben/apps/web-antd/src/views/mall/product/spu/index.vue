<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MallSpuApi } from '#/api/mall/product/spu';

import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { confirm, Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Tabs } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { exportSpu, getSpuPage, getSpuReviewPolicy, getTabsCount, submitSpuReview, updateStatus, withdrawSpuReview } from '#/api/mall/product/spu';

import { useGridColumns, useGridFormSchema } from './data';
import { canOperateProduct } from './review-model';

const { push } = useRouter();
const route = useRoute();
const { hasAccessByCodes } = useAccess();
const tabType = ref<number | undefined>();
const policy = ref<MallSpuApi.ReviewPolicy>();
const policyError = ref(false);
const countError = ref(false);
const busyId = ref<number>();
const tabsData = ref([
  { name: '出售中', type: 0, count: undefined as number | undefined },
  { name: '仓库中', type: 1, count: undefined as number | undefined },
  { name: '已售罄', type: 2, count: undefined as number | undefined },
  { name: '警戒库存', type: 3, count: undefined as number | undefined },
  { name: '回收站', type: 4, count: undefined as number | undefined },
]);

async function loadPolicy() {
  policy.value = undefined;
  policyError.value = false;
  try {
    policy.value = await getSpuReviewPolicy();
  } catch {
    policyError.value = true;
  }
}

async function handleExport() {
  const data = await exportSpu({ ...(await gridApi.formApi.getValues()), tabType: tabType.value });
  downloadFileFromBlobPart({ fileName: '商品.xls', source: data });
}

let countRequest = 0;
async function getTabCount(params?: Record<string, unknown>) {
  const request = ++countRequest;
  try {
    const res = await getTabsCount(params ?? await gridApi.formApi.getValues());
    if (request !== countRequest) return;
    for (const tab of tabsData.value) tab.count = res[String(tab.type)];
    countError.value = false;
  } catch {
    if (request === countRequest) countError.value = true;
  }
}

function handleDetail(row: MallSpuApi.Spu) {
  void push({ name: 'ProductSpuDetail', params: { id: row.id } });
}

function handleReview(row: MallSpuApi.Spu) {
  void push({ name: 'ProductSpuReview', params: { id: row.id } });
}

function canMaintain(row: MallSpuApi.Spu) {
  return canOperateProduct(
    policy.value,
    row.shopId,
    hasAccessByCodes(['product:spu:review']),
  );
}

function canCreate() {
  return canOperateProduct(
    policy.value,
    undefined,
    hasAccessByCodes(['product:spu:review']),
  );
}

function canPublish(row: MallSpuApi.Spu) {
  return canMaintain(row) && row.status === 0 && row.reviewStatus === 20
    && row.contentVersion === row.approvedVersion;
}

async function runMaintenance(row: MallSpuApi.Spu, operation: 'submit' | 'withdraw' | number) {
  if (busyId.value !== undefined || !canMaintain(row) || !row.id) return;
  if (!Number.isSafeInteger(row.contentVersion)) {
    message.warning('商品版本缺失，请刷新列表，不能使用旧页面执行操作。');
    return;
  }
  const label = operation === 'submit' ? '提交审核'
    : operation === 'withdraw' ? '撤回审核'
      : operation === 1 ? '上架' : operation === -1 ? '回收' : row.status === -1 ? '恢复为草稿' : '下架';
  busyId.value = row.id;
  try {
    try {
      await confirm(`确认将“${row.name}”${label}？${operation === 'submit' ? '审核通过后仍下架，提交人员不能审核自己的提交。' : ''}`);
    } catch {
      return;
    }
    const expectedVersion = row.contentVersion!;
    if (operation === 'submit') await submitSpuReview({ id: row.id, expectedVersion });
    else if (operation === 'withdraw') {
      if (!row.submissionId) { message.warning('提交编号缺失，请刷新后重试。'); return; }
      await withdrawSpuReview({ id: row.id, expectedVersion, submissionId: row.submissionId });
    } else await updateStatus({ id: row.id, status: operation, expectedVersion });
    message.success(`${label}成功`);
    await gridApi.query();
  } catch {
    message.warning('操作未能确认成功，请刷新核对最新状态；不能使用旧版本继续操作。');
    await gridApi.query();
  } finally {
    busyId.value = undefined;
  }
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
          const result = await getSpuPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            tabType: tabType.value,
          });
          void getTabCount(formValues);
          return result;
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<MallSpuApi.Spu>,
});

function onChangeTab(key: number | string) {
  tabType.value = key === 'all' ? undefined : Number(key);
  void gridApi.query();
}

async function showPendingQueue() {
  tabType.value = undefined;
  await gridApi.formApi.setValues({ reviewStatus: 10 });
  await gridApi.query();
}

onMounted(async () => {
  void loadPolicy();
  if (route.query.categoryId) {
    await gridApi.formApi.setValues({ categoryId: Number(route.query.categoryId) });
  }
  await getTabCount();
});
</script>

<template>
  <Page auto-content-height>
    <Alert class="mb-3" type="info" show-icon message="商品经营与商品审核均在后台完成，并由不同角色分别操作。" description="页面会同时校验后台权限和服务端经营策略；新增、修改、复制、恢复后均须审核，提交人员不可自审。未修改的存量在售商品按历史过渡保留，通过审核不自动上架。" />
    <Alert v-if="policyError" class="mb-3" type="warning" show-icon message="暂无法加载审核策略，页面保持只读，审核入口暂不可用。">
      <template #action><Button size="small" @click="loadPolicy">重试</Button></template>
    </Alert>
    <Grid>
      <template #toolbar-actions>
        <Tabs :active-key="tabType ?? 'all'" @change="onChangeTab" class="w-full">
          <Tabs.TabPane key="all" tab="全部商品" />
          <Tabs.TabPane v-for="item in tabsData" :key="item.type" :tab="`${item.name} (${countError ? '暂不可用' : item.count ?? '…'})`" />
        </Tabs>
      </template>
      <template #toolbar-tools>
        <Button class="mr-2" @click="showPendingQueue">待审队列</Button>
        <TableAction :actions="[
          { label: '新增商品草稿', icon: ACTION_ICON.ADD, auth: ['product:spu:create'], ifShow: canCreate, onClick: () => push({ name: 'ProductSpuAdd' }) },
          { label: '导出', icon: ACTION_ICON.DOWNLOAD, auth: ['product:spu:export'], onClick: handleExport },
        ]" />
      </template>
      <template #actions="{ row }">
        <TableAction :actions="[
          { label: '详情', type: 'link', icon: ACTION_ICON.VIEW, auth: ['product:spu:query'], onClick: handleDetail.bind(null, row) },
          { label: row.reviewStatus === 10 ? '审核详情' : '审核记录', type: 'link', auth: ['product:spu:query'], ifShow: () => policy?.reviewEnabled === true, onClick: handleReview.bind(null, row) },
          { label: '编辑草稿', type: 'link', auth: ['product:spu:update'], ifShow: () => canMaintain(row) && row.reviewStatus !== 10 && row.status !== -1, onClick: () => push({ name: 'ProductSpuEdit', params: { id: row.id } }) },
          { label: '送审', type: 'link', auth: ['product:spu:update'], disabled: busyId !== undefined, ifShow: () => canMaintain(row) && row.status !== -1 && (row.reviewStatus == null || row.reviewStatus === 0 || row.reviewStatus === 30), onClick: () => runMaintenance(row, 'submit') },
          { label: '撤回', type: 'link', auth: ['product:spu:update'], disabled: busyId !== undefined, ifShow: () => canMaintain(row) && row.reviewStatus === 10, onClick: () => runMaintenance(row, 'withdraw') },
          { label: '上架', type: 'link', auth: ['product:spu:update'], disabled: busyId !== undefined, ifShow: () => canPublish(row), onClick: () => runMaintenance(row, 1) },
          { label: '下架', type: 'link', auth: ['product:spu:update'], disabled: busyId !== undefined, ifShow: () => canMaintain(row) && row.status === 1, onClick: () => runMaintenance(row, 0) },
          { label: '回收', type: 'link', auth: ['product:spu:update'], disabled: busyId !== undefined, ifShow: () => canMaintain(row) && row.status === 0 && row.reviewStatus !== 10, onClick: () => runMaintenance(row, -1) },
          { label: '恢复为草稿', type: 'link', auth: ['product:spu:update'], disabled: busyId !== undefined, ifShow: () => canMaintain(row) && row.status === -1, onClick: () => runMaintenance(row, 0) },
        ]" />
      </template>
    </Grid>
  </Page>
</template>
