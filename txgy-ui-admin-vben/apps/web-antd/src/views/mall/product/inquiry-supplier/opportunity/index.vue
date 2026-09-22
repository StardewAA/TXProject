<script setup lang="ts">
import type { SupplierInquiryApi } from '#/api/mall/product/inquiry-supplier';

import { computed, onUnmounted, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useAccessStore, useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Input,
  Pagination,
  Select,
  SelectOption,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getOpportunityPage,
  getSupplierShops,
} from '#/api/mall/product/inquiry-supplier';

import {
  INQUIRY_QUERY_PERMISSION,
  INQUIRY_QUOTE_PERMISSION,
  inquiryStatus,
  positiveId,
} from '../model';
import QuoteWorkbench from '../modules/quote-workbench.vue';

const access = useAccessStore();
const user = useUserStore();
const { hasAccessByCodes } = useAccess();
const shops = ref<SupplierInquiryApi.Shop[]>([]);
const rows = ref<SupplierInquiryApi.Summary[]>([]);
const shopId = ref<number>();
const keyword = ref('');
const state = ref('open');
const page = ref(1);
const pageSize = ref(12);
const total = ref(0);
const busy = ref(false);
const error = ref('');
const workbenchOpen = ref(false);
const selectedInquiryId = ref<number>();
let generation = 0;

const crossTenant = computed(() => access.visitTenantId != null
  && String(access.visitTenantId) !== String(access.tenantId));
const canQuery = computed(() => !!access.accessToken
  && positiveId(access.tenantId)
  && hasAccessByCodes([INQUIRY_QUERY_PERMISSION])
  && !crossTenant.value);
const canQuote = computed(() => hasAccessByCodes([INQUIRY_QUOTE_PERMISSION])
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
    ? `当前账号缺少 ${INQUIRY_QUERY_PERMISSION} 权限，页面不会读取询价和店铺数据。`
    : '');

const columns = [
  { key: 'demand', title: '采购需求', width: 330 },
  { dataIndex: 'enterpriseName', key: 'enterpriseName', title: '采购企业', width: 190 },
  { dataIndex: 'deliveryArea', key: 'deliveryArea', title: '交付区域', width: 150 },
  { key: 'deadline', title: '报价截止', width: 180 },
  { key: 'status', title: '状态', width: 100 },
  { key: 'action', title: '操作', width: 130 },
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
  selectedInquiryId.value = undefined;
  workbenchOpen.value = false;
  error.value = '';
}

async function loadRows(ticket = generation) {
  if (!canQuery.value || !shopId.value) return;
  const requestedShopId = shopId.value;
  busy.value = true;
  error.value = '';
  try {
    const result = await getOpportunityPage({
      keyword: keyword.value.trim() || undefined,
      pageNo: page.value,
      pageSize: pageSize.value,
      shopId: requestedShopId,
      state: state.value || undefined,
    });
    if (ticket !== generation || shopId.value !== requestedShopId) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-inquiry] opportunity page failed', cause);
      rows.value = [];
      total.value = 0;
      error.value = '询价机会读取失败，请重试；空列表不代表没有采购需求。';
    }
  } finally {
    if (ticket === generation) busy.value = false;
  }
}

async function initialize() {
  clear();
  if (!canQuery.value) return;
  const ticket = generation;
  busy.value = true;
  try {
    const result = await getSupplierShops();
    if (ticket !== generation) return;
    shops.value = result.filter((row) => row.status === 0 && positiveId(row.id));
    shopId.value = shops.value[0]?.id;
    if (!shopId.value) {
      error.value = '当前后台账号没有已启用的企业店铺授权，不能查看或响应询价。';
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

function search() {
  page.value = 1;
  void loadRows();
}

function selectShop() {
  page.value = 1;
  rows.value = [];
  total.value = 0;
  void loadRows();
}

function open(row: Record<string, any>) {
  selectedInquiryId.value = Number(row.id);
  workbenchOpen.value = true;
}

watch(contextKey, initialize, { immediate: true });
onUnmounted(() => { generation += 1; });
</script>

<template>
  <Page auto-content-height>
    <Alert
      class="mb-3"
      show-icon
      type="info"
      message="询价机会按当前账号的真实企业和店铺授权展示，包含本租户机会及品类、配送范围匹配的平台公开机会。"
      description="平台公开不等于开放其他租户后台数据；跨租户机会还会按本店已审核商品的运费模板核对交付地区，服务端继续复核双方租户、企业、店铺和报价权限，联系方式保持脱敏。"
    />
    <Alert v-if="blocked" class="mb-3" show-icon type="warning" :message="blocked" />
    <Card v-else title="销售管理 · 询价机会" :loading="busy && !shops.length">
      <div class="filters">
        <label>
          报价店铺
          <Select v-model:value="shopId" class="control" :disabled="busy" @change="selectShop">
            <SelectOption v-for="shop in shops" :key="shop.id" :value="shop.id">{{ shop.name }}</SelectOption>
          </Select>
        </label>
        <label>
          询价范围
          <Select v-model:value="state" class="control" :disabled="busy" @change="search">
            <SelectOption value="open">仍在报价期</SelectOption>
            <SelectOption value="all">全部可见记录</SelectOption>
          </Select>
        </label>
        <label class="keyword">
          标题或物料
          <Input v-model:value="keyword" allow-clear placeholder="输入询价标题或物料关键字" @press-enter="search" />
        </label>
        <Button type="primary" :loading="busy" :disabled="!shopId" @click="search">查询</Button>
      </div>
      <Alert v-if="error" class="mb-3" show-icon type="error" :message="error">
        <template #action><Button size="small" @click="initialize">重新加载</Button></template>
      </Alert>
      <Table
        :columns="columns"
        :data-source="rows"
        :loading="busy"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 1080 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'demand'">
            <strong>{{ record.title }}</strong>
            <p class="muted">{{ record.no || `询价 #${record.id}` }} · {{ record.itemSummary || `${record.itemCount || 0} 项物料` }}</p>
            <Tag v-if="record.visibility === 'PLATFORM_PUBLIC'" color="blue">平台公开</Tag>
            <Tag v-else-if="record.visibility === 'TENANT_PUBLIC'" color="green">租户公开</Tag>
            <Tag v-else>定向邀请</Tag>
          </template>
          <template v-else-if="column.key === 'deadline'">{{ formatTime(record.deadline) }}</template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status === 1 ? 'processing' : record.status === 4 ? 'success' : 'default'">{{ inquiryStatus[record.status] || '未知状态' }}</Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Button type="link" @click="open(record)">{{ record.canQuote && canQuote ? '查看 / 报价' : '查看' }}</Button>
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
      :inquiry-id="selectedInquiryId"
      :shop-id="shopId"
      @saved="loadRows()"
    />
  </Page>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-bottom: 16px; }
.filters label { display: grid; min-width: 210px; gap: 6px; color: #475569; }
.filters .keyword { min-width: 320px; flex: 1; }
.control { width: 100%; }
.muted { margin: 4px 0 0; color: #64748b; font-size: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
