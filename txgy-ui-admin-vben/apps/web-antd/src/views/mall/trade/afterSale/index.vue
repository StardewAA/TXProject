<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MallAfterSaleApi } from '#/api/mall/trade/afterSale';

import { onDeactivated, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { DocAlert, Page } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { Button, Image, Tabs, Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAfterSaleCount, getAfterSalePage } from '#/api/mall/trade/afterSale';

import TradeTodoPanel from '../components/TradeTodoPanel.vue';
import { commonFilters, countText, pageFilters } from '../components/workbench-model';

import { useGridColumns, useGridFormSchema } from './data';

const { push } = useRouter();
const access = useAccessStore();
const currentScope = () => `${access.tenantId}:${access.visitTenantId}:${access.accessToken}`;
const todoPanel = ref<InstanceType<typeof TradeTodoPanel>>();
const afterSaleIds = ref<number[]>([]);
const filters = ref<Record<string, unknown>>({});
const counts = ref<Record<string, number>>();
const countsFailed = ref(false);
let countVersion = 0;
let countController: AbortController | undefined;
async function refreshCounts() {
  countController?.abort();
  countController = new AbortController();
  const version = ++countVersion;
  try {
    const data = await getAfterSaleCount(commonFilters(filters.value), countController.signal);
    if (version !== countVersion) return;
    counts.value = data;
    countsFailed.value = false;
  } catch { if (version === countVersion) countsFailed.value = true; }
}
function onSnapshot(_snapshot: unknown, failed: boolean) {
  if (!_snapshot) { cancelCounts(); counts.value = undefined; countsFailed.value = false; return; }
  if (failed) countsFailed.value = true;
  else void refreshCounts();
}
function cancelCounts() { countVersion++; countController?.abort(); }
onDeactivated(cancelCounts);
onUnmounted(cancelCounts);

const statusTabs = ref([
  {
    label: '全部',
    value: 'all',
  },
]);
const statusTab = ref(statusTabs.value[0]!.value);

/** 处理退款 */
async function handleOpenAfterSaleDetail(row: MallAfterSaleApi.AfterSale) {
  const scope = currentScope();
  const entries = todoPanel.value?.rowEvents(row.id, 'AFTER_SALE') || [];
  if (entries.length) {
    try { await todoPanel.value?.markRead(entries); } catch { /* 已读失败保留提醒，仍允许查看真实详情。 */ }
  }
  if (scope !== currentScope()) return;
  push({ name: 'TradeAfterSaleDetail', params: { id: row.id } });
}

/** 查看订单详情 */
function handleOpenOrderDetail(row: MallAfterSaleApi.AfterSale) {
  push({ name: 'TradeOrderDetail', params: { id: row.orderId } });
}

/** 切换售后状态 */
function handleChangeStatus(key: number | string) {
  statusTab.value = key.toString();
  gridApi.reload();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    cellConfig: {
      height: 60,
    },
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filters.value = commonFilters(formValues);
          void refreshCounts();
          const result = await getAfterSalePage({
            ...pageFilters(formValues, statusTab.value),
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
          afterSaleIds.value = result.list.flatMap((row) => row.id ? [row.id] : []);
          return result;
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
  } as VxeTableGridOptions<MallAfterSaleApi.AfterSale>,
});

/** 初始化 */
onMounted(() => {
  for (const dict of getDictOptions(DICT_TYPE.TRADE_AFTER_SALE_STATUS)) {
    statusTabs.value.push({
      label: dict.label,
      value: dict.value.toString(),
    });
  }
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【交易】售后退款"
        url="https://doc.iocoder.cn/mall/trade-aftersale/"
      />
    </template>

    <TradeTodoPanel ref="todoPanel" :after-sale-ids="afterSaleIds" @snapshot="onSnapshot" />
    <Grid>
      <template #toolbar-actions>
        <div class="w-full min-w-0">
        <div class="mb-2">当前筛选售后数量（独立于主订单总数）<span v-if="countsFailed"> · 暂无法更新统计，请刷新重试</span></div>
        <Tabs
          v-model:active-key="statusTab"
          class="w-full"
          @change="handleChangeStatus"
        >
          <Tabs.TabPane
            v-for="tab in statusTabs"
            :key="tab.value"
            :tab="`${tab.label} (${countText(counts?.[tab.value], countsFailed)})`"
          />
        </Tabs>
        </div>
      </template>
      <template #afterSaleNo="{ row }">
        <Tag v-if="todoPanel?.rowEvents(row.id, 'AFTER_SALE').length" color="orange">新 · 未读</Tag>{{ row.no }}
      </template>
      <template #processing="{ row }">
        <div>{{ row.processBlockReason || (row.processable ? '可按授权处理' : '查看当前售后进度') }}</div>
        <Tag v-if="row.erpStatus" class="mt-1">历史履约 {{ row.erpStatus }}</Tag>
      </template>
      <template #orderNo="{ row }">
        <Button type="link" @click="handleOpenOrderDetail(row)">
          {{ row.orderNo }}
        </Button>
      </template>
      <template #productInfo="{ row }">
        <div class="flex items-start gap-2 text-left">
          <Image
            v-if="row.picUrl"
            :src="row.picUrl"
            :width="40"
            :height="40"
            :preview="{ src: row.picUrl }"
          />
          <div class="flex flex-1 flex-col gap-1">
            <span class="text-sm">{{ row.spuName }}</span>
            <div class="mt-1 flex flex-wrap gap-1">
              <Tag
                v-for="property in row.properties"
                :key="property.propertyId!"
                size="small"
                color="blue"
              >
                {{ property.propertyName }}: {{ property.valueName }}
              </Tag>
            </div>
          </div>
        </div>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看 / 处理',
              type: 'link',
              auth: ['trade:after-sale:query'],
              onClick: handleOpenAfterSaleDetail.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
