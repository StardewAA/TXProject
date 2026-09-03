<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { contentStatColumns, contentStatRows } from '../data';

defineOptions({ name: 'CmsContentStat' });
</script>

<template>
  <CmsTablePage
    :columns="contentStatColumns"
    :rows="contentStatRows"
    :stats="[
      { label: '已发布内容', value: '1,076' },
      { label: '总浏览量', value: '243,930' },
      { label: '总下载量', value: '32,578' },
      { label: '总订阅量', value: '3,878' },
    ]"
    eyebrow="CMS 统计分析"
    title="内容数据统计"
  />
</template>
