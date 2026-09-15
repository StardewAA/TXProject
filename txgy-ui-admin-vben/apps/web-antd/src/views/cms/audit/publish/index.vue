<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { publishColumns, publishRows } from '../data';

defineOptions({ name: 'CmsAuditPublish' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '发布' }, { label: '下架', danger: true }]"
    :columns="publishColumns"
    :rows="publishRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '今日发布', value: 3 },
      { label: '已发布内容', value: 826 },
      { label: '已下架内容', value: 42 },
      { label: '待发布内容', value: 11 },
    ]"
    eyebrow="CMS 内容审核"
    status-field="publishStatus"
    title="发布/下架管理"
  />
</template>
