<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import ExamWorkspace from '../../components/exam-workspace.vue';

defineOptions({ name: 'TrainingExamSchedules' });
</script>

<template>
  <Page><ExamWorkspace embedded initial-page="schedules" /></Page>
</template>
