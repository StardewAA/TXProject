<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import ExamWorkspace from '../../components/exam-workspace.vue';

defineOptions({ name: 'TrainingExamCertificates' });
</script>

<template>
  <Page><ExamWorkspace embedded initial-page="certificates" /></Page>
</template>
