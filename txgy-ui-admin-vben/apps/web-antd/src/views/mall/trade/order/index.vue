<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MallOrderApi } from '#/api/mall/trade/order';
import type { OrderStatusCounts, WorkbenchSummary } from '#/api/mall/trade/workbench';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { useAccessStore } from '@vben/stores';
import { fenToYuan } from '@vben/utils';

import { Button, Image, List, Tabs, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getOrderPage } from '#/api/mall/trade/order';
import { DictTag } from '#/components/dict-tag';
import { $t } from '#/locales';

import TradeTodoPanel from '../components/TradeTodoPanel.vue';
import { canDeliver, commonFilters, countText, ORDER_STATUS_TABS, pageFilters } from '../components/workbench-model';
import { useGridColumns, useGridFormSchema } from './data';
import DeliveryForm from './modules/delivery-form.vue';
import RemarkForm from './modules/remark-form.vue';

const { push } = useRouter();
const access = useAccessStore();
const currentScope = () => `${access.tenantId}:${access.visitTenantId}:${access.accessToken}`;
const activeStatus = ref('all');
const filters = ref<Record<string, unknown>>({});
const orderIds = ref<number[]>([]);
const todoPanel = ref<InstanceType<typeof TradeTodoPanel>>();
const counts = ref<OrderStatusCounts>();
const countsFailed = ref(false);
const countKeys: Record<string, keyof OrderStatusCounts> = { all: 'allCount', 0: 'unpaidCount', 10: 'undeliveredCount', 20: 'deliveredCount', 30: 'completedCount', 40: 'canceledCount' };
function updateSnapshot(snapshot: WorkbenchSummary | undefined, failed: boolean) {
  counts.value = snapshot?.orderStatusCounts;
  countsFailed.value = failed;
}
async function changeStatus(key: number | string) {
  activeStatus.value = String(key);
  await gridApi.reload();
}

const [DeliveryFormModal, deliveryFormModalApi] = useVbenModal({
  connectedComponent: DeliveryForm,
  destroyOnClose: true,
});

const [RemarkFormModal, remarkFormModalApi] = useVbenModal({
  connectedComponent: RemarkForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
  void todoPanel.value?.refresh();
}

/** 详情 */
async function handleDetail(row: MallOrderApi.Order) {
  const scope = currentScope();
  const events = todoPanel.value?.rowEvents(row.id, 'ORDER') || [];
  if (events.length) {
    try { await todoPanel.value?.markRead(events); } catch { /* 已读失败不妨碍重新鉴权查看真实详情。 */ }
  }
  if (scope !== currentScope()) return;
  push({ name: 'TradeOrderDetail', params: { id: row.id } });
}

/** 发货 */
function handleDelivery(row: MallOrderApi.Order) {
  deliveryFormModalApi.setData(row).open();
}

/** 备注 */
function handleRemark(row: MallOrderApi.Order) {
  remarkFormModalApi.setData(row).open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    expandConfig: {
      trigger: 'row',
      expandAll: true,
      padding: true,
    },
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filters.value = commonFilters(formValues);
          const result = await getOrderPage({
            ...pageFilters(formValues, activeStatus.value),
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
          orderIds.value = result.list.flatMap((row) => row.id ? [row.id] : []);
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
  } as VxeTableGridOptions<MallOrderApi.Order>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【交易】交易订单"
        url="https://doc.iocoder.cn/mall/trade-order/"
      />
      <DocAlert
        title="【交易】购物车"
        url="https://doc.iocoder.cn/mall/trade-cart/"
      />
    </template>

    <DeliveryFormModal @success="handleRefresh" />
    <RemarkFormModal @success="handleRefresh" />
    <TradeTodoPanel ref="todoPanel" :filters="filters" :order-ids="orderIds" @snapshot="updateSnapshot" />
    <Grid table-title="订单列表">
      <template #toolbar-actions>
        <div class="w-full min-w-0">
          <div class="mb-2 flex flex-wrap items-center gap-3">
            <span>当前筛选结果 · 主状态订单数量</span>
            <Button v-access:code="['trade:after-sale:query']" type="link" @click="push('/mall/trade/after-sale')">售后管理（独立事项）</Button>
          </div>
          <Tabs :active-key="activeStatus" @change="changeStatus">
            <Tabs.TabPane v-for="tab in ORDER_STATUS_TABS" :key="tab.key" :tab="`${tab.label} (${countText(counts?.[countKeys[tab.key]!], countsFailed)})`" />
          </Tabs>
        </div>
      </template>
      <template #orderNo="{ row }">
        <div><Tag v-if="todoPanel?.rowEvents(row.id, 'ORDER').length" color="orange">新 · 未读</Tag>{{ row.no }}</div>
      </template>
      <template #processing="{ row }">
        <div>{{ row.deliveryType === 2 ? (row.pickupBlockReason || (row.pickupable ? '可按授权核销' : '查看自提进度')) : (row.deliveryBlockReason || (row.deliverable ? '可安排发货' : '按当前订单状态处理')) }}</div>
        <Tag v-if="row.erpStatus" class="mt-1">ERP {{ row.erpStatus }}</Tag>
      </template>
      <template #expand_content="{ row }">
        <List item-layout="vertical" :data-source="row.items">
          <template #renderItem="{ item }">
            <List.Item>
              <List.Item.Meta>
                <template #title>
                  {{ item.spuName }}
                  <Tag
                    color="blue"
                    v-for="property in item.properties"
                    :key="property.propertyId"
                  >
                    {{ property.propertyName }} : {{ property.valueName }}
                  </Tag>
                </template>
                <template #avatar>
                  <Image :src="item.picUrl" :width="40" :height="40" />
                </template>
                <template #description>
                  {{
                    `原价：${fenToYuan(item.price)} 元 / 数量：${item.count} 个`
                  }}
                  |
                  <DictTag
                    :type="DICT_TYPE.TRADE_ORDER_ITEM_AFTER_SALE_STATUS"
                    :value="item.afterSaleStatus"
                  />
                </template>
              </List.Item.Meta>
            </List.Item>
          </template>
        </List>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['trade:order:query'],
              onClick: handleDetail.bind(null, row),
            },
          ]"
          :drop-down-actions="[
            {
              label: '发货',
              type: 'link',
              auth: ['trade:order:update'],
              ifShow: () => canDeliver(row),
              onClick: handleDelivery.bind(null, row),
            },
            {
              label: '备注',
              type: 'link',
              auth: ['trade:order:update'],
              onClick: handleRemark.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
