<script lang="ts" setup>
import SkeletonPage from '../components/skeleton-page.vue';

defineOptions({ name: 'TrainingExternalApi' });
</script>

<template>
  <SkeletonPage
    description="外部工程项目系统对接：培训任务下发、人员培训状态查询/推送、脱敏培训档案批量获取（单次≤200条）。"
    skeleton-no="96"
    title="外部系统对接"
  />
</template>
