<script lang="ts" setup>
import SkeletonPage from '../components/skeleton-page.vue';

defineOptions({ name: 'TrainingLecturerData' });
</script>

<template>
  <SkeletonPage
    description="认证讲师仅可查看自己创建课程下学员学习完成情况，无跨课程与企业级数据权限。"
    skeleton-no="93"
    title="讲师数据权限"
  />
</template>
