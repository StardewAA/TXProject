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
  List,
  ListItem,
  ListItemMeta,
  message,
  Modal,
  Pagination,
  Select,
  SelectOption,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  deliverSupplierOrder,
  getSupplierExpressList,
  getSupplierOrder,
  getSupplierOrderPage,
  getSupplierTradeShops,
} from '#/api/mall/trade/supplier';

const QUERY = 'trade:supplier-order:query';
const UPDATE = 'trade:supplier-order:update';
const orderStatus: Record<number, string> = { 0: '待支付', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消' };
const access = useAccessStore();
const user = useUserStore();
const { hasAccessByCodes } = useAccess();
const shops = ref<SupplierTradeApi.Shop[]>([]);
const shopId = ref<number>();
const rows = ref<SupplierTradeApi.OrderRow[]>([]);
const no = ref('');
const status = ref<number>();
const page = ref(1);
const total = ref(0);
const pendingTotal = ref(0);
const busy = ref(false);
const error = ref('');
const opened = ref(false);
const detail = ref<SupplierTradeApi.OrderDetail>();
const expresses = ref<SupplierTradeApi.Express[]>([]);
const logisticsId = ref<number>();
const logisticsNo = ref('');
let generation = 0;

const crossTenant = computed(() => access.visitTenantId != null && String(access.visitTenantId) !== String(access.tenantId));
const canQuery = computed(() => !!access.accessToken && hasAccessByCodes([QUERY]) && !crossTenant.value);
const canUpdate = computed(() => hasAccessByCodes([UPDATE]) && !crossTenant.value);
const contextKey = computed(() => [access.accessToken, access.tenantId, access.visitTenantId, access.accessCodes.join('|'), user.userInfo?.id].join(':'));
const blocked = computed(() => crossTenant.value ? '店铺订单不支持跨租户代操作，请切回账号所属租户。'
  : !canQuery.value ? `当前账号缺少 ${QUERY} 权限，页面不会读取订单数据。` : '');
const columns = [
  { dataIndex: 'no', key: 'no', title: '订单号', width: 220 },
  { key: 'status', title: '状态', width: 110 },
  { key: 'amount', title: '实付金额', width: 120 },
  { key: 'fulfillment', title: '履约状态', width: 260 },
  { key: 'createTime', title: '下单时间', width: 180 },
  { key: 'action', title: '操作', width: 130 },
];
const money = (value?: number) => (Number(value || 0) / 100).toFixed(2);
const time = (value?: number | string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
const wmsStatusText: Record<string, string> = {
  WMS_OUTBOUND_COMPLETED: '出库完成',
  WMS_OUTBOUND_PENDING: '等待 WMS 出库',
  WMS_OUTBOUND_REJECTED: 'WMS 拒绝 / 执行失败',
  WMS_UNAVAILABLE: 'WMS 暂不可用',
};
const wmsColor = (value?: string) => value === 'WMS_OUTBOUND_COMPLETED' ? 'success'
  : value === 'WMS_OUTBOUND_PENDING' ? 'processing' : 'error';

function clear() {
  generation += 1; shops.value = []; shopId.value = undefined; rows.value = []; total.value = 0; pendingTotal.value = 0;
  opened.value = false; detail.value = undefined; error.value = '';
}
async function loadRows(ticket = generation) {
  if (!canQuery.value || !shopId.value) return;
  const requestedShopId = shopId.value;
  busy.value = true; error.value = '';
  try {
    const [result, pending] = await Promise.all([
      getSupplierOrderPage({ pageNo: page.value, pageSize: 12, shopId: requestedShopId, no: no.value.trim() || undefined, status: status.value }),
      getSupplierOrderPage({ pageNo: 1, pageSize: 1, shopId: requestedShopId, status: 10 }),
    ]);
    if (ticket !== generation || shopId.value !== requestedShopId) return; rows.value = result.list; total.value = result.total; pendingTotal.value = pending.total;
  } catch (cause) {
    if (ticket === generation) { console.error('[supplier-order] page failed', cause); rows.value = []; total.value = 0; pendingTotal.value = 0; error.value = '店铺订单读取失败，请重试。'; }
  } finally { if (ticket === generation) busy.value = false; }
}
async function initialize() {
  clear(); if (!canQuery.value) return; const ticket = generation; busy.value = true;
  try {
    const result = await getSupplierTradeShops(); if (ticket !== generation) return;
    shops.value = result.filter((row) => row.status === 0); shopId.value = shops.value[0]?.id;
    if (!shopId.value) { error.value = '当前账号没有已启用的店铺经营授权。'; return; }
    await loadRows(ticket);
  } catch (cause) { if (ticket === generation) { console.error('[supplier-order] shops failed', cause); error.value = '授权店铺读取失败，请联系企业管理员核对经营范围。'; } }
  finally { if (ticket === generation) busy.value = false; }
}
function filter() { page.value = 1; void loadRows(); }
function focusPending() { status.value = 10; page.value = 1; void loadRows(); }
async function show(row: Record<string, any>) {
  if (!shopId.value || !Number(row.id)) return; busy.value = true;
  try {
    detail.value = await getSupplierOrder(shopId.value, Number(row.id)); logisticsId.value = detail.value.logisticsId; logisticsNo.value = detail.value.logisticsNo || '';
    expresses.value = canUpdate.value && detail.value.deliverable ? await getSupplierExpressList(shopId.value) : [];
    opened.value = true;
  } catch { message.error('订单详情读取失败，请刷新后重试'); }
  finally { busy.value = false; }
}
function deliver() {
  if (!shopId.value || !detail.value?.order.id || !detail.value.deliverable || !canUpdate.value || !logisticsId.value || !/^[A-Za-z0-9-]{5,64}$/.test(logisticsNo.value)) {
    message.warning('请选择快递公司并填写 5 至 64 位字母、数字或短横线运单号'); return;
  }
  Modal.confirm({ title: '确认订单已交付快递？', content: '提交后将进入真实发货状态，并按服务端规则触发 WMS 履约。', async onOk() {
    busy.value = true; try { await deliverSupplierOrder({ id: detail.value!.order.id, logisticsId: logisticsId.value!, logisticsNo: logisticsNo.value, shopId: shopId.value! }); message.success('订单已发货'); opened.value = false; await loadRows(); } finally { busy.value = false; }
  } });
}
watch(contextKey, initialize, { immediate: true });
onUnmounted(() => { generation += 1; });
</script>

<template>
  <Page auto-content-height>
    <Alert class="mb-3" type="info" show-icon message="店铺订单只显示当前后台账号获授权店铺的完整单店订单；混店或归属不完整的历史订单交由平台处理。" />
    <Alert v-if="blocked" class="mb-3" type="warning" show-icon :message="blocked" />
    <Card v-else title="订单中心 · 店铺订单">
      <Alert v-if="pendingTotal > 0" class="mb-3" type="warning" show-icon :message="`有 ${pendingTotal} 笔待发货订单需要处理`">
        <template #action><Button size="small" danger @click="focusPending">立即查看</Button></template>
      </Alert>
      <div class="filters">
        <label>经营店铺<Select v-model:value="shopId" class="control" @change="filter"><SelectOption v-for="shop in shops" :key="shop.id" :value="shop.id">{{ shop.name }}</SelectOption></Select></label>
        <label>订单状态<Select v-model:value="status" allow-clear class="control" placeholder="全部状态" @change="filter"><SelectOption v-for="(label,value) in orderStatus" :key="value" :value="Number(value)">{{ label }}</SelectOption></Select></label>
        <label class="grow">订单号<Input v-model:value="no" allow-clear placeholder="输入订单号" @press-enter="filter" /></label>
        <Button type="primary" :loading="busy" @click="filter">查询</Button>
      </div>
      <Alert v-if="error" class="mb-3" type="error" show-icon :message="error"><template #action><Button size="small" @click="initialize">重试</Button></template></Alert>
      <Table :columns="columns" :data-source="rows" :loading="busy" :pagination="false" row-key="id" :scroll="{x:1100}">
        <template #bodyCell="{column,record}">
          <Tag v-if="column.key === 'status'" :color="record.status === 10 ? 'orange' : record.status === 30 ? 'success' : 'default'">{{ orderStatus[record.status] || '未知状态' }}</Tag>
          <template v-else-if="column.key === 'amount'">¥{{ money(record.payPrice) }}</template>
          <template v-else-if="column.key === 'fulfillment'"><span>{{ record.deliveryBlockReason || (record.deliverable ? '可安排发货' : '按当前状态查看') }}</span><Tag v-if="record.wmsStatus" class="ml-2" :color="wmsColor(record.wmsStatus)">{{ wmsStatusText[record.wmsStatus] || record.wmsStatus }}<template v-if="record.wmsShipmentCount">（{{ record.wmsShipmentCount }} 单）</template></Tag><Tag v-else-if="record.erpStatus" class="ml-2">历史履约 {{ record.erpStatus }}</Tag><div v-if="record.wmsFailureReason" class="wms-error">{{ record.wmsFailureReason }}</div></template>
          <template v-else-if="column.key === 'createTime'">{{ time(record.createTime) }}</template>
          <Button v-else-if="column.key === 'action'" type="link" @click="show(record)">{{ record.deliverable && canUpdate ? '查看 / 发货' : '查看' }}</Button>
        </template>
      </Table>
      <Pagination v-model:current="page" class="pager" :total="total" :page-size="12" :show-size-changer="false" @change="loadRows()" />
    </Card>
    <Drawer v-model:open="opened" title="店铺订单详情" :width="920" destroy-on-close>
      <template v-if="detail">
        <Descriptions bordered :column="2" size="small">
          <DescriptionsItem label="订单号">{{ detail.order.no }}</DescriptionsItem><DescriptionsItem label="状态">{{ orderStatus[detail.order.status] }}</DescriptionsItem>
          <DescriptionsItem label="收货人">{{ detail.receiverName }} / {{ detail.receiverMobile }}</DescriptionsItem><DescriptionsItem label="收货地址">{{ detail.address }}</DescriptionsItem>
          <DescriptionsItem label="买家备注" :span="2">{{ detail.userRemark || '无' }}</DescriptionsItem>
        </Descriptions>
        <List class="mt-4" bordered :data-source="detail.items"><template #renderItem="{item}"><ListItem><ListItemMeta :title="item.name" :description="`${item.specification || '默认规格'} · ${item.count} 件 · 实付 ¥${money(item.payPrice)}`"><template #avatar><Image v-if="item.picUrl" :src="item.picUrl" :width="56" :height="56" /></template></ListItemMeta></ListItem></template></List>
        <section v-if="detail.wmsShipments?.length" class="wms-panel">
          <h3>WMS 出库进度</h3>
          <div v-for="shipment in detail.wmsShipments" :key="shipment.id" class="wms-row">
            <div><Tag :color="wmsColor(shipment.status)">{{ wmsStatusText[shipment.status] || shipment.status }}</Tag><b>{{ shipment.warehouseCode || `仓库 ${shipment.warehouseId || '-'}` }}</b></div>
            <span>商城申请：{{ shipment.sourceOrderNo || '-' }} ｜ WMS 单号：{{ shipment.targetOrderNo || '待受理' }} ｜ 最近同步：{{ time(shipment.lastSyncedAt) }}</span>
            <Alert v-if="shipment.failureReason" type="error" show-icon :message="shipment.failureReason" />
          </div>
        </section>
        <Alert v-if="detail.order.deliveryBlockReason" class="mt-4" type="warning" show-icon :message="detail.order.deliveryBlockReason" />
        <div v-if="detail.deliverable && canUpdate" class="delivery"><Select v-model:value="logisticsId" placeholder="选择快递公司"><SelectOption v-for="item in expresses" :key="item.id" :value="item.id">{{ item.name }}</SelectOption></Select><Input v-model:value="logisticsNo" placeholder="运单号（字母、数字、短横线）" /><Button type="primary" :loading="busy" @click="deliver">确认发货</Button></div>
      </template>
    </Drawer>
  </Page>
</template>
<style scoped>.filters,.delivery{display:flex;flex-wrap:wrap;gap:12px;align-items:end;margin-bottom:16px}.filters label{display:grid;min-width:210px;gap:6px;color:#475569}.filters .grow{flex:1}.control{width:100%}.pager{display:flex;justify-content:flex-end;margin-top:16px}.delivery{margin-top:18px}.delivery>*{min-width:220px}.wms-error{margin-top:5px;color:#b42318;font-size:12px}.wms-panel{margin-top:16px;padding:16px;border:1px solid #d9e2ec;border-radius:6px}.wms-panel h3{margin:0 0 10px}.wms-row{display:grid;gap:7px;padding:10px 0;border-top:1px solid #edf0f3}.wms-row:first-of-type{border-top:0}.wms-row b{margin-left:8px}.wms-row>span{color:#64748b;font-size:12px}</style>
