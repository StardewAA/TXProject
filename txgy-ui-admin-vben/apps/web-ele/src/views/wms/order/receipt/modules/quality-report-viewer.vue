<script lang="ts" setup>
import type { QualityReport } from '#/api/wms/md/item/quality-report';

import { ref } from 'vue';
import { ElDialog, ElEmpty, ElImage, ElTag, ElTimeline, ElTimelineItem } from 'element-plus';
import { getQualityReportList, QualityStatus } from '#/api/wms/md/item/quality-report';

const visible = ref(false);
const loading = ref(false);
const reports = ref<QualityReport[]>([]);
const itemName = ref('');

function statusMeta(status?: number) {
  if (status === QualityStatus.QUALIFIED) return { label: '合格', type: 'success' as const };
  if (status === QualityStatus.ABNORMAL) return { label: '异常', type: 'danger' as const };
  return { label: '待质检', type: 'info' as const };
}

async function open(itemId: number | undefined, name?: string, current?: QualityReport) {
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
  <ElDialog v-model="visible" :title="`${itemName || '商品'}质检报告`" width="760px" append-to-body>
    <ElTimeline v-loading="loading">
      <ElTimelineItem v-for="report in reports" :key="report.id || report.createTime" :timestamp="report.createTime">
        <div class="rounded border p-3">
          <div class="mb-2 flex items-center gap-2">
            <ElTag :type="statusMeta(report.status).type">{{ statusMeta(report.status).label }}</ElTag>
            <ElTag v-if="report.current" type="primary" effect="plain">当前有效</ElTag>
            <span class="text-xs text-gray-400">{{ report.creatorName || report.creator || '-' }}</span>
          </div>
          <div class="flex flex-wrap gap-2">
            <ElImage v-for="url in report.imageUrls" :key="url" :src="url" :preview-src-list="report.imageUrls" class="h-16 w-16 rounded border" fit="cover" />
          </div>
          <div class="mt-2 rounded bg-gray-50 p-2 text-sm">{{ report.remark || '无备注' }}</div>
        </div>
      </ElTimelineItem>
    </ElTimeline>
    <ElEmpty v-if="!loading && reports.length === 0" description="暂无质检报告，当前按待质检处理" />
  </ElDialog>
</template>
