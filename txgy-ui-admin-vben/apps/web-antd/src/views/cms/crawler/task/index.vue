<script lang="ts" setup>
import CmsTablePage from '../../_shared/cms-table-page.vue';
import { taskColumns, taskRows } from '../data';

defineOptions({ name: 'CmsCrawlerTask' });
</script>

<template>
  <CmsTablePage
    :actions="[{ label: '查看日志' }, { label: '重试', type: 'primary' }]"
    :columns="taskColumns"
    :rows="taskRows"
    :show-action="true"
    :show-status="true"
    :stats="[
      { label: '今日任务', value: 12 },
      { label: '成功', value: 10 },
      { label: '运行中', value: 1 },
      { label: '失败', value: 1 },
    ]"
    eyebrow="CMS 政策采集"
    primary-label="手动触发"
    title="采集任务管理"
  />
</template>
