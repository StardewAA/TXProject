<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { pendingColumns, pendingRows } from '../data';

defineOptions({ name: 'CmsAuditPending' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '通过审核', type: 'primary' }, { label: '驳回', danger: true }]"
    :columns="pendingColumns"
    :rows="pendingRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '待审核总数', value: 5 },
      { label: '今日新增', value: 3 },
      { label: '超过3天', value: 1 },
      { label: '平均处理时长', value: '1.2天' },
    ]"
    eyebrow="CMS 内容审核"
    primary-label="刷新待审"
    title="待审核内容"
  />
</template>
