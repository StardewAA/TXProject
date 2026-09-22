<script setup lang="ts">
import type { SupplierTradeApi } from '#/api/mall/trade/supplier';

import { computed, onUnmounted, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useAccessStore, useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Image,
  Input,
  message,
  Modal,
  Pagination,
  Select,
  SelectOption,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getSupplierAfterSale,
  getSupplierAfterSalePage,
  getSupplierTradeShops,
  processSupplierAfterSale,
} from '#/api/mall/trade/supplier';

const QUERY = 'trade:supplier-after-sale:query';
const PROCESS = 'trade:supplier-after-sale:process';
const statusText: Record<number, string> = {
  10: '待审核',
  20: '待买家退货',
  30: '待商家收货',
  40: '退款中',
  50: '已完成',
  61: '买家已取消',
  62: '商家已拒绝',
  63: '商家拒绝收货',
};
const wayText: Record<number, string> = { 10: '仅退款', 20: '退货退款' };

const access = useAccessStore();
const user = useUserStore();
const { hasAccessByCodes } = useAccess();
const shops = ref<SupplierTradeApi.Shop[]>([]);
const shopId = ref<number>();
const rows = ref<SupplierTradeApi.AfterSaleRow[]>([]);
const no = ref('');
const status = ref<number>();
const page = ref(1);
const total = ref(0);
const pendingTotal = ref(0);
const pendingAuditTotal = ref(0);
const busy = ref(false);
const error = ref('');
const opened = ref(false);
const detail = ref<SupplierTradeApi.AfterSaleDetail>();
const reason = ref('');
let generation = 0;

const crossTenant = computed(() => access.visitTenantId != null
  && String(access.visitTenantId) !== String(access.tenantId));
const canQuery = computed(() => !!access.accessToken
  && hasAccessByCodes([QUERY])
  && !crossTenant.value);
const canProcess = computed(() => hasAccessByCodes([PROCESS]) && !crossTenant.value);
const contextKey = computed(() => [
  access.accessToken,
  access.tenantId,
  access.visitTenantId,
  access.accessCodes.join('|'),
  user.userInfo?.id,
].join(':'));
const blocked = computed(() => crossTenant.value
  ? '售后退款不支持跨租户代操作，请切回账号所属租户。'
  : !canQuery.value
    ? `当前账号缺少 ${QUERY} 权限，页面不会读取售后数据。`
    : '');
const columns = [
  { dataIndex: 'no', key: 'no', title: '售后单号', width: 210 },
  { key: 'order', title: '订单 / 商品', width: 270 },
  { key: 'status', title: '状态', width: 160 },
  { key: 'refund', title: '退款金额', width: 120 },
  { key: 'processing', title: '处理提示', width: 250 },
  { key: 'time', title: '申请时间', width: 180 },
  { key: 'action', title: '操作', width: 130 },
];
const money = (value?: number) => (Number(value || 0) / 100).toFixed(2);
const time = (value?: number | string) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '-';

function clear() {
  generation += 1;
  shops.value = [];
  shopId.value = undefined;
  rows.value = [];
  total.value = 0;
  pendingTotal.value = 0;
  pendingAuditTotal.value = 0;
  opened.value = false;
  detail.value = undefined;
  error.value = '';
}

async function loadRows(ticket = generation) {
  if (!canQuery.value || !shopId.value) return;
  const requestedShopId = shopId.value;
  busy.value = true;
  error.value = '';
  try {
    const [result, audit, receive] = await Promise.all([
      getSupplierAfterSalePage({
        no: no.value.trim() || undefined,
        pageNo: page.value,
        pageSize: 12,
        shopId: requestedShopId,
        status: status.value,
      }),
      getSupplierAfterSalePage({ pageNo: 1, pageSize: 1, shopId: requestedShopId, status: 10 }),
      getSupplierAfterSalePage({ pageNo: 1, pageSize: 1, shopId: requestedShopId, status: 30 }),
    ]);
    if (ticket !== generation || shopId.value !== requestedShopId) return;
    rows.value = result.list;
    total.value = result.total;
    pendingTotal.value = audit.total + receive.total;
    pendingAuditTotal.value = audit.total;
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-after-sale] page failed', cause);
      rows.value = [];
      total.value = 0;
      pendingTotal.value = 0;
      pendingAuditTotal.value = 0;
      error.value = '售后退款读取失败，请重试。';
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
    const result = await getSupplierTradeShops();
    if (ticket !== generation) return;
    shops.value = result.filter((row) => row.status === 0);
    shopId.value = shops.value[0]?.id;
    if (!shopId.value) {
      error.value = '当前账号没有已启用的店铺经营授权。';
      return;
    }
    await loadRows(ticket);
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-after-sale] shops failed', cause);
      error.value = '授权店铺读取失败，请联系企业管理员核对经营范围。';
    }
  } finally {
    if (ticket === generation) busy.value = false;
  }
}

function filter() {
  page.value = 1;
  void loadRows();
}

function focusPending() {
  status.value = pendingAuditTotal.value > 0 ? 10 : 30;
  page.value = 1;
  void loadRows();
}

async function show(row: Record<string, any>) {
  if (!shopId.value || !Number(row.id)) return;
  busy.value = true;
  try {
    detail.value = await getSupplierAfterSale(shopId.value, Number(row.id));
    reason.value = '';
    opened.value = true;
  } catch (cause) {
    console.error('[supplier-after-sale] detail failed', cause);
    message.error('售后详情读取失败，请刷新后重试');
  } finally {
    busy.value = false;
  }
}

function actionLabel(action: SupplierTradeApi.AfterSaleAction) {
  return {
    AGREE: '同意售后',
    RECEIVE: '确认退货收货',
    REFUSE: '拒绝退货收货',
    REJECT: '拒绝售后',
  }[action];
}

function process(action: SupplierTradeApi.AfterSaleAction) {
  const row = detail.value?.row;
  if (!row || !shopId.value || !canProcess.value || !row.processable) return;
  const reject = action === 'REJECT' || action === 'REFUSE';
  if (reject && !reason.value.trim()) {
    message.warning('请填写拒绝原因');
    return;
  }
  Modal.confirm({
    title: `确认${actionLabel(action)}？`,
    content: action === 'AGREE' && row.way === 20
      ? '系统将使用已维护的店铺退货地址；未配置时服务端会拒绝。'
      : '业务状态以服务端处理结果为准，不会在前端伪造退款或入库。',
    async onOk() {
      busy.value = true;
      try {
        await processSupplierAfterSale({
          action,
          id: row.id,
          reason: reason.value.trim() || undefined,
          shopId: shopId.value!,
        });
        message.success('售后状态已更新');
        opened.value = false;
        await loadRows();
      } finally {
        busy.value = false;
      }
    },
  });
}

watch(contextKey, initialize, { immediate: true });
onUnmounted(() => { generation += 1; });
</script>

<template>
  <Page auto-content-height>
    <Alert class="mb-3" type="info" show-icon message="售后退款只显示当前账号获授权店铺的申请；同意退货不会自动退款，退款和 WMS 入库按各自真实状态流转。" />
    <Alert v-if="blocked" class="mb-3" type="warning" show-icon :message="blocked" />
    <Card v-else title="订单中心 · 售后退款">
      <Alert v-if="pendingTotal > 0" class="mb-3" type="warning" show-icon :message="`有 ${pendingTotal} 笔售后申请等待处理或确认退货`">
        <template #action><Button size="small" danger @click="focusPending">{{ pendingAuditTotal > 0 ? '先看待审核' : '查看待收货' }}</Button></template>
      </Alert>
      <div class="filters">
        <label>经营店铺<Select v-model:value="shopId" class="control" @change="filter"><SelectOption v-for="shop in shops" :key="shop.id" :value="shop.id">{{ shop.name }}</SelectOption></Select></label>
        <label>售后状态<Select v-model:value="status" allow-clear class="control" placeholder="全部状态" @change="filter"><SelectOption v-for="(label, value) in statusText" :key="value" :value="Number(value)">{{ label }}</SelectOption></Select></label>
        <label class="grow">订单号<Input v-model:value="no" allow-clear placeholder="输入订单号" @press-enter="filter" /></label>
        <Button type="primary" :loading="busy" @click="filter">查询</Button>
      </div>
      <Alert v-if="error" class="mb-3" type="error" show-icon :message="error"><template #action><Button size="small" @click="initialize">重试</Button></template></Alert>
      <Table :columns="columns" :data-source="rows" :loading="busy" :pagination="false" row-key="id" :scroll="{ x: 1200 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'order'"><strong>{{ record.orderNo }}</strong><p class="muted">{{ record.spuName }} × {{ record.count }}</p></template>
          <Tag v-else-if="column.key === 'status'">{{ statusText[record.status] || '未知状态' }} · {{ wayText[record.way] || '未知方式' }}</Tag>
          <template v-else-if="column.key === 'refund'">¥{{ money(record.refundPrice) }}</template>
          <template v-else-if="column.key === 'processing'">{{ record.processBlockReason || (record.processable ? '可按权限处理' : '查看处理进度') }}<Tag v-if="record.erpStatus" class="ml-2">历史履约 {{ record.erpStatus }}</Tag></template>
          <template v-else-if="column.key === 'time'">{{ time(record.createTime) }}</template>
          <Button v-else-if="column.key === 'action'" type="link" @click="show(record)">{{ record.processable && canProcess ? '查看 / 处理' : '查看' }}</Button>
        </template>
      </Table>
      <Pagination v-model:current="page" class="pager" :total="total" :page-size="12" :show-size-changer="false" @change="loadRows()" />
    </Card>

    <Drawer v-model:open="opened" title="售后退款详情" :width="820" destroy-on-close>
      <template v-if="detail">
        <Descriptions bordered :column="2" size="small">
          <DescriptionsItem label="售后单">{{ detail.row.no }}</DescriptionsItem>
          <DescriptionsItem label="订单号">{{ detail.row.orderNo }}</DescriptionsItem>
          <DescriptionsItem label="商品">{{ detail.row.spuName }} × {{ detail.row.count }}</DescriptionsItem>
          <DescriptionsItem label="申请金额">¥{{ money(detail.row.refundPrice) }}</DescriptionsItem>
          <DescriptionsItem label="申请原因">{{ detail.row.applyReason || '-' }}</DescriptionsItem>
          <DescriptionsItem label="申请说明">{{ detail.row.applyDescription || '-' }}</DescriptionsItem>
          <DescriptionsItem v-if="detail.returnAddress" label="退货地址" :span="2">{{ detail.returnReceiverName }} / {{ detail.returnReceiverMobile }} / {{ detail.returnAddress }}</DescriptionsItem>
        </Descriptions>
        <div v-if="detail.pictures?.length" class="pictures"><Image v-for="url in detail.pictures" :key="url" :src="url" :width="100" :height="100" /></div>
        <Alert v-if="detail.row.processBlockReason" class="mt-4" type="warning" show-icon :message="detail.row.processBlockReason" />
        <template v-if="detail.row.processable && canProcess">
          <Input.TextArea v-model:value="reason" class="mt-4" :maxlength="200" show-count placeholder="拒绝操作必须填写原因；同意时可选填处理说明" />
          <Space class="mt-4">
            <Button v-if="detail.row.status === 10" type="primary" :loading="busy" @click="process('AGREE')">同意售后</Button>
            <Button v-if="detail.row.status === 10" danger :loading="busy" @click="process('REJECT')">拒绝售后</Button>
            <Button v-if="detail.row.status === 30 && detail.row.way === 20" type="primary" :loading="busy" @click="process('RECEIVE')">确认退货收货</Button>
            <Button v-if="detail.row.status === 30 && detail.row.way === 20" danger :loading="busy" @click="process('REFUSE')">拒绝退货收货</Button>
          </Space>
        </template>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-bottom: 16px; }
.filters label { display: grid; min-width: 210px; gap: 6px; color: #475569; }
.filters .grow { flex: 1; }
.control { width: 100%; }
.muted { margin: 4px 0 0; color: #64748b; font-size: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.pictures { display: flex; gap: 10px; margin-top: 16px; }
</style>
