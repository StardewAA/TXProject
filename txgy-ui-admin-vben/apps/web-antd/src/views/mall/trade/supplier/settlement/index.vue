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
  message,
  Modal,
  Pagination,
  Select,
  SelectOption,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  confirmSupplierSettlement,
  getSupplierSettlement,
  getSupplierSettlementPage,
  getSupplierSettlementProof,
  getSupplierTradeShops,
} from '#/api/mall/trade/supplier';

const QUERY = 'trade:supplier-settlement:query';
const CONFIRM = 'trade:supplier-settlement:confirm';
const statusText: Record<number, string> = {
  10: '待平台审核',
  20: '待店铺确认',
  30: '待平台付款登记',
  40: '已登记线下付款',
  50: '已退回待重算',
  60: '已结转',
};

const access = useAccessStore();
const user = useUserStore();
const { hasAccessByCodes } = useAccess();
const shops = ref<SupplierTradeApi.Shop[]>([]);
const shopId = ref<number>();
const status = ref<number>();
const rows = ref<SupplierTradeApi.SettlementBill[]>([]);
const page = ref(1);
const total = ref(0);
const pendingTotal = ref(0);
const busy = ref(false);
const error = ref('');
const opened = ref(false);
const detail = ref<SupplierTradeApi.SettlementDetail>();
const proof = ref('');
let generation = 0;

const crossTenant = computed(() => access.visitTenantId != null
  && String(access.visitTenantId) !== String(access.tenantId));
const canQuery = computed(() => !!access.accessToken
  && hasAccessByCodes([QUERY])
  && !crossTenant.value);
const canConfirm = computed(() => hasAccessByCodes([CONFIRM]) && !crossTenant.value);
const contextKey = computed(() => [
  access.accessToken,
  access.tenantId,
  access.visitTenantId,
  access.accessCodes.join('|'),
  user.userInfo?.id,
].join(':'));
const blocked = computed(() => crossTenant.value
  ? '店铺结算不支持跨租户代操作，请切回账号所属租户。'
  : !canQuery.value
    ? `当前账号缺少 ${QUERY} 权限，页面不会读取结算数据。`
    : '');
const columns = [
  { dataIndex: 'no', key: 'no', title: '结算单号', width: 210 },
  { key: 'shopMonth', title: '店铺 / 月份', width: 260 },
  { dataIndex: 'orderCount', key: 'orderCount', title: '订单数', width: 100 },
  { key: 'amount', title: '应结金额', width: 130 },
  { key: 'status', title: '状态', width: 150 },
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
  opened.value = false;
  detail.value = undefined;
  proof.value = '';
  error.value = '';
}

async function loadRows(ticket = generation) {
  if (!canQuery.value || !shopId.value) return;
  const requestedShopId = shopId.value;
  busy.value = true;
  error.value = '';
  try {
    const [result, pending] = await Promise.all([
      getSupplierSettlementPage({
        pageNo: page.value,
        pageSize: 12,
        shopId: requestedShopId,
        status: status.value,
      }),
      getSupplierSettlementPage({ pageNo: 1, pageSize: 1, shopId: requestedShopId, status: 20 }),
    ]);
    if (ticket !== generation || shopId.value !== requestedShopId) return;
    rows.value = result.list;
    total.value = result.total;
    pendingTotal.value = pending.total;
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-settlement] page failed', cause);
      rows.value = [];
      total.value = 0;
      pendingTotal.value = 0;
      error.value = '店铺结算读取失败，请重试；空列表不代表没有账单。';
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
      error.value = '当前账号没有已启用的店铺财务授权。';
      return;
    }
    await loadRows(ticket);
  } catch (cause) {
    if (ticket === generation) {
      console.error('[supplier-settlement] shops failed', cause);
      error.value = '授权店铺读取失败，请联系企业管理员核对财务经营范围。';
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
  status.value = 20;
  page.value = 1;
  void loadRows();
}

async function show(row: Record<string, any>) {
  if (!shopId.value || !Number(row.id)) return;
  busy.value = true;
  proof.value = '';
  try {
    detail.value = await getSupplierSettlement(shopId.value, Number(row.id));
    opened.value = true;
  } catch (cause) {
    console.error('[supplier-settlement] detail failed', cause);
    message.error('结算详情读取失败，请刷新后重试');
  } finally {
    busy.value = false;
  }
}

function confirmBill() {
  const bill = detail.value?.bill;
  if (!bill || !shopId.value || bill.status !== 20 || !canConfirm.value) return;
  Modal.confirm({
    title: '确认本版结算账单？',
    content: '确认前请核对订单、退款、佣金和跨期调整。确认仅表示店铺认可账单，不代表平台已经付款。',
    okText: '确认账单',
    cancelText: '返回核对',
    async onOk() {
      busy.value = true;
      try {
        await confirmSupplierSettlement({ id: bill.id, shopId: shopId.value!, version: bill.version });
        message.success('账单已确认，等待平台登记实际付款');
        opened.value = false;
        await loadRows();
      } finally {
        busy.value = false;
      }
    },
  });
}

async function viewProof() {
  const bill = detail.value?.bill;
  if (!bill || !shopId.value) return;
  busy.value = true;
  try {
    proof.value = await getSupplierSettlementProof(shopId.value, bill.id);
    if (!proof.value) message.info('平台尚未登记付款凭证');
  } finally {
    busy.value = false;
  }
}

watch(contextKey, initialize, { immediate: true });
onUnmounted(() => { generation += 1; });
</script>

<template>
  <Page auto-content-height>
    <Alert
      class="mb-3"
      type="info"
      show-icon
      message="店铺财务只查看当前后台账号获授权店铺的正式结算单。"
      description="店铺确认不等于付款；账单生成、平台审核、线下付款登记和余额结转仍由平台财务完成。"
    />
    <Alert v-if="blocked" class="mb-3" type="warning" show-icon :message="blocked" />
    <Card v-else title="订单中心 · 店铺结算">
      <Alert v-if="pendingTotal > 0" class="mb-3" type="warning" show-icon :message="`有 ${pendingTotal} 张平台已审核账单等待店铺确认`">
        <template #action><Button size="small" danger @click="focusPending">立即核对</Button></template>
      </Alert>
      <div class="filters">
        <label>
          经营店铺
          <Select v-model:value="shopId" class="control" @change="filter">
            <SelectOption v-for="shop in shops" :key="shop.id" :value="shop.id">{{ shop.name }}</SelectOption>
          </Select>
        </label>
        <label>
          结算状态
          <Select v-model:value="status" allow-clear class="control" placeholder="全部状态" @change="filter">
            <SelectOption v-for="(label, value) in statusText" :key="value" :value="Number(value)">{{ label }}</SelectOption>
          </Select>
        </label>
        <Button :loading="busy" :disabled="!shopId" @click="loadRows()">刷新</Button>
      </div>
      <Alert v-if="error" class="mb-3" type="error" show-icon :message="error">
        <template #action><Button size="small" @click="initialize">重试</Button></template>
      </Alert>
      <Table
        :columns="columns"
        :data-source="rows"
        :loading="busy"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 1000 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'shopMonth'">{{ record.shopName }} / {{ record.billMonth }}</template>
          <template v-else-if="column.key === 'amount'">¥{{ money(record.settlementAmount) }}</template>
          <Tag v-else-if="column.key === 'status'" :color="record.status === 20 ? 'processing' : record.status === 40 || record.status === 60 ? 'success' : 'default'">
            {{ statusText[record.status] || '未知状态' }}
          </Tag>
          <Button v-else-if="column.key === 'action'" type="link" @click="show(record)">
            {{ record.status === 20 && canConfirm ? '核对 / 确认' : '查看' }}
          </Button>
        </template>
      </Table>
      <Pagination
        v-model:current="page"
        class="pager"
        :total="total"
        :page-size="12"
        :show-size-changer="false"
        @change="loadRows()"
      />
    </Card>

    <Drawer v-model:open="opened" title="店铺结算详情" :width="980" destroy-on-close>
      <template v-if="detail">
        <Descriptions bordered :column="2" size="small">
          <DescriptionsItem label="结算单号">{{ detail.bill.no }}</DescriptionsItem>
          <DescriptionsItem label="账期 / 版本">{{ detail.bill.billMonth }} / 第 {{ detail.bill.version }} 版</DescriptionsItem>
          <DescriptionsItem label="店铺">{{ detail.bill.shopName }}</DescriptionsItem>
          <DescriptionsItem label="状态">{{ statusText[detail.bill.status] }}</DescriptionsItem>
          <DescriptionsItem label="订单实付">¥{{ money(detail.bill.orderPayAmount) }}</DescriptionsItem>
          <DescriptionsItem label="累计退款">¥{{ money(detail.bill.refundAmount) }}</DescriptionsItem>
          <DescriptionsItem label="平台佣金">¥{{ money(detail.bill.commissionAmount) }}</DescriptionsItem>
          <DescriptionsItem label="跨期调整">¥{{ money(detail.bill.adjustmentAmount) }}</DescriptionsItem>
          <DescriptionsItem label="上期抵扣">¥{{ money(detail.bill.carryAmount) }}</DescriptionsItem>
          <DescriptionsItem label="本期应结"><strong>¥{{ money(detail.bill.settlementAmount) }}</strong></DescriptionsItem>
          <DescriptionsItem v-if="detail.bill.auditRemark" label="审核意见" :span="2">{{ detail.bill.auditRemark }}</DescriptionsItem>
          <DescriptionsItem v-if="detail.bill.paymentReference" label="付款登记" :span="2">
            {{ detail.bill.paymentReference }} / {{ time(detail.bill.paymentTime) }}
          </DescriptionsItem>
        </Descriptions>

        <h3 class="section-title">结算明细</h3>
        <Table :data-source="detail.items" :pagination="false" row-key="id" size="small" :scroll="{ x: 900 }">
          <Table.Column title="订单号" data-index="orderNo" :width="210" />
          <Table.Column title="类型" :width="100"><template #default="{ record }">{{ record.kind === 1 ? '订单结算' : '跨期退款' }}</template></Table.Column>
          <Table.Column title="实付" :width="110"><template #default="{ record }">¥{{ money(record.payAmount) }}</template></Table.Column>
          <Table.Column title="运费（已含）" :width="120"><template #default="{ record }">¥{{ money(record.deliveryAmount) }}</template></Table.Column>
          <Table.Column title="累计退款" :width="110"><template #default="{ record }">¥{{ money(record.refundTotal) }}</template></Table.Column>
          <Table.Column title="佣金 / 冲回" :width="120"><template #default="{ record }">¥{{ money(record.commissionAmount) }}</template></Table.Column>
          <Table.Column title="本项应结" :width="110"><template #default="{ record }">¥{{ money(record.amount) }}</template></Table.Column>
        </Table>

        <Alert
          v-if="detail.bill.status === 20"
          class="mt-4"
          type="warning"
          show-icon
          message="请核对本版账单。平台退回重算后版本会变化，旧页面提交会被服务端拒绝。"
        />
        <Button
          v-if="detail.bill.status === 20 && canConfirm"
          class="mt-4"
          type="primary"
          :loading="busy"
          @click="confirmBill"
        >
          确认本版账单
        </Button>
        <Alert
          v-else-if="detail.bill.status === 20"
          class="mt-4"
          type="info"
          show-icon
          :message="`当前账号缺少 ${CONFIRM} 权限，仅可查看账单。`"
        />
        <Button v-if="detail.bill.status === 40" class="mt-4" :loading="busy" @click="viewProof">查看平台付款凭证</Button>
        <Image v-if="proof" class="proof" :src="proof" :width="420" />

        <h3 class="section-title">操作记录</h3>
        <p v-if="!detail.logs.length" class="muted">暂无操作记录</p>
        <p v-for="(log, index) in detail.logs" :key="index" class="log">
          第 {{ log.version }} 版 · {{ log.action }} · {{ time(log.createTime) }} · {{ log.remark || '无备注' }}
        </p>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-bottom: 16px; }
.filters label { display: grid; min-width: 240px; gap: 6px; color: #475569; }
.control { width: 100%; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.section-title { margin: 22px 0 12px; font-weight: 600; }
.muted, .log { color: #64748b; }
.proof { display: block; margin-top: 16px; }
</style>
