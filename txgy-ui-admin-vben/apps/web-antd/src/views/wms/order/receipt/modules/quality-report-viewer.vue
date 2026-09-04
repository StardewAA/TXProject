<script lang="ts" setup>
import type { QualityReport } from '#/api/wms/md/item/quality-report';

import { ref } from 'vue';
import { Empty, Image, Modal, Spin, Tag } from 'ant-design-vue';

import { getQualityReportList, QualityStatus } from '#/api/wms/md/item/quality-report';

const visible = ref(false);
const loading = ref(false);
const reports = ref<QualityReport[]>([]);
const itemName = ref('');

function statusMeta(status?: number) {
  if (status === QualityStatus.QUALIFIED) return { color: 'success', label: '合格' };
  if (status === QualityStatus.ABNORMAL) return { color: 'error', label: '异常' };
  return { color: 'default', label: '待质检' };
}

async function open(itemId?: number, name?: string, current?: QualityReport) {
  itemName.value = name || '';
  visible.value = true;
  reports.value = current ? [current] : [];
  if (!itemId) return;
  loading.value = true;
  try {
    reports.value = await getQualityReportList(itemId);
  } finally {
    loading.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <Modal v-model:open="visible" :title="`${itemName || '商品'}质检报告`" width="760px" :footer="null">
    <Spin :spinning="loading">
      <div v-for="report in reports" :key="report.id || report.createTime" class="mb-3 rounded border p-3">
        <div class="mb-2 flex items-center gap-2">
          <Tag :color="statusMeta(report.status).color">{{ statusMeta(report.status).label }}</Tag>
          <Tag v-if="report.current" color="blue">当前有效</Tag>
          <span class="text-xs text-gray-400">{{ report.creatorName || report.creator || '-' }} · {{ report.createTime || '-' }}</span>
        </div>
        <div class="flex flex-wrap gap-2">
          <Image v-for="url in report.imageUrls" :key="url" :src="url" :width="64" :height="64" />
        </div>
        <div class="mt-2 rounded bg-gray-50 p-2 text-sm">{{ report.remark || '无备注' }}</div>
      </div>
      <Empty v-if="!loading && reports.length === 0" description="暂无质检报告，当前按待质检处理" />
    </Spin>
  </Modal>
</template>
