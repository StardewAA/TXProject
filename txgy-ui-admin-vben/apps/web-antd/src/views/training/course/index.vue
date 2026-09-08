<script lang="ts" setup>
import { onBeforeMount } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

defineOptions({ name: 'TrainingCourse' });

// 课程管理为带子菜单的父页面：点击父菜单或直接访问 /training/course 时，
// 默认跳转到第一个子菜单（课程列表），保证子页面始终嵌在父容器中渲染。
const route = useRoute();
const router = useRouter();

onBeforeMount(() => {
  if (route.path === '/training/course') {
    router.replace('/training/course/list');
  }
});
</script>

<template>
  <Page auto-content-height>
    <router-view />
  </Page>
</template>
