<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import ExamWorkspace from '../../components/exam-workspace.vue';

defineOptions({ name: 'TrainingExamIssuers' });
</script>

<template>
  <Page><ExamWorkspace embedded initial-page="issuers" /></Page>
</template>
