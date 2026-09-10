<script lang="ts" setup>
import type { ErpCollaborationApi } from '#/api/erp/collaboration';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Descriptions, Table, Tag, Timeline, TimelineItem } from 'ant-design-vue';

import {
  getBusinessStatusColor,
  getBusinessStatusText,
  getCollaborationDetail,
  getCollaborationTypeText,
} from '#/api/erp/collaboration';

const emit = defineEmits(['success']);

const detail = ref<ErpCollaborationApi.CollaborationDetail>();

const [Modal, modalApi] = useVbenModal({
  footer: false,
  fullscreenButton: false,
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ id: number }>();
      if (data?.id) {
        loadDetail(data.id);
      }
    }
  },
});

async function loadDetail(id: number) {
  detail.value = await getCollaborationDetail(id);
}

const infoItems = [
  { key: 'collaborationNo', label: '协同单号' },
  { key: 'collaborationType', label: '协同单类型' },
  { key: 'businessStatus', label: '业务状态' },
  { key: 'sourceOrderNo', label: '来源单号' },
  { key: 'sourceProductionOrderNo', label: '来源生产工单' },
  { key: 'sourceEnterpriseCode', label: '发起方企业' },
  { key: 'targetEnterpriseCode', label: '接收方企业' },
  { key: 'currency', label: '币种' },
  { key: 'requiredAt', label: '期望交期' },
  { key: 'confirmUserName', label: '确认人' },
  { key: 'purchaseOrderNo', label: '关联采购订单' },
  { key: 'reason', label: '原因/补充说明' },
];

const itemColumns = [
  { title: '行号', dataIndex: 'lineNo', width: 60 },
  { title: '物料编码', dataIndex: 'materialCode', width: 150 },
  { title: '物料名称', dataIndex: 'materialNameSnapshot' },
  { title: '规格', dataIndex: 'specificationSnapshot' },
  { title: '单位', dataIndex: 'unitNameSnapshot', width: 70 },
  { title: '需求数量', dataIndex: 'shortageQuantity', width: 90 },
  { title: '参考价格', dataIndex: 'referencePrice', width: 90 },
  { title: '参考税率', dataIndex: 'taxRate', width: 90 },
  { title: '交货地址', dataIndex: 'deliveryAddress' },
];

const actionTextMap: Record<string, string> = {
  ACCEPTED: '接受',
  REJECTED: '拒绝',
  NEED_SUPPLEMENT: '要求补充资料',
};

function getFieldValue(key: string) {
  if (!detail.value) return '-';
  const value = (detail.value as Record<string, any>)[key];
  return value === null || value === undefined || value === '' ? '-' : value;
}
</script>

<template>
  <Modal class="w-[900px]" title="协同单详情">
    <div v-if="detail" class="p-4">
      <Descriptions :column="3" bordered size="small" title="基本信息">
        <Descriptions.Item v-for="info in infoItems" :key="info.key" :label="info.label">
          <template v-if="info.key === 'collaborationType'">
            {{ getCollaborationTypeText(detail.collaborationType) }}
          </template>
          <template v-else-if="info.key === 'businessStatus'">
            <Tag :color="getBusinessStatusColor(detail.businessStatus)">
              {{ getBusinessStatusText(detail.businessStatus) }}
            </Tag>
          </template>
          <template v-else>
            {{ getFieldValue(info.key) }}
          </template>
        </Descriptions.Item>
      </Descriptions>

      <Table
        class="mt-4"
        :columns="itemColumns"
        :data-source="detail.items"
        :pagination="false"
        bordered
        size="small"
        title="商品明细"
      />

      <div v-if="detail.confirmLogs?.length" class="mt-4">
        <h3 class="mb-2 text-base font-semibold">业务确认记录</h3>
        <Timeline>
          <TimelineItem v-for="(log, index) in detail.confirmLogs" :key="index">
            <Tag>{{ actionTextMap[log.action] || log.action }}</Tag>
            <span class="text-gray-500">
              {{ log.confirmUserName || '-' }} · {{ log.createTime || '' }}
            </span>
            <div v-if="log.reason" class="text-sm">{{ log.reason }}</div>
          </TimelineItem>
        </Timeline>
      </div>
    </div>
  </Modal>
</template>
