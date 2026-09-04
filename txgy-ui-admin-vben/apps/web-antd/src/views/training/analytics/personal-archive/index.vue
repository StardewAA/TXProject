<script lang="ts" setup>
import SkeletonPage from '../components/skeleton-page.vue';

defineOptions({ name: 'TrainingPersonalArchive' });
</script>

<template>
  <SkeletonPage
    description="课程学时明细、考试记录、普通与联合认证证书、VR 实操记录与人才成长进度、证书到期提醒。"
    skeleton-no="92"
    title="个人学习档案"
  />
</template>
