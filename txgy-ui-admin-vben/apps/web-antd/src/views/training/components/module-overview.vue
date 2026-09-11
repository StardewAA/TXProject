<script lang="ts" setup>
import type { TrainingModuleKey } from '../module-data';

import { computed } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Card, Progress, Tag } from 'ant-design-vue';

import { getTrainingModule } from '../module-data';
import PageHeaderBar from './page-header-bar.vue';

defineOptions({ name: 'TrainingModuleOverview' });

const props = defineProps<{ moduleKey: TrainingModuleKey }>();
const currentModule = computed(() => getTrainingModule(props.moduleKey));
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <PageHeaderBar
        :crumb="`云培训学院 / 模块 ${currentModule.number}`"
        :title="currentModule.name"
        :description="currentModule.description"
      >
        <template #actions>
          <Tag color="processing" class="!m-0">模块框架已就绪</Tag>
        </template>
      </PageHeaderBar>

      <section class="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Card v-for="metric in currentModule.metrics" :key="metric.label" :bordered="false">
          <div class="text-sm text-gray-500 dark:text-gray-400">{{ metric.label }}</div>
          <div class="mt-2 flex items-baseline gap-1">
            <span class="text-3xl font-semibold text-gray-900 dark:text-gray-100">{{ metric.value }}</span>
            <span class="text-sm text-gray-400">{{ metric.suffix }}</span>
          </div>
          <div class="mt-3 h-1 rounded-full bg-gray-100 dark:bg-gray-700">
            <div class="h-full w-0 rounded-full" :style="{ backgroundColor: currentModule.accent }"></div>
          </div>
        </Card>
      </section>

      <section class="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(300px,1fr)]">
        <Card title="核心业务能力" :bordered="false">
          <div class="grid gap-4 md:grid-cols-2">
            <div
              v-for="capability in currentModule.capabilities"
              :key="capability.name"
              class="group rounded-xl border border-gray-100 p-4 transition hover:-translate-y-0.5 hover:shadow-md dark:border-gray-700"
            >
              <div class="flex gap-3">
                <div
                  class="flex size-10 shrink-0 items-center justify-center rounded-xl"
                  :style="{ backgroundColor: `${currentModule.accent}18`, color: currentModule.accent }"
                >
                  <IconifyIcon :icon="capability.icon" class="size-5" />
                </div>
                <div>
                  <div class="font-medium text-gray-900 dark:text-gray-100">{{ capability.name }}</div>
                  <div class="mt-1 text-sm leading-6 text-gray-500 dark:text-gray-400">
                    {{ capability.description }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Card>

        <Card title="建设进度" :bordered="false">
          <div class="flex items-center gap-5">
            <Progress type="circle" :percent="15" :stroke-color="currentModule.accent" :size="86" />
            <div>
              <div class="font-medium">第一阶段：模块底座</div>
              <div class="mt-1 text-sm text-gray-500">菜单、权限、服务和页面框架已接入</div>
            </div>
          </div>
          <div class="mt-6 space-y-4">
            <div v-for="(stage, index) in currentModule.stages" :key="stage" class="flex items-center gap-3">
              <div
                class="flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-medium"
                :style="{
                  backgroundColor: index === 0 ? `${currentModule.accent}20` : '#f3f4f6',
                  color: index === 0 ? currentModule.accent : '#9ca3af',
                }"
              >
                {{ index + 1 }}
              </div>
              <div class="text-sm" :class="index === 0 ? 'font-medium' : 'text-gray-500'">{{ stage }}</div>
            </div>
          </div>
        </Card>
      </section>
    </div>
  </Page>
</template>
