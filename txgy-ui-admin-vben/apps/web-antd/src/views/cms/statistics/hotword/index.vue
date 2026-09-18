<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { hotwordColumns, hotwordRows } from '../data';

defineOptions({ name: 'CmsHotwordStat' });
</script>

<template>
  <CmsTablePage
    :columns="hotwordColumns"
    :rows="hotwordRows"
    :stats="[
      { label: '今日检索量', value: '22,680' },
      { label: '活跃关键词', value: 386 },
      { label: '搜索转化率', value: '31.8%' },
      { label: '零结果词', value: 24 },
    ]"
    eyebrow="CMS 统计分析"
    title="检索热词分析"
  />
</template>
