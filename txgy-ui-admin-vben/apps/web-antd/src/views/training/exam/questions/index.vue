<script lang="ts" setup>
// 题库管理：复用资源中心「考试题库」同一套页面（新增/编辑/AI 智能导入/搜索与资源中心完全同源）
import QuestionList from '../../resource/question-list/index.vue';

defineOptions({ name: 'TrainingExamQuestions' });
</script>

<template>
  <QuestionList />
</template>
