<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { Page } from '@vben/common-ui';
import { Alert, Button, Card, Descriptions, DescriptionsItem, Modal, Spin, Table, Tag, message } from 'ant-design-vue';
import type { ConsultOrder } from '#/api/cms/expert-consult';
import { listConsultOrders } from '#/api/cms/expert-consult';

defineOptions({ name: 'CmsExpertOrder' });
const loading = ref(false);
const rows = ref<ConsultOrder[]>([]);
const detail = ref<ConsultOrder>();
const detailOpen = ref(false);
const statusNames = ['待支付', '待接单', '已接单', '咨询中', '待确认', '已完成', '已取消', '已过期', '已拒单', '退款中', '已退款', '纠纷中'];
const statusColors = ['orange', 'blue', 'cyan', 'processing', 'gold', 'green', 'default', 'default', 'red', 'orange', 'default', 'red'];
const columns = [
  { dataIndex: 'orderNo', title: '订单编号', width: 210 },
  { dataIndex: 'expertName', title: '服务专家', width: 120 },
  { dataIndex: 'serviceSubject', title: '咨询主题' },
  { dataIndex: 'appointmentTime', title: '预约时间', width: 170 },
  { key: 'price', title: '金额', width: 100 },
  { key: 'status', title: '状态', width: 100 },
  { dataIndex: 'createTime', title: '下单时间', width: 170 },
  { key: 'action', title: '操作', width: 90 },
];
const completed = computed(() => rows.value.filter((row) => row.status === 5));
async function load() {
  loading.value = true;
  try { rows.value = await listConsultOrders(); }
  catch { message.error('咨询订单读取失败'); }
  finally { loading.value = false; }
}
function show(row: Record<string, any>) { detail.value = row as ConsultOrder; detailOpen.value = true; }
onMounted(load);
</script>

<template>
  <Page><div class="order-page">
    <header><div><p>CMS 专家管理</p><h1>咨询订单管理</h1></div><Button @click="load">刷新</Button></header>
    <div class="stats">
      <Card><strong>{{ rows.length }}</strong><span>全部订单</span></Card>
      <Card><strong>{{ rows.filter((row) => [2, 3, 4].includes(row.status)).length }}</strong><span>服务中</span></Card>
      <Card><strong>{{ rows.filter((row) => row.status === 9).length }}</strong><span>退款处理中</span></Card>
      <Card><strong>¥ {{ (completed.reduce((sum, row) => sum + row.price, 0) / 100).toFixed(2) }}</strong><span>已完成金额</span></Card>
    </div>
    <Alert message="支付单与 IM 双人群由整合模块回填；本页显示真实业务订单状态，不再使用模拟数据。" show-icon type="info" />
    <Spin :spinning="loading"><Table :columns="columns" :data-source="rows" row-key="id" :pagination="{ pageSize: 10 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'price'">¥ {{ (record.price / 100).toFixed(2) }}</template>
        <template v-else-if="column.key === 'status'"><Tag :color="statusColors[record.status]">{{ statusNames[record.status] }}</Tag></template>
        <template v-else-if="column.key === 'action'"><Button type="link" @click="show(record)">详情</Button></template>
      </template>
    </Table></Spin>
    <Modal v-model:open="detailOpen" title="咨询订单详情" :footer="null" width="760px">
      <Descriptions v-if="detail" bordered :column="2">
        <DescriptionsItem label="订单编号">{{ detail.orderNo }}</DescriptionsItem><DescriptionsItem label="状态">{{ statusNames[detail.status] }}</DescriptionsItem>
        <DescriptionsItem label="专家">{{ detail.expertName }}</DescriptionsItem><DescriptionsItem label="价格">¥ {{ (detail.price / 100).toFixed(2) }}</DescriptionsItem>
        <DescriptionsItem label="预约时间" :span="2">{{ detail.appointmentTime }}</DescriptionsItem>
        <DescriptionsItem label="支付单">{{ detail.payOrderId || '等待支付模块回填' }}</DescriptionsItem><DescriptionsItem label="IM 群">{{ detail.imGroupId || '等待 IM 模块建群' }}</DescriptionsItem>
        <DescriptionsItem label="咨询问题" :span="2">{{ detail.question }}</DescriptionsItem><DescriptionsItem v-if="detail.cancelReason" label="关闭原因" :span="2">{{ detail.cancelReason }}</DescriptionsItem>
      </Descriptions>
    </Modal>
  </div></Page>
</template>

<style scoped>
.order-page{display:grid;gap:16px}.order-page>header{display:flex;align-items:flex-end;justify-content:space-between;padding:24px 28px;background:linear-gradient(130deg,#14243f,#2c4470);border-radius:22px;color:#fff}.order-page header p{margin:0 0 5px;color:#dfc48a}.order-page h1{margin:0;font-size:26px}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.stats :deep(.ant-card-body){display:flex;flex-direction:column}.stats strong{font-size:23px;color:#1a3153}.stats span{color:#7d8ba0}@media(max-width:900px){.stats{grid-template-columns:repeat(2,1fr)}}
</style>
