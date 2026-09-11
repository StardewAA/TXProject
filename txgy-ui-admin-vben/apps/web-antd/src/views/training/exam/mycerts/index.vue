<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import ExamWorkspace from '../../components/exam-workspace.vue';

defineOptions({ name: 'TrainingExamMyCerts' });
</script>

<template>
  <Page><ExamWorkspace embedded initial-page="mycerts" /></Page>
</template>
