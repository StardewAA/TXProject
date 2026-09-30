<script lang="ts" setup>
import { onBeforeMount } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

defineOptions({ name: 'TrainingVr' });

// VR 虚拟仿真实操训练为带子菜单的父页面：点击父菜单或直接访问 /training/vr 时，
// 默认跳转到第一个子模块（VR 资源项目管理），保证子页面始终嵌在父容器中渲染。
const route = useRoute();
const router = useRouter();

onBeforeMount(() => {
  if (route.path === '/training/vr') {
    router.replace('/training/vr/project');
  }
});
</script>

<template>
  <Page auto-content-height>
    <router-view />
  </Page>
</template>
