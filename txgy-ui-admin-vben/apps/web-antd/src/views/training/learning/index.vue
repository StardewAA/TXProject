<script lang="ts" setup>
import { onBeforeMount } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

defineOptions({ name: 'TrainingLearning' });

// 学员学习中心为带子菜单的父页面：点击父菜单或直接访问 /training/learning 时，
// 默认跳转到第一个子菜单（学习任务），保证子页面始终嵌在父容器中渲染。
const route = useRoute();
const router = useRouter();

onBeforeMount(() => {
  if (route.path === '/training/learning') {
    router.replace('/training/learning/task');
  }
});
</script>

<template>
  <Page auto-content-height>
    <router-view />
  </Page>
</template>