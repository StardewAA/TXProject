<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { sourceColumns, sourceRows } from '../data';

defineOptions({ name: 'CmsCrawlerSource' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '测试采集' }, { label: '编辑' }]"
    :columns="sourceColumns"
    :rows="sourceRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '启用采集源', value: 9 },
      { label: '停用采集源', value: 4 },
      { label: '今日任务', value: 12 },
      { label: '平均成功率', value: '96.5%' },
    ]"
    eyebrow="CMS 政策采集"
    primary-label="新增采集源"
    title="采集源配置"
  />
</template>
