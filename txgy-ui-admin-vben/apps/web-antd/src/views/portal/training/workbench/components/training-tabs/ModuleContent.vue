<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue';

import { TRAINING_MODULES } from '../../../training-modules';
import MyLearningOverview from './MyLearningOverview.vue';

const props = defineProps<{ modulePath: string; subModule?: string }>();

// 复用原云培训学院各模块页面及其数据请求；一级 Tab 不触发路由。
const panels = {
  '/portal/training/courses': defineAsyncComponent(() => import('../../../courses/index.vue')),
  '/portal/training/learning': MyLearningOverview,
  '/portal/training/vr': defineAsyncComponent(() => import('../../../vr/index.vue')),
  '/portal/training/exam': defineAsyncComponent(() => import('../../../exam/index.vue')),
  '/portal/training/live': defineAsyncComponent(() => import('../../../live/index.vue')),
  '/portal/training/tasks': defineAsyncComponent(() => import('../../../tasks/index.vue')),
  '/portal/training/profile': defineAsyncComponent(() => import('../../../profile/index.vue')),
  '/portal/training/cooperation': defineAsyncComponent(() => import('../../../cooperation/index.vue')),
  '/portal/training/contest': defineAsyncComponent(() => import('../../../contest/index.vue')),
} as Record<string, ReturnType<typeof defineAsyncComponent>>;

const currentPanel = computed(() => panels[props.modulePath]);
const currentModule = computed(() => TRAINING_MODULES.find((item) => item.path === props.modulePath));
const subModuleCopy: Record<string, { title: string; summary: string }> = {
  safety: { title: '安全知识库', summary: 'HSE、安全规范、特殊作业与典型事故案例' },
  beginner: { title: '岗位入门课程', summary: '新员工入职、公司认知与岗位基础课程' },
  manual: { title: '技能速记手册', summary: '工艺卡、作业指导书与现场参数速查' },
  practice: { title: '实操训练中心', summary: '实操项目、设备操作、施工训练与训练记录' },
  professional: { title: '专业知识库', summary: '防腐涂装、保温绝热、检测与材料专业内容' },
  'ability-path': { title: '能力提升路径', summary: '分级学习任务、上岗要求与认证进度' },
};
const currentCopy = computed(() => subModuleCopy[props.subModule ?? '']);
const contentTitle = computed(() => currentCopy.value?.title ?? currentModule.value?.label);
const contentSummary = computed(() => currentCopy.value?.summary ?? currentModule.value?.summary);
</script>

<template>
  <section class="module-content" :aria-label="contentTitle">
    <header class="module-content-header">
      <div><h2>{{ contentTitle }}</h2><p>{{ contentSummary }}</p></div>
    </header>
    <component :is="currentPanel" :key="`${modulePath}-${subModule || ''}`" v-bind="modulePath === '/portal/training/courses' ? { subModule: props.subModule } : {}" />
  </section>
</template>

<style scoped>
.module-content { width: 100%; min-width: 0; margin-top: 27px; }
.module-content-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding-bottom: 15px; border-bottom: 1px solid #edf0f2; }
.module-content-header h2 { margin: 0 0 5px; color: #343b43; font-size: 22px; font-weight: 550; }
.module-content-header p { margin: 0; color: #949ca4; font-size: 12px; }
.module-content-header button { padding: 7px 15px; color: #db752b; background: #fff8f2; border: 0; border-radius: 17px; cursor: pointer; font-size: 12px; }
.module-content-header button:hover { background: #ffeddf; }
</style>
