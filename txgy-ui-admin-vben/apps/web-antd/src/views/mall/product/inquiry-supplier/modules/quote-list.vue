<script setup lang="ts">
import type { SupplierInquiryApi } from '#/api/mall/product/inquiry-supplier';

import { computed, onUnmounted, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore, useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Pagination,
  Select,
  SelectOption,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getQuotePage,
  getSupplierShops,
} from '#/api/mall/product/inquiry-supplier';

import {
  INQUIRY_MESSAGE_PERMISSION,
  INQUIRY_QUERY_PERMISSION,
  money,
  positiveId,
  quoteStatus,
} from '../model';
import QuoteWorkbench from './quote-workbench.vue';

const props = withDefaults(defineProps<{ communicationOnly?: boolean }>(), {
  communicationOnly: false,
});

const access = useAccessStore();
const user = useUserStore();
const { hasAccessByCodes } = useAccess();
const shops = ref<SupplierInquiryApi.Shop[]>([]);
const rows = ref<SupplierInquiryApi.Quote[]>([]);
const shopId = ref<number>();
const status = ref<number>();
const page = ref(1);
const pageSize = ref(12);
const total = ref(0);
const busy = ref(false);
const error = ref('');
const workbenchOpen = ref(false);
const selectedQuoteId = ref<number>();
let generation = 0;

const crossTenant = computed(() => access.visitTenantId != null
  && String(access.visitTenantId) !== String(access.tenantId));
const canQuery = computed(() => !!access.accessToken
  && positiveId(access.tenantId)
  && hasAccessByCodes([INQUIRY_QUERY_PERMISSION])
  && !crossTenant.value);
const canMessage = computed(() => hasAccessByCodes([INQUIRY_MESSAGE_PERMISSION])
  && !crossTenant.value);
const contextKey = computed(() => [
  access.accessToken,
  access.tenantId,
  access.visitTenantId,
  access.accessCodes.join('|'),
  user.userInfo?.id,
].join(':'));
const blocked = computed(() => crossTenant.value
  ? '供应经营不支持跨租户代操作，请切回后台账号所属租户。'
  : !canQuery.value
    ? `当前账号缺少 ${INQUIRY_QUERY_PERMISSION} 权限，页面不会读取报价数据。`
    : props.communicationOnly && !canMessage.value
      ? `当前账号缺少 ${INQUIRY_MESSAGE_PERMISSION} 权限，页面不会读取沟通记录。`
      : '');

const columns = [
  { key: 'inquiry', title: '询价单', width: 310 },
  { dataIndex: 'shopName', key: 'shopName', title: '报价店铺', width: 180 },
  { key: 'amount', title: '报价金额', width: 130 },
  { key: 'status', title: '状态', width: 110 },
  { key: 'time', title: '更新时间', width: 180 },
  { key: 'action', title: '操作', width: 150 },
];

function formatTime(value?: number | string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
}

function clear() {
  generation += 1;
  shops.value = [];
  rows.value = [];
  total.value = 0;
  shopId.value = undefined;
  selectedQuoteId.value = undefined;
  workbenchOpen.value = false;
  error.value = '';
}

async function loadRows(ticket = generation) {
  if (!canQuery.value || !shopId.value || (props.communicationOnly && !canMessage.value)) return;
  const requestedShopId = shopId.value;
  busy.value = true;
  error.value = '';
  try {
    const result = await getQuotePage({
      pageNo: page.value,
      pageSize: pageSize.value,
      shopId: requestedShopId,
      status: status.value,
    });
    if (ticket !== generation || shopId.value !== requestedShopId) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-inquiry] quote page failed', cause);
      rows.value = [];
      total.value = 0;
      error.value = '报价列表读取失败，请重试；空列表不代表没有报价。';
    }
  } finally {
    if (ticket === generation) busy.value = false;
  }
}

async function initialize() {
  clear();
  if (!canQuery.value || (props.communicationOnly && !canMessage.value)) return;
  const ticket = generation;
  busy.value = true;
  try {
    const result = await getSupplierShops();
    if (ticket !== generation) return;
    shops.value = result.filter((row) => row.status === 0 && positiveId(row.id));
    shopId.value = shops.value[0]?.id;
    if (!shopId.value) {
      error.value = '当前后台账号没有已启用的企业店铺授权，不能查看报价。';
      return;
    }
    await loadRows(ticket);
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-inquiry] authorized shops failed', cause);
      error.value = '授权店铺读取失败，请联系企业管理员核对后台账号与店铺授权。';
    }
  } finally {
    if (ticket === generation) busy.value = false;
  }
}

function changeFilter() {
  page.value = 1;
  rows.value = [];
  total.value = 0;
  void loadRows();
}

function open(row: Record<string, any>) {
  if (!Number(row.id)) return;
  selectedQuoteId.value = Number(row.id);
  workbenchOpen.value = true;
}

watch(contextKey, initialize, { immediate: true });
onUnmounted(() => { generation += 1; });
</script>

<template>
  <Alert v-if="blocked" class="mb-3" show-icon type="warning" :message="blocked" />
  <Card v-else :title="communicationOnly ? '销售管理 · 报价沟通' : '销售管理 · 我的报价'" :loading="busy && !shops.length">
    <template #extra>
      <span class="scope-note">只显示当前后台账号的授权店铺范围</span>
    </template>
    <div class="filters">
      <label>
        报价店铺
        <Select v-model:value="shopId" class="control" :disabled="busy" @change="changeFilter">
          <SelectOption v-for="shop in shops" :key="shop.id" :value="shop.id">{{ shop.name }}</SelectOption>
        </Select>
      </label>
      <label>
        报价状态
        <Select v-model:value="status" allow-clear class="control" :disabled="busy" placeholder="全部状态" @change="changeFilter">
          <SelectOption v-for="(label, value) in quoteStatus" :key="value" :value="Number(value)">{{ label }}</SelectOption>
        </Select>
      </label>
      <Button :loading="busy" :disabled="!shopId" @click="loadRows()">刷新</Button>
    </div>
    <Alert v-if="communicationOnly" class="mb-3" show-icon type="info" message="沟通记录严格绑定报价与店铺；查看报价权限不会自动获得发送消息权限。" />
    <Alert v-if="error" class="mb-3" show-icon type="error" :message="error">
      <template #action><Button size="small" @click="initialize">重新加载</Button></template>
    </Alert>
    <Table
      :columns="columns"
      :data-source="rows"
      :loading="busy"
      :pagination="false"
      row-key="id"
      :scroll="{ x: 1060 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'inquiry'">
          <strong>{{ record.inquiryTitle || '企业采购询价' }}</strong>
          <p class="muted">{{ record.inquiryNo || `询价 #${record.inquiryId}` }}</p>
        </template>
        <template v-else-if="column.key === 'amount'">¥{{ money(record.totalPrice) }}</template>
        <template v-else-if="column.key === 'status'">
          <Tag :color="record.status === 2 ? 'success' : record.status === 1 ? 'processing' : 'default'">{{ quoteStatus[record.status] || '未知状态' }}</Tag>
        </template>
        <template v-else-if="column.key === 'time'">{{ formatTime(record.submittedAt || record.updateTime || record.createTime) }}</template>
        <template v-else-if="column.key === 'action'">
          <Button type="link" @click="open(record)">{{ communicationOnly && record.canMessage && canMessage ? '进入沟通' : communicationOnly ? '查看报价' : '查看 / 处理' }}</Button>
        </template>
      </template>
    </Table>
    <div class="pager">
      <Pagination
        v-model:current="page"
        v-model:page-size="pageSize"
        :total="total"
        :show-size-changer="false"
        show-less-items
        @change="loadRows()"
      />
    </div>
  </Card>
  <QuoteWorkbench
    v-model:open="workbenchOpen"
    :communication-only="communicationOnly"
    :quote-id="selectedQuoteId"
    :shop-id="shopId"
    @saved="loadRows()"
  />
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-bottom: 16px; }
.filters label { display: grid; min-width: 240px; gap: 6px; color: #475569; }
.control { width: 100%; }
.scope-note, .muted { color: #64748b; font-size: 12px; }
.muted { margin: 4px 0 0; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
