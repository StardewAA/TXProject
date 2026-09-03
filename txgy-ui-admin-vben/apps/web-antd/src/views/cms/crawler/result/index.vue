<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { resultColumns, resultRows } from '../data';

defineOptions({ name: 'CmsCrawlerResult' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '审核发布', type: 'primary' }, { label: '忽略', danger: true }]"
    :columns="resultColumns"
    :rows="resultRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '待审核', value: 8 },
      { label: '今日新增', value: 6 },
      { label: '重复跳过', value: 12 },
      { label: '已发布', value: 54 },
    ]"
    eyebrow="CMS 政策采集"
    title="采集结果审核"
  />
</template>
