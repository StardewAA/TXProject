<script lang="ts" setup>
import type { ErpPurchaseOrderApi } from '#/api/erp/purchase/order';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Descriptions, DescriptionsItem, Spin, Table, Tag } from 'ant-design-vue';

import { getPurchaseOrderTrace } from '#/api/erp/purchase/order';

const trace = ref<ErpPurchaseOrderApi.OrderTrace>();
const loading = ref(false);

/** 单据状态：与 ErpAuditStatus 保持一致 */
const STATUS_MAP: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  10: { color: 'processing', text: '审批中' },
  20: { color: 'success', text: '已审批' },
  30: { color: 'error', text: '已驳回' },
  40: { color: 'default', text: '已作废' },
};

function statusText(status?: number) {
  return status === undefined ? '-' : (STATUS_MAP[status]?.text ?? String(status));
}
function statusColor(status?: number) {
  return STATUS_MAP[status ?? -1]?.color ?? 'default';
}

const awardColumns = [
  { title: '产品编码', dataIndex: 'productCode', width: 120 },
  { title: '产品名称', dataIndex: 'productName', width: 180 },
  { title: '中标供应商', dataIndex: 'supplierName', width: 200 },
  { title: '中标数量', dataIndex: 'awardCount', width: 100 },
  { title: '未税单价', dataIndex: 'productPrice', width: 100 },
  { title: '推荐原因', dataIndex: 'awardReason', ellipsis: true },
];

const inColumns = [
  { title: '入库单号', dataIndex: 'no', width: 180 },
  { title: '入库时间', dataIndex: 'inTime', width: 170 },
  { title: '金额', dataIndex: 'totalPrice', width: 120 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '业务链号', dataIndex: 'businessChainNo', width: 220 },
  { title: 'WMS 状态', dataIndex: 'wmsStatus', width: 120 },
];

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      trace.value = undefined;
      return;
    }
    const data = modalApi.getData<{ id?: number }>();
    if (!data?.id) {
      return;
    }
    modalApi.lock();
    loading.value = true;
    try {
      trace.value = await getPurchaseOrderTrace(data.id);
    } finally {
      loading.value = false;
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="采购订单溯源" class="w-3/4" :show-confirm-button="false">
    <Spin :spinning="loading">
      <div v-if="trace" class="px-2 pb-2">
        <!-- 溯源状态 -->
        <Alert
          v-if="!trace.traceable"
          type="warning"
          show-icon
          class="mb-4"
          message="该订单没有上游溯源"
          :description="trace.traceHint"
        />
        <Alert
          v-else
          type="success"
          show-icon
          class="mb-4"
          message="已建立溯源链路"
          :description="`业务链号 ${trace.order?.businessChainNo ?? '-'}`"
        />

        <!-- 订单信息 -->
        <Descriptions title="订单信息" :column="3" size="small" bordered class="mb-4">
          <DescriptionsItem label="订单单号">{{ trace.order?.no ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="供应商">{{ trace.order?.supplierName ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="订单时间">{{ trace.order?.orderTime ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="未税金额">{{ trace.order?.totalProductPrice ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="含税金额">{{ trace.order?.totalPrice ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="状态">
            <Tag :color="statusColor(trace.order?.status)">{{ statusText(trace.order?.status) }}</Tag>
          </DescriptionsItem>
          <DescriptionsItem label="来源类型">{{ trace.order?.sourceType ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="来源单号">{{ trace.order?.sourceNo ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="业务链号">{{ trace.order?.businessChainNo ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="已入库数量">{{ trace.order?.inCount ?? 0 }}</DescriptionsItem>
          <DescriptionsItem label="已退货数量">{{ trace.order?.returnCount ?? 0 }}</DescriptionsItem>
          <DescriptionsItem label="已生成订单">
            {{ trace.inquiry?.purchaseOrderIds ?? '-' }}
          </DescriptionsItem>
        </Descriptions>

        <!-- 来源询价单 -->
        <Descriptions v-if="trace.inquiry" title="来源询价单" :column="3" size="small" bordered class="mb-4">
          <DescriptionsItem label="询价单号">{{ trace.inquiry.no }}</DescriptionsItem>
          <DescriptionsItem label="标题">{{ trace.inquiry.title ?? '-' }}</DescriptionsItem>
          <DescriptionsItem label="有效报价数">{{ trace.inquiry.quoteCount ?? 0 }}</DescriptionsItem>
          <DescriptionsItem label="询价审批">
            <Tag :color="statusColor(trace.inquiry.status)">{{ statusText(trace.inquiry.status) }}</Tag>
          </DescriptionsItem>
          <DescriptionsItem label="定标审批">
            <Tag :color="statusColor(trace.inquiry.awardStatus)">{{ statusText(trace.inquiry.awardStatus) }}</Tag>
          </DescriptionsItem>
        </Descriptions>

        <!-- 中标明细 -->
        <div class="mb-2 font-medium">中标明细</div>
        <Table
          :columns="awardColumns"
          :data-source="trace.awardItems ?? []"
          :pagination="false"
          size="small"
          row-key="productCode"
          class="mb-4"
        >
          <template #emptyText>无中标明细（该订单可能不是由定标生成）</template>
        </Table>

        <!-- 入库单 -->
        <div class="mb-2 font-medium">采购入库单</div>
        <Table
          :columns="inColumns"
          :data-source="trace.inDocs ?? []"
          :pagination="false"
          size="small"
          row-key="id"
        >
          <template #emptyText>暂无入库单</template>
        </Table>
      </div>
    </Spin>
  </Modal>
</template>
