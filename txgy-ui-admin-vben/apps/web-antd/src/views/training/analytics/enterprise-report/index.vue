<script lang="ts" setup>
import SkeletonPage from '../components/skeleton-page.vue';

defineOptions({ name: 'TrainingEnterpriseReport' });
</script>

<template>
  <SkeletonPage
    description="本企业全员培训档案、学徒技师人才等级分布、多维报表导出、项目培训归档与督办，严格企业数据隔离。"
    show-export
    skeleton-no="94"
    title="企业培训报表与档案"
  />
</template>
