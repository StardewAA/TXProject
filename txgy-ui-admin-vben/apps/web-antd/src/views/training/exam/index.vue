<script lang="ts" setup>
import { onBeforeMount } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

defineOptions({ name: 'TrainingExam' });

// 考试与认证为带二级菜单的父页面：点击父菜单或直接访问 /training/exam 时，
// 默认跳转到第一个子模块（总览），保证子页面始终嵌在父容器中渲染。
const route = useRoute();
const router = useRouter();

onBeforeMount(() => {
  if (route.path === '/training/exam') {
    router.replace('/training/exam/overview');
  }
});
</script>

<template>
  <Page auto-content-height>
    <router-view />
  </Page>
</template>
