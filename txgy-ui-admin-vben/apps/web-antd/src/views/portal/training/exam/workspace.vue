<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import ExamWorkspace from '#/views/training/components/exam-workspace.vue';

defineOptions({ name: 'PortalTrainingExamWorkspace' });
</script>

<template>
  <Page auto-content-height>
    <!-- 门户入口不依赖后台菜单路由：embedded=false，保留考试系统内置导航 -->
    <ExamWorkspace mode="learner" initial-page="overview" />
  </Page>
</template>
