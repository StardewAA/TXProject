<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import ExamWorkspace from '../../components/exam-workspace.vue';

defineOptions({ name: 'TrainingExamPapers' });
</script>

<template>
  <Page><ExamWorkspace embedded initial-page="papers" /></Page>
</template>
