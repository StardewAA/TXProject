<script lang="ts" setup>
import type { ErpLogisticsApi } from '#/api/erp/logistics';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import {
  Button,
  Descriptions,
  Empty,
  Table,
  Tag,
  Timeline,
  TimelineItem,
} from 'ant-design-vue';

import {
  getLogisticsStatusColor,
  getLogisticsStatusText,
  getLogisticsTrack,
  getSourceOrderTypeText,
} from '#/api/erp/logistics';

import ExceptionHandle from './exception-handle.vue';

const emit = defineEmits(['success']);
const detail = ref<ErpLogisticsApi.Detail>();
const currentId = ref<number>();

const [HandleModal, handleModalApi] = useVbenModal({
  connectedComponent: ExceptionHandle,
  destroyOnClose: true,
});

const [Modal, modalApi] = useVbenModal({
  footer: false,
  fullscreenButton: true,
  onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id: number }>();
    if (data?.id) {
      currentId.value = data.id;
      loadDetail();
    }
  },
});

async function loadDetail() {
  if (currentId.value) detail.value = await getLogisticsTrack(currentId.value);
}

function openTrack() {
  if (detail.value?.trackUrl) window.open(detail.value.trackUrl, '_blank', 'noopener,noreferrer');
}

function handleException(id: number) {
  handleModalApi.setData({ id }).open();
}

async function handleSuccess() {
  await loadDetail();
  emit('success');
}

const itemColumns = [
  { title: '行号', dataIndex: 'lineNo', width: 65 },
  { title: '物料编码', dataIndex: 'materialCode', width: 150 },
  { title: '物料名称', dataIndex: 'itemNameSnapshot' },
  { title: '规格', dataIndex: 'specificationSnapshot' },
  { title: '单位', dataIndex: 'unitNameSnapshot', width: 75 },
  { title: '数量', dataIndex: 'quantity', width: 100 },
];

const receiptColumns = [
  { title: '签收类型', dataIndex: 'receiptType', width: 150 },
  { title: '签收人', dataIndex: 'receiverName', width: 100 },
  { title: '签收数量', dataIndex: 'receiptQuantity', width: 100 },
  { title: '签收时间', dataIndex: 'receiptTime', width: 170 },
  { title: '说明', dataIndex: 'remark' },
  { title: '凭证', dataIndex: 'attachments', width: 160 },
];

const exceptionColumns = [
  { title: '异常类型', dataIndex: 'exceptionName', width: 140 },
  { title: '发生时间', dataIndex: 'occurredAt', width: 170 },
  { title: '说明', dataIndex: 'description' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '处理结果', dataIndex: 'handleResult' },
  { title: '操作', dataIndex: 'action', width: 90 },
];
</script>

<template>
  <Modal class="w-[1040px]" title="物流任务详情">
    <HandleModal @success="handleSuccess" />
    <div v-if="detail" class="p-4">
      <Descriptions :column="3" bordered size="small" title="运输信息">
        <Descriptions.Item label="物流任务号">{{ detail.logisticsTaskNo }}</Descriptions.Item>
        <Descriptions.Item label="运单号">{{ detail.waybillNo }}</Descriptions.Item>
        <Descriptions.Item label="当前状态">
          <Tag :color="getLogisticsStatusColor(detail.logisticsStatus)">
            {{ getLogisticsStatusText(detail.logisticsStatus) }}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="来源单据">
          {{ getSourceOrderTypeText(detail.sourceOrderType) }} / {{ detail.sourceOrderNo }}
        </Descriptions.Item>
        <Descriptions.Item label="业务链号">{{ detail.businessChainNo }}</Descriptions.Item>
        <Descriptions.Item label="承运商">{{ detail.carrierName }}</Descriptions.Item>
        <Descriptions.Item label="车辆">{{ detail.vehicleNo || '-' }}</Descriptions.Item>
        <Descriptions.Item label="司机">{{ detail.driverName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="联系电话">{{ detail.driverPhoneMasked || '-' }}</Descriptions.Item>
        <Descriptions.Item label="计划发车">{{ detail.plannedDepartureTime || '-' }}</Descriptions.Item>
        <Descriptions.Item label="预计到达">{{ detail.estimatedArrivalTime || '-' }}</Descriptions.Item>
        <Descriptions.Item label="定位状态">
          <Tag :color="detail.locationOnline ? 'success' : 'default'">
            {{ detail.locationOnline ? '在线' : '离线' }}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="当前位置">{{ detail.latestLocation || '-' }}</Descriptions.Item>
        <Descriptions.Item label="最后在线">{{ detail.lastOnlineTime || '-' }}</Descriptions.Item>
        <Descriptions.Item label="事件版本">{{ detail.latestEventVersion }}</Descriptions.Item>
      </Descriptions>

      <div class="mt-4 flex items-center justify-between">
        <h3 class="text-base font-semibold">关键节点</h3>
        <Button v-if="detail.trackUrl" type="primary" @click="openTrack">查看外部轨迹</Button>
      </div>
      <Timeline v-if="detail.nodes.length" class="mt-3">
        <TimelineItem v-for="node in detail.nodes" :key="node.id">
          <div class="flex flex-wrap items-baseline gap-2">
            <strong>{{ node.nodeName }}</strong>
            <span class="text-gray-500">{{ node.occurredAt }}</span>
            <Tag>v{{ node.eventVersion }}</Tag>
          </div>
          <div v-if="node.locationDescription" class="text-sm">{{ node.locationDescription }}</div>
          <div v-if="node.remark" class="text-sm text-gray-500">{{ node.remark }}</div>
        </TimelineItem>
      </Timeline>
      <Empty v-else description="暂无物流节点" />

      <h3 class="mt-4 text-base font-semibold">订单履约时间轴</h3>
      <Timeline v-if="detail.fulfillmentEvents.length" class="mt-3">
        <TimelineItem v-for="event in detail.fulfillmentEvents" :key="event.id">
          <div class="flex flex-wrap items-baseline gap-2">
            <strong>{{ event.eventName }}</strong>
            <span class="text-gray-500">{{ event.occurredAt }}</span>
            <Tag v-if="event.eventVersion">v{{ event.eventVersion }}</Tag>
          </div>
          <div v-if="event.eventDescription" class="text-sm text-gray-500">
            {{ event.eventDescription }}
          </div>
        </TimelineItem>
      </Timeline>
      <Empty v-else description="暂无履约记录" />

      <Table
        class="mt-4"
        :columns="itemColumns"
        :data-source="detail.items"
        :pagination="false"
        bordered
        row-key="id"
        size="small"
        title="运输明细"
      />

      <Table
        v-if="detail.receipts.length"
        class="mt-4"
        :columns="receiptColumns"
        :data-source="detail.receipts"
        :pagination="false"
        bordered
        row-key="id"
        size="small"
        title="签收记录"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'attachments'">
            <a
              v-for="attachment in record.attachments"
              :key="attachment.fileId"
              class="mr-2"
              :href="attachment.url"
              rel="noopener noreferrer"
              target="_blank"
            >
              {{ attachment.fileName }}
            </a>
            <span v-if="!record.attachments?.length">-</span>
          </template>
        </template>
      </Table>

      <Table
        v-if="detail.exceptions.length"
        class="mt-4"
        :columns="exceptionColumns"
        :data-source="detail.exceptions"
        :pagination="false"
        bordered
        row-key="id"
        size="small"
        title="异常记录"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <Tag :color="record.status === 'OPEN' ? 'error' : 'success'">
              {{ record.status === 'OPEN' ? '待处理' : '已处理' }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'action' && record.status === 'OPEN'">
            <Button
              v-access:code="['erp:logistics:exception-handle']"
              type="link"
              @click="handleException(record.id)"
            >
              处理
            </Button>
          </template>
        </template>
      </Table>
    </div>
  </Modal>
</template>
