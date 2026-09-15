<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { recordColumns, recordRows } from '../data';

defineOptions({ name: 'CmsAuditRecord' });
</script>

<template>
  <CmsTablePage
    :columns="recordColumns"
    :rows="recordRows"
    :show-status="true"
    status-field="status"
    :stats="[
      { label: '审核记录总数', value: 1286 },
      { label: '本月通过', value: 318 },
      { label: '本月驳回', value: 46 },
      { label: '平均审核时长', value: '1.8小时' },
    ]"
    eyebrow="CMS 内容审核"
    title="审核记录"
  />
</template>
