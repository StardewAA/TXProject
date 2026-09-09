<script lang="ts" setup>
import type { TableColumnsType } from 'ant-design-vue';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  Descriptions,
  DescriptionsItem,
  Empty,
  Input,
  message,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getPurchaseOrder,
  getPurchaseOrderPage,
  submitPurchaseOrder,
} from '#/api/erp/purchase/order';
import {
  getSaleOrder,
  getSaleOrderPage,
  submitSaleOrder,
} from '#/api/erp/sale/order';
import {
  getPurchaseRequest,
  getPurchaseRequestPage,
  submitPurchaseRequest,
} from '#/api/erp/purchase/request';
import {
  getInquiry,
  getInquiryPage,
  submitAward,
  submitInquiry,
} from '#/api/erp/purchase/inquiry';
import {
  getCostResult,
  getCostResultPage,
  submitCostResult,
} from '#/api/erp/sale/cost';
import BpmProcessInstanceTaskList from '#/views/bpm/processInstance/detail/modules/task-list.vue';

defineOptions({ name: 'ErpApprovalManagement' });

type OrderType =
  | 'PURCHASE'
  | 'SALE'
  | 'PURCHASE_REQUEST'
  | 'INQUIRY'
  | 'AWARD'
  | 'COST_RESULT';

interface ApprovalOrder {
  id: number;
  key: string;
  orderType: OrderType;
  no: string;
  partnerName?: string;
  productNames?: string;
  creatorName?: string;
  orderTime?: Date | string;
  createTime?: Date | string;
  totalCount?: number;
  totalPrice?: number;
  status: number;
  processInstanceId?: string;
  remark?: string;
  items?: any[];
}

const router = useRouter();
const route = useRoute();
const loading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const allOrders = ref<ApprovalOrder[]>([]);
const selectedOrder = ref<ApprovalOrder>();
const keyword = ref('');
const orderType = ref<OrderType | undefined>();
const status = ref<number | undefined>();
const currentPage = ref(1);
const pageSize = ref(10);

const statusMeta: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  10: { color: 'processing', text: '审批中' },
  20: { color: 'success', text: '审批通过' },
  30: { color: 'error', text: '审批驳回' },
  40: { color: 'warning', text: '已撤销' },
};

const filteredOrders = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return allOrders.value.filter((item) => {
    const matchesKeyword =
      !value ||
      [item.no, item.partnerName, item.productNames, item.creatorName].some(
        (field) => field?.toLowerCase().includes(value),
      );
    return (
      matchesKeyword &&
      (!orderType.value || item.orderType === orderType.value) &&
      (status.value === undefined || item.status === status.value)
    );
  });
});

const pagedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredOrders.value.slice(start, start + pageSize.value);
});

const statistics = computed(() => ({
  total: allOrders.value.length,
  process: allOrders.value.filter((item) => item.status === 10).length,
  approve: allOrders.value.filter((item) => item.status === 20).length,
  pending: allOrders.value.filter((item) => [0, 30, 40].includes(item.status))
    .length,
}));

const columns: TableColumnsType = [
  { title: '订单类型', dataIndex: 'orderType', key: 'orderType', width: 100 },
  { title: '订单编号', dataIndex: 'no', key: 'no', width: 180 },
  {
    title: '供应商/客户',
    dataIndex: 'partnerName',
    key: 'partnerName',
    width: 190,
  },
  {
    title: '产品',
    dataIndex: 'productNames',
    key: 'productNames',
    ellipsis: true,
  },
  { title: '发起人', dataIndex: 'creatorName', key: 'creatorName', width: 110 },
  {
    title: '订单金额',
    dataIndex: 'totalPrice',
    key: 'totalPrice',
    align: 'right',
    width: 130,
  },
  { title: '审批状态', dataIndex: 'status', key: 'status', width: 120 },
  { title: '下单时间', dataIndex: 'orderTime', key: 'orderTime', width: 180 },
  { title: '操作', key: 'actions', fixed: 'right', width: 190 },
];

function normalizePurchase(item: any): ApprovalOrder {
  return {
    ...item,
    id: item.id,
    key: `PURCHASE-${item.id}`,
    orderType: 'PURCHASE',
    partnerName: item.supplierName ?? item.partnerName,
  };
}

function normalizeSale(item: any): ApprovalOrder {
  return {
    ...item,
    id: item.id,
    key: `SALE-${item.id}`,
    orderType: 'SALE',
    partnerName: item.customerName ?? item.partnerName,
  };
}

function normalizeBusiness(item: any, type: OrderType): ApprovalOrder {
  const status = type === 'AWARD' ? item.awardStatus : item.status;
  const processInstanceId =
    type === 'AWARD' ? item.awardProcessInstanceId : item.processInstanceId;
  return {
    ...item,
    id: item.id,
    key: `${type}-${item.id}`,
    orderType: type,
    no: item.no,
    partnerName:
      type === 'PURCHASE_REQUEST' ? item.purpose : item.title || item.name,
    orderTime:
      item.requestTime || item.deadline || item.receivedAt || item.createTime,
    status,
    processInstanceId,
  };
}

async function loadOrders() {
  loading.value = true;
  try {
    const [purchasePage, salePage, requestPage, inquiryPage, costPage] =
      await Promise.all([
        getPurchaseOrderPage({ pageNo: 1, pageSize: 200 }),
        getSaleOrderPage({ pageNo: 1, pageSize: 200 }),
        getPurchaseRequestPage({ pageNo: 1, pageSize: 200 }),
        getInquiryPage({ pageNo: 1, pageSize: 200 }),
        getCostResultPage({ pageNo: 1, pageSize: 200 }),
      ]);
    allOrders.value = [
      ...(purchasePage.list || []).map(normalizePurchase),
      ...(salePage.list || []).map(normalizeSale),
      ...(requestPage.list || []).map((item) =>
        normalizeBusiness(item, 'PURCHASE_REQUEST'),
      ),
      ...(inquiryPage.list || []).map((item) =>
        normalizeBusiness(item, 'INQUIRY'),
      ),
      ...(inquiryPage.list || [])
        .filter((item) => (item.businessStatus ?? 0) >= 40)
        .map((item) => normalizeBusiness(item, 'AWARD')),
      ...(costPage.list || []).map((item) =>
        normalizeBusiness(item, 'COST_RESULT'),
      ),
    ].sort(
      (a, b) =>
        new Date(b.createTime || 0).getTime() -
        new Date(a.createTime || 0).getTime(),
    );
    if (!selectedOrder.value && allOrders.value.length > 0) {
      const targetOrderNo = String(route.query.orderNo || '');
      const targetOrderType = String(route.query.orderType || '');
      const targetOrder = allOrders.value.find(
        (item) =>
          item.no === targetOrderNo &&
          (!targetOrderType || item.orderType === targetOrderType),
      );
      await selectOrder(targetOrder || allOrders.value[0]!);
    }
  } finally {
    loading.value = false;
  }
}

async function selectOrder(order: ApprovalOrder | Record<string, any>) {
  const approvalOrder = order as ApprovalOrder;
  detailLoading.value = true;
  try {
    const getters: Record<OrderType, (id: number) => Promise<any>> = {
      PURCHASE: getPurchaseOrder,
      SALE: getSaleOrder,
      PURCHASE_REQUEST: getPurchaseRequest,
      INQUIRY: getInquiry,
      AWARD: getInquiry,
      COST_RESULT: getCostResult,
    };
    const detail: any = await getters[approvalOrder.orderType](
      approvalOrder.id,
    );
    // 详情接口主要返回业务字段和明细；保留列表接口已经补齐的名称字段，
    // 避免点击订单后供应商/客户、产品和发起人显示为“-”。
    const mergedDetail = {
      ...approvalOrder,
      ...detail,
      creatorName: detail.creatorName ?? approvalOrder.creatorName,
      partnerName:
        detail.supplierName ?? detail.customerName ?? approvalOrder.partnerName,
      productNames: detail.productNames ?? approvalOrder.productNames,
    };
    selectedOrder.value =
      approvalOrder.orderType === 'PURCHASE'
        ? normalizePurchase(mergedDetail)
        : approvalOrder.orderType === 'SALE'
          ? normalizeSale(mergedDetail)
          : normalizeBusiness(mergedDetail, approvalOrder.orderType);
  } finally {
    detailLoading.value = false;
  }
}

async function handleSubmit(order: ApprovalOrder | Record<string, any>) {
  const approvalOrder = order as ApprovalOrder;
  submitting.value = true;
  try {
    const submitters: Record<OrderType, (id: number) => Promise<any>> = {
      PURCHASE: submitPurchaseOrder,
      SALE: submitSaleOrder,
      PURCHASE_REQUEST: submitPurchaseRequest,
      INQUIRY: submitInquiry,
      AWARD: submitAward,
      COST_RESULT: submitCostResult,
    };
    await submitters[approvalOrder.orderType](approvalOrder.id);
    message.success('订单已提交审批');
    selectedOrder.value = undefined;
    await loadOrders();
  } finally {
    submitting.value = false;
  }
}

function openProcess(order: ApprovalOrder | Record<string, any>) {
  const approvalOrder = order as ApprovalOrder;
  if (!approvalOrder.processInstanceId) return;
  router.push({
    path: '/bpm/process-instance/detail',
    query: { id: approvalOrder.processInstanceId },
  });
}

function formatMoney(value?: number) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`;
}

function formatTime(value?: Date | string) {
  if (!value) return '-';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

const typeLabels: Record<OrderType, string> = {
  PURCHASE: '采购订单',
  SALE: '销售订单',
  PURCHASE_REQUEST: '采购申请',
  INQUIRY: '询价审批',
  AWARD: '定标审批',
  COST_RESULT: '造价结果',
};

onMounted(loadOrders);
</script>

<template>
  <Page auto-content-height>
    <div class="flex flex-col gap-4">
      <Row :gutter="16">
        <Col :span="6"
          ><Card><Statistic title="全部订单" :value="statistics.total" /></Card
        ></Col>
        <Col :span="6"
          ><Card><Statistic title="审批中" :value="statistics.process" /></Card
        ></Col>
        <Col :span="6"
          ><Card
            ><Statistic title="审批通过" :value="statistics.approve" /></Card
        ></Col>
        <Col :span="6"
          ><Card
            ><Statistic
              title="待提交/需处理"
              :value="statistics.pending" /></Card
        ></Col>
      </Row>

      <Card title="订单与审批详情">
        <Spin :spinning="detailLoading">
          <template v-if="selectedOrder">
            <Descriptions bordered :column="4" size="small">
              <DescriptionsItem label="订单类型">
                {{ typeLabels[selectedOrder.orderType] }}
              </DescriptionsItem>
              <DescriptionsItem label="订单编号">{{
                selectedOrder.no
              }}</DescriptionsItem>
              <DescriptionsItem label="供应商/客户">{{
                selectedOrder.partnerName || '-'
              }}</DescriptionsItem>
              <DescriptionsItem label="审批状态">
                <Tag :color="statusMeta[selectedOrder.status]?.color">
                  {{
                    statusMeta[selectedOrder.status]?.text ||
                    selectedOrder.status
                  }}
                </Tag>
              </DescriptionsItem>
              <DescriptionsItem label="发起人">{{
                selectedOrder.creatorName || '-'
              }}</DescriptionsItem>
              <DescriptionsItem label="下单时间">{{
                formatTime(selectedOrder.orderTime)
              }}</DescriptionsItem>
              <DescriptionsItem label="合计数量">{{
                selectedOrder.totalCount || 0
              }}</DescriptionsItem>
              <DescriptionsItem label="订单金额">{{
                formatMoney(selectedOrder.totalPrice)
              }}</DescriptionsItem>
              <DescriptionsItem label="产品" :span="2">{{
                selectedOrder.productNames || '-'
              }}</DescriptionsItem>
              <DescriptionsItem label="备注" :span="2">{{
                selectedOrder.remark || '-'
              }}</DescriptionsItem>
            </Descriptions>

            <div class="mt-4 flex justify-end gap-2">
              <Button
                v-if="[0, 30, 40].includes(selectedOrder.status)"
                type="primary"
                :loading="submitting"
                @click="handleSubmit(selectedOrder)"
              >
                提交审批
              </Button>
              <Button
                v-if="selectedOrder.processInstanceId"
                @click="openProcess(selectedOrder)"
              >
                查看完整流程
              </Button>
            </div>

            <div class="mt-4">
              <BpmProcessInstanceTaskList
                v-if="selectedOrder.processInstanceId"
                :key="selectedOrder.processInstanceId"
                :id="selectedOrder.processInstanceId"
                :loading="detailLoading"
              />
              <Empty v-else description="该订单尚未提交审批" />
            </div>
          </template>
          <Empty v-else description="请从下方选择一个订单" />
        </Spin>
      </Card>

      <Card title="全部订单">
        <div class="approval-filter-bar">
          <Space wrap>
            <Input
              v-model:value="keyword"
              allow-clear
              placeholder="订单号、供应商、客户、产品或发起人"
              style="width: 320px"
            />
            <Select
              v-model:value="orderType"
              allow-clear
              placeholder="订单类型"
              style="width: 140px"
              :options="[
                ...Object.entries(typeLabels).map(([value, label]) => ({
                  label,
                  value,
                })),
              ]"
            />
            <Select
              v-model:value="status"
              allow-clear
              placeholder="审批状态"
              style="width: 140px"
              :options="
                Object.entries(statusMeta).map(([value, item]) => ({
                  label: item.text,
                  value: Number(value),
                }))
              "
            />
            <Button @click="loadOrders">刷新</Button>
          </Space>
        </div>

        <Table
          class="approval-orders-table"
          :columns="columns"
          :data-source="pagedOrders"
          :loading="loading"
          :row-key="(row: ApprovalOrder) => row.key"
          :custom-row="
            (row: ApprovalOrder) => ({
              onClick: () => selectOrder(row),
              style: { cursor: 'pointer' },
            })
          "
          :pagination="{
            current: currentPage,
            pageSize,
            total: filteredOrders.length,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条订单`,
            onChange: (page: number, size: number) => {
              currentPage = page;
              pageSize = size;
            },
          }"
          :scroll="{ x: 1450 }"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'orderType'">
              <Tag
                :color="
                  record.orderType === 'PURCHASE'
                    ? 'blue'
                    : record.orderType === 'SALE'
                      ? 'purple'
                      : 'cyan'
                "
              >
                {{ typeLabels[record.orderType as OrderType] }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'totalPrice'">{{
              formatMoney(record.totalPrice)
            }}</template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="statusMeta[record.status]?.color">{{
                statusMeta[record.status]?.text || record.status
              }}</Tag>
            </template>
            <template v-else-if="column.key === 'orderTime'">{{
              formatTime(record.orderTime)
            }}</template>
            <template v-else-if="column.key === 'actions'">
              <Space>
                <Button
                  type="link"
                  size="small"
                  @click.stop="selectOrder(record)"
                  >查看</Button
                >
                <Button
                  v-if="[0, 30, 40].includes(record.status)"
                  type="link"
                  size="small"
                  @click.stop="handleSubmit(record)"
                  >提交审批</Button
                >
                <Button
                  v-if="record.processInstanceId"
                  type="link"
                  size="small"
                  @click.stop="openProcess(record)"
                  >流程</Button
                >
              </Space>
            </template>
          </template>
        </Table>
      </Card>
    </div>
  </Page>
</template>

<style scoped>
.approval-filter-bar {
  margin-bottom: 20px;
}

.approval-orders-table :deep(.ant-table-thead > tr > th) {
  background-color: transparent !important;
}
</style>
