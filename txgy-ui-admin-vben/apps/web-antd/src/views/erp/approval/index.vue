<script lang="ts" setup>
import type { TableColumnsType } from 'ant-design-vue';

import type { BpmProcessInstanceApi } from '#/api/bpm/processInstance';
import type { BpmTaskApi } from '#/api/bpm/task';

import { computed, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  DescriptionsItem,
  Empty,
  Input,
  message,
  Modal,
  Progress,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getApprovalDetail } from '#/api/bpm/processInstance';
import { claimErpApprovalTask, getTaskTodoPage } from '#/api/bpm/task';
import {
  getInquiry,
  getInquiryPage,
  submitAward,
  submitInquiry,
} from '#/api/erp/purchase/inquiry';
import {
  getPurchaseOrder,
  getPurchaseOrderPage,
  submitPurchaseOrder,
} from '#/api/erp/purchase/order';
import {
  getPurchaseRequest,
  getPurchaseRequestPage,
  submitPurchaseRequest,
} from '#/api/erp/purchase/request';
import {
  getCostResult,
  getCostResultPage,
  submitCostResult,
} from '#/api/erp/sale/cost';
import {
  getSaleOrder,
  getSaleOrderPage,
  submitSaleOrder,
} from '#/api/erp/sale/order';
import BpmProcessInstanceTaskList from '#/views/bpm/processInstance/detail/modules/task-list.vue';

defineOptions({ name: 'ErpApprovalManagement' });

type OrderType =
  | 'AWARD'
  | 'COST_RESULT'
  | 'INQUIRY'
  | 'PURCHASE'
  | 'PURCHASE_REQUEST'
  | 'SALE';

type ViewScope = 'ALL' | 'DONE' | 'MY_TODO' | 'PROCESSING' | 'RESUBMIT';

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
  todoTask?: BpmTaskApi.Task;
  remark?: string;
  items?: any[];
}

const router = useRouter();
const route = useRoute();
const { hasAccessByCodes } = useAccess();
const loading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const claimingTaskId = ref<string>();
const submitConfirmOpen = ref(false);
const allOrders = ref<ApprovalOrder[]>([]);
const selectedOrder = ref<ApprovalOrder>();
const approvalNodes = ref<BpmProcessInstanceApi.ApprovalNodeInfo[]>([]);
const selectedTodoTask = ref<BpmTaskApi.Task>();
const keyword = ref('');
const orderType = ref<OrderType | undefined>();
const status = ref<number | undefined>();
const viewScope = ref<ViewScope>('ALL');
const currentPage = ref(1);
const pageSize = ref(10);
const lastActivatedAt = ref(0);
let detailRequestId = 0;

const canApproveOrders = computed(() =>
  hasAccessByCodes(['erp:approval:approve']),
);

const statusMeta: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  10: { color: 'processing', text: '审批中' },
  20: { color: 'success', text: '审批通过' },
  30: { color: 'error', text: '审批驳回' },
  40: { color: 'warning', text: '已撤销' },
};

/**
 * 列表排序：先按审批状态分组，再在组内按下单时间倒序。
 * 分组顺序 —— 草稿（最上）→ 审批中（中间）→ 审批通过（后面）→ 审批驳回 → 已撤销。
 * 因此“审批中”的单据一旦审批通过，会自动落到后面的“审批通过”分组并按时间排位。
 */
const STATUS_SORT_RANK: Record<number, number> = {
  0: 0, // 草稿置顶
  10: 1, // 审批中居中
  20: 2, // 审批通过靠后
  30: 3, // 审批驳回
  40: 4, // 已撤销
};

interface SortableOrder {
  createTime?: Date | string;
  orderTime?: Date | string;
  status: number;
}

function statusSortRank(status: number) {
  return STATUS_SORT_RANK[status] ?? 5;
}

function orderSortTime(order: SortableOrder) {
  const time = new Date(order.orderTime || order.createTime || 0).getTime();
  return Number.isNaN(time) ? 0 : time;
}

function compareOrders(a: SortableOrder, b: SortableOrder) {
  const rankDiff = statusSortRank(a.status) - statusSortRank(b.status);
  return rankDiff === 0 ? orderSortTime(b) - orderSortTime(a) : rankDiff;
}

const filteredOrders = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return allOrders.value
    .filter((item) => {
      const matchesKeyword =
        !value ||
        [item.no, item.partnerName, item.productNames, item.creatorName].some(
          (field) => field?.toLowerCase().includes(value),
        );
      return (
        matchesKeyword &&
        (!orderType.value || item.orderType === orderType.value) &&
        (status.value === undefined || item.status === status.value) &&
        (viewScope.value === 'ALL' ||
          (viewScope.value === 'MY_TODO' &&
            (canApproveOrders.value
              ? item.status === 10
              : Boolean(item.todoTask))) ||
          (viewScope.value === 'PROCESSING' && item.status === 10) ||
          (viewScope.value === 'DONE' && item.status === 20) ||
          (viewScope.value === 'RESUBMIT' &&
            [0, 30, 40].includes(item.status)))
      );
    })
    .sort(compareOrders);
});

const pagedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredOrders.value.slice(start, start + pageSize.value);
});

const statistics = computed(() => ({
  total: allOrders.value.length,
  process: allOrders.value.filter((item) => item.status === 10).length,
  approve: allOrders.value.filter((item) => item.status === 20).length,
  pending: allOrders.value.filter((item) =>
    canApproveOrders.value ? item.status === 10 : Boolean(item.todoTask),
  ).length,
  resubmit: allOrders.value.filter((item) => [0, 30, 40].includes(item.status))
    .length,
}));

const activeApprovalNodes = computed(() =>
  approvalNodes.value.filter((node) => node.startTime && !node.endTime),
);

const currentRunnableTask = computed<BpmTaskApi.Task | undefined>(() => {
  if (selectedTodoTask.value?.id) return selectedTodoTask.value;
  return activeApprovalNodes.value
    .flatMap((node) => (node.tasks || []) as BpmTaskApi.Task[])
    .find((task) => task.id && !task.endTime);
});

const currentNodeNames = computed(() => {
  const names = activeApprovalNodes.value
    .map((node) => node.name)
    .filter(Boolean);
  return names.length > 0 ? [...new Set(names)].join('、') : '-';
});

const currentApproverNames = computed(() => {
  const names = activeApprovalNodes.value.flatMap((node) => [
    ...(node.tasks || []).map(
      (task) => task.assigneeUser?.nickname || task.ownerUser?.nickname,
    ),
    ...(node.candidateUsers || []).map((user) => user.nickname),
  ]);
  const validNames = names.filter(Boolean);
  if (selectedTodoTask.value?.assigneeUser?.nickname) {
    validNames.unshift(selectedTodoTask.value.assigneeUser.nickname);
  }
  return validNames.length > 0
    ? [...new Set(validNames)].join('、')
    : '等待流程分配';
});

const approvalProgress = computed(() => {
  const nodes = approvalNodes.value.filter((node) => node.tasks?.length);
  if (nodes.length === 0) return selectedOrder.value?.status === 20 ? 100 : 0;
  const completed = nodes.filter((node) => Boolean(node.endTime)).length;
  return Math.round((completed / nodes.length) * 100);
});

const scopeOptions = computed(() => [
  { label: `全部 ${statistics.value.total}`, value: 'ALL' },
  {
    label: `${canApproveOrders.value ? '可审批' : '我的待办'} ${statistics.value.pending}`,
    value: 'MY_TODO',
  },
  { label: `审批中 ${statistics.value.process}`, value: 'PROCESSING' },
  { label: `已通过 ${statistics.value.approve}`, value: 'DONE' },
  { label: `待提交 ${statistics.value.resubmit}`, value: 'RESUBMIT' },
]);

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
  { title: '当前处理', dataIndex: 'todoTask', key: 'todoTask', width: 140 },
  { title: '下单时间', dataIndex: 'orderTime', key: 'orderTime', width: 180 },
  { title: '操作', key: 'actions', fixed: 'right', width: 230 },
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
    const failedSources: string[] = [];
    const safeLoad = async (name: string, loader: () => Promise<any>) => {
      try {
        return await loader();
      } catch {
        failedSources.push(name);
        return { list: [] };
      }
    };
    const [
      purchasePage,
      salePage,
      requestPage,
      inquiryPage,
      costPage,
      todoPage,
    ] = await Promise.all([
      safeLoad('采购订单', () =>
        getPurchaseOrderPage({ pageNo: 1, pageSize: 200 }),
      ),
      safeLoad('销售订单', () =>
        getSaleOrderPage({ pageNo: 1, pageSize: 200 }),
      ),
      safeLoad('采购申请', () =>
        getPurchaseRequestPage({ pageNo: 1, pageSize: 200 }),
      ),
      safeLoad('询价/定标', () => getInquiryPage({ pageNo: 1, pageSize: 200 })),
      safeLoad('造价结果', () =>
        getCostResultPage({ pageNo: 1, pageSize: 200 }),
      ),
      safeLoad('我的待办', () => getTaskTodoPage({ pageNo: 1, pageSize: 200 })),
    ]);
    const todoTaskMap = new Map<string, BpmTaskApi.Task>(
      (todoPage.list || [])
        .filter((task: BpmTaskApi.Task) => task.processInstanceId)
        .map((task: BpmTaskApi.Task) => [task.processInstanceId, task]),
    );
    allOrders.value = [
      ...(purchasePage.list || []).map(normalizePurchase),
      ...(salePage.list || []).map(normalizeSale),
      ...(requestPage.list || []).map((item: any) =>
        normalizeBusiness(item, 'PURCHASE_REQUEST'),
      ),
      ...(inquiryPage.list || []).map((item: any) =>
        normalizeBusiness(item, 'INQUIRY'),
      ),
      ...(inquiryPage.list || [])
        .filter((item: any) => (item.businessStatus ?? 0) >= 40)
        .map((item: any) => normalizeBusiness(item, 'AWARD')),
      ...(costPage.list || []).map((item: any) =>
        normalizeBusiness(item, 'COST_RESULT'),
      ),
    ]
      .map((item) => ({
        ...item,
        todoTask: item.processInstanceId
          ? todoTaskMap.get(item.processInstanceId)
          : undefined,
      }))
      .sort(compareOrders);
    if (failedSources.length > 0) {
      message.warning(`部分数据暂时无法读取：${failedSources.join('、')}`);
    }
    const previousSelection = selectedOrder.value
      ? allOrders.value.find((item) => item.key === selectedOrder.value?.key)
      : undefined;
    if (allOrders.value.length > 0) {
      const targetOrderNo = String(route.query.orderNo || '');
      const targetOrderType = String(route.query.orderType || '');
      const targetOrder = allOrders.value.find(
        (item) =>
          item.no === targetOrderNo &&
          (!targetOrderType || item.orderType === targetOrderType),
      );
      await selectOrder(
        targetOrder ||
          previousSelection ||
          allOrders.value.find((item) => item.todoTask) ||
          allOrders.value[0]!,
      );
    } else {
      selectedOrder.value = undefined;
      approvalNodes.value = [];
      selectedTodoTask.value = undefined;
    }
  } finally {
    loading.value = false;
  }
}

async function selectOrder(order: ApprovalOrder | Record<string, any>) {
  const approvalOrder = order as ApprovalOrder;
  const requestId = ++detailRequestId;
  detailLoading.value = true;
  approvalNodes.value = [];
  selectedTodoTask.value = approvalOrder.todoTask;
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
    if (requestId !== detailRequestId) return;
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
    selectedOrder.value.todoTask = approvalOrder.todoTask;
    if (selectedOrder.value.processInstanceId) {
      try {
        const approvalDetail = await getApprovalDetail({
          processInstanceId: selectedOrder.value.processInstanceId,
          taskId: approvalOrder.todoTask?.id,
        });
        if (requestId !== detailRequestId) return;
        approvalNodes.value = approvalDetail.activityNodes || [];
        selectedTodoTask.value =
          approvalDetail.todoTask || approvalOrder.todoTask;
      } catch {
        message.warning('订单已读取，但审批节点信息暂时无法加载');
      }
    }
  } finally {
    if (requestId === detailRequestId) {
      detailLoading.value = false;
    }
  }
}

async function handleSubmit(order: ApprovalOrder | Record<string, any>) {
  if (submitting.value || submitConfirmOpen.value) return;
  const approvalOrder = order as ApprovalOrder;
  submitConfirmOpen.value = true;
  Modal.confirm({
    title: `确认提交${typeLabels[approvalOrder.orderType]}审批？`,
    content: `${approvalOrder.no} 提交后将按已发布的流程逐级流转；审批中不能直接修改业务数据。`,
    okText: '确认提交',
    cancelText: '暂不提交',
    onCancel() {
      submitConfirmOpen.value = false;
    },
    async onOk() {
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
        message.success(`${approvalOrder.no} 已提交，等待审批`);
        selectedOrder.value = undefined;
        await loadOrders();
      } finally {
        submitting.value = false;
        submitConfirmOpen.value = false;
      }
    },
  });
}

function openProcess(
  order: ApprovalOrder | Record<string, any>,
  handleTodo = false,
  taskId?: string,
) {
  const approvalOrder = order as ApprovalOrder;
  if (!approvalOrder.processInstanceId) return;
  router.push({
    path: '/bpm/process-instance/detail',
    query: {
      id: approvalOrder.processInstanceId,
      ...(handleTodo && (taskId || approvalOrder.todoTask?.id)
        ? { taskId: taskId || approvalOrder.todoTask?.id }
        : {}),
    },
  });
}

async function handleApproval(order: ApprovalOrder | Record<string, any>) {
  const approvalOrder = order as ApprovalOrder;
  if (!canApproveOrders.value) {
    message.warning('当前账号没有处理 ERP 订单审批的权限');
    return;
  }
  await selectOrder(approvalOrder);
  const task = currentRunnableTask.value;
  if (!task?.id) {
    message.warning('当前审批节点已经处理或暂时没有可执行任务，请刷新后重试');
    await loadOrders();
    return;
  }
  claimingTaskId.value = task.id;
  try {
    await claimErpApprovalTask(task.id);
    message.success(`已进入“${task.name}”审批`);
    openProcess(approvalOrder, true, task.id);
  } finally {
    claimingTaskId.value = undefined;
  }
}

function resetFilters() {
  keyword.value = '';
  orderType.value = undefined;
  status.value = undefined;
  viewScope.value = 'ALL';
  currentPage.value = 1;
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

watch([keyword, orderType, status, viewScope], () => {
  currentPage.value = 1;
});

watch(viewScope, async () => {
  const currentVisible = filteredOrders.value.some(
    (item) => item.key === selectedOrder.value?.key,
  );
  if (!currentVisible && filteredOrders.value[0]) {
    await selectOrder(filteredOrders.value[0]);
  }
});

onMounted(async () => {
  await loadOrders();
  lastActivatedAt.value = Date.now();
});

onActivated(async () => {
  if (lastActivatedAt.value && Date.now() - lastActivatedAt.value > 1000) {
    await loadOrders();
    lastActivatedAt.value = Date.now();
  }
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex flex-col gap-4">
      <Row :gutter="16">
        <Col :xs="12" :lg="6">
<Card class="approval-stat-card" @click="viewScope = 'ALL'">
<Statistic title="全部业务单据" :value="statistics.total" />
</Card>
</Col>
        <Col :xs="12" :lg="6">
<Card class="approval-stat-card" @click="viewScope = 'MY_TODO'">
<Statistic
              title="我的待审批"
              :value="statistics.pending"
              :value-style="
                statistics.pending ? { color: '#cf1322' } : {}
              "
/>
</Card>
</Col>
        <Col :xs="12" :lg="6">
<Card class="approval-stat-card" @click="viewScope = 'PROCESSING'">
<Statistic title="审批中" :value="statistics.process" />
</Card>
</Col>
        <Col :xs="12" :lg="6">
<Card class="approval-stat-card" @click="viewScope = 'DONE'">
<Statistic
              title="审批通过"
              :value="statistics.approve"
              :value-style="{ color: '#3f8600' }"
/>
</Card>
</Col>
      </Row>

      <Alert
        show-icon
        type="info"
        message="订单审批由角色权限控制"
        description="拥有“处理订单审批”权限的账号可以处理任一当前 ERP 审批节点；无此权限的账号只能查看。多人、多级顺序仍由工作流模型配置，系统会记录实际审批账号。"
      />

      <Card title="订单与审批详情">
        <Spin :spinning="detailLoading">
          <template v-if="selectedOrder">
            <Descriptions bordered :column="4" size="small">
              <DescriptionsItem label="订单类型">
                {{ typeLabels[selectedOrder.orderType] }}
              </DescriptionsItem>
              <DescriptionsItem label="订单编号">
{{
                selectedOrder.no
              }}
</DescriptionsItem>
              <DescriptionsItem label="供应商/客户">
{{
                selectedOrder.partnerName || '-'
              }}
</DescriptionsItem>
              <DescriptionsItem label="审批状态">
                <Tag :color="statusMeta[selectedOrder.status]?.color">
                  {{
                    statusMeta[selectedOrder.status]?.text ||
                    selectedOrder.status
                  }}
                </Tag>
              </DescriptionsItem>
              <DescriptionsItem label="发起人">
{{
                selectedOrder.creatorName || '-'
              }}
</DescriptionsItem>
              <DescriptionsItem label="下单时间">
{{
                formatTime(selectedOrder.orderTime)
              }}
</DescriptionsItem>
              <DescriptionsItem label="合计数量">
{{
                selectedOrder.totalCount || 0
              }}
</DescriptionsItem>
              <DescriptionsItem label="订单金额">
{{
                formatMoney(selectedOrder.totalPrice)
              }}
</DescriptionsItem>
              <DescriptionsItem
                v-if="selectedOrder.processInstanceId"
                label="当前审批节点"
              >
                {{ currentNodeNames }}
              </DescriptionsItem>
              <DescriptionsItem
                v-if="selectedOrder.processInstanceId"
                label="当前审批人"
              >
                {{ currentApproverNames }}
              </DescriptionsItem>
              <DescriptionsItem label="产品" :span="2">
{{
                selectedOrder.productNames || '-'
              }}
</DescriptionsItem>
              <DescriptionsItem label="备注" :span="2">
{{
                selectedOrder.remark || '-'
              }}
</DescriptionsItem>
            </Descriptions>

            <div
              v-if="selectedOrder.processInstanceId"
              class="approval-progress-panel"
            >
              <div class="flex items-center justify-between gap-4">
                <div>
                  <div class="font-medium">审批进度</div>
                  <div class="mt-1 text-sm text-gray-500">
                    <template v-if="canApproveOrders && currentRunnableTask">
                      你具备订单审批权限，可处理“{{
                        currentRunnableTask.name
                      }}”。进入审批页面后填写意见并通过或驳回。
                    </template>
                    <template v-else-if="selectedOrder.status === 10">
                      当前账号没有处理订单审批的权限，只能查看进度。
                    </template>
                    <template v-else>流程已结束，可查看完整审批记录。</template>
                  </div>
                </div>
                <Progress
                  type="circle"
                  :percent="approvalProgress"
                  :size="64"
                />
              </div>
            </div>

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
                v-if="canApproveOrders && selectedOrder.status === 10 && currentRunnableTask"
                type="primary"
                danger
                :loading="claimingTaskId === currentRunnableTask.id"
                @click="handleApproval(selectedOrder)"
              >
                处理当前审批
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
        <div class="mb-4 overflow-x-auto">
          <Segmented v-model:value="viewScope" :options="scopeOptions" />
        </div>
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
            <Button @click="resetFilters">重置</Button>
          </Space>
        </div>

        <Table
          class="approval-orders-table"
          :columns="columns"
          :data-source="pagedOrders"
          :loading="loading"
          :row-key="(row: ApprovalOrder) => row.key"
          :row-class-name="
            (row: ApprovalOrder) =>
              row.key === selectedOrder?.key ? 'approval-selected-row' : ''
          "
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
          :scroll="{ x: 1600 }"
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
            <template v-else-if="column.key === 'totalPrice'">
{{
              formatMoney(record.totalPrice)
            }}
</template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="statusMeta[record.status]?.color">
{{
                statusMeta[record.status]?.text || record.status
              }}
</Tag>
            </template>
            <template v-else-if="column.key === 'todoTask'">
              <Tooltip
                v-if="record.status === 10 && canApproveOrders"
                title="拥有审批权限，可处理当前审批节点"
              >
                <Tag color="error">可审批</Tag>
              </Tooltip>
              <span v-else-if="record.status === 10" class="text-gray-500">审批中</span>
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'orderTime'">
{{
              formatTime(record.orderTime)
            }}
</template>
            <template v-else-if="column.key === 'actions'">
              <Space>
                <Button
                  type="link"
                  size="small"
                  @click.stop="selectOrder(record)"
                  >
查看
</Button>
                <Button
                  v-if="record.status === 10 && canApproveOrders"
                  type="link"
                  danger
                  size="small"
                  :loading="Boolean(claimingTaskId) && selectedOrder?.key === record.key"
                  @click.stop="handleApproval(record)"
                  >
处理审批
</Button>
                <Button
                  v-if="[0, 30, 40].includes(record.status)"
                  type="link"
                  size="small"
                  @click.stop="handleSubmit(record)"
                  >
提交审批
</Button>
                <Button
                  v-if="record.processInstanceId"
                  type="link"
                  size="small"
                  @click.stop="openProcess(record)"
                  >
流程
</Button>
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

.approval-stat-card {
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.approval-stat-card:hover {
  box-shadow: 0 6px 18px rgb(0 0 0 / 8%);
  transform: translateY(-2px);
}

.approval-progress-panel {
  padding: 16px;
  margin-top: 16px;
  background: rgb(0 0 0 / 2%);
  border: 1px solid rgb(0 0 0 / 6%);
  border-radius: 8px;
}

.approval-orders-table :deep(.ant-table-thead > tr > th) {
  background-color: transparent !important;
}

.approval-orders-table :deep(.approval-selected-row > td) {
  background: var(--ant-color-primary-bg, #e6f4ff) !important;
}
</style>
