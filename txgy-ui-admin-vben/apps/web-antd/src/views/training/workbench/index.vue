<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Avatar, Badge, Card, Tag } from 'ant-design-vue';

import { trainingModules } from '../module-data';

defineOptions({ name: 'TrainingWorkbench' });

const router = useRouter();

const dashboardMetrics = [
  { label: '本月学习人次', value: '0', trend: '数据接入后展示', icon: 'lucide:users-round', color: '#2563eb' },
  { label: '培训完成率', value: '0%', trend: '暂无培训任务', icon: 'lucide:circle-check-big', color: '#16a34a' },
  { label: '累计学习时长', value: '0h', trend: '暂无学习记录', icon: 'lucide:clock-3', color: '#7c3aed' },
  { label: '有效资格证书', value: '0', trend: '暂无证书记录', icon: 'lucide:award', color: '#ea580c' },
];

const todos = [
  { name: '待审核培训资源', count: 0, icon: 'lucide:file-check-2', color: '#2563eb' },
  { name: '待发布培训计划', count: 0, icon: 'lucide:calendar-clock', color: '#7c3aed' },
  { name: '待阅卷考试', count: 0, icon: 'lucide:clipboard-check', color: '#ea580c' },
  { name: '待复审证书', count: 0, icon: 'lucide:badge-alert', color: '#dc2626' },
];

const messages = [
  { title: '云培训学院模块已创建', time: '刚刚', type: '系统消息', color: 'blue' },
  { title: '九个一级业务模块框架已准备完成', time: '刚刚', type: '建设进度', color: 'green' },
  { title: '业务数据将在功能建设后自动汇总', time: '刚刚', type: '使用提示', color: 'orange' },
];
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <section class="flex flex-col items-start justify-between gap-4 rounded-xl bg-gradient-to-r from-blue-700 via-indigo-700 to-violet-700 p-5 text-white shadow-sm sm:flex-row sm:items-center">
        <div>
          <div class="mb-1 flex items-center gap-2 text-sm text-blue-100">
            <IconifyIcon icon="lucide:graduation-cap" class="size-5" />
            <span>企业数字化人才培养平台</span>
          </div>
          <h1 class="m-0 text-xl font-semibold">云培训学院</h1>
        </div>
        <button
          type="button"
          class="shrink-0 rounded-lg bg-white/15 px-4 py-2 text-sm font-medium text-white backdrop-blur-sm transition hover:bg-white/25"
          @click="router.push('/portal/training')"
        >
          进入学员端 →
        </button>
      </section>

      <section class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Card v-for="item in dashboardMetrics" :key="item.label" :bordered="false">
          <div class="flex items-start justify-between">
            <div>
              <div class="text-sm text-gray-500 dark:text-gray-400">{{ item.label }}</div>
              <div class="mt-2 text-3xl font-semibold text-gray-900 dark:text-gray-100">{{ item.value }}</div>
              <div class="mt-2 text-xs text-gray-400">{{ item.trend }}</div>
            </div>
            <div class="flex size-11 items-center justify-center rounded-xl" :style="{ backgroundColor: `${item.color}18`, color: item.color }">
              <IconifyIcon :icon="item.icon" class="size-6" />
            </div>
          </div>
        </Card>
      </section>

      <section class="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        <Card :bordered="false">
          <template #title>
            <div>
              <div class="font-semibold">快捷入口</div>
              <div class="mt-1 text-xs font-normal text-gray-400">云培训学院九个一级业务模块</div>
            </div>
          </template>
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <button
              v-for="item in trainingModules"
              :key="item.key"
              type="button"
              class="group flex min-h-28 cursor-pointer items-start gap-3 rounded-xl border border-gray-100 bg-transparent p-4 text-left transition hover:-translate-y-0.5 hover:border-transparent hover:shadow-lg dark:border-gray-700"
              @click="router.push(item.path)"
            >
              <div class="flex size-10 shrink-0 items-center justify-center rounded-xl transition group-hover:scale-105" :style="{ backgroundColor: `${item.accent}18`, color: item.accent }">
                <IconifyIcon :icon="item.icon" class="size-5" />
              </div>
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <span class="font-medium text-gray-900 dark:text-gray-100">{{ item.name }}</span>
                  <span class="text-xs text-gray-300">{{ item.number }}</span>
                </div>
                <p class="mb-0 mt-2 line-clamp-2 text-xs leading-5 text-gray-500 dark:text-gray-400">{{ item.description }}</p>
              </div>
            </button>
          </div>
        </Card>

        <Card title="我的待办" :bordered="false">
          <div class="space-y-2">
            <div v-for="todo in todos" :key="todo.name" class="flex items-center justify-between rounded-xl px-2 py-3 transition hover:bg-gray-50 dark:hover:bg-gray-800">
              <div class="flex items-center gap-3">
                <div class="flex size-9 items-center justify-center rounded-lg" :style="{ backgroundColor: `${todo.color}16`, color: todo.color }">
                  <IconifyIcon :icon="todo.icon" class="size-5" />
                </div>
                <span class="text-sm">{{ todo.name }}</span>
              </div>
              <Badge :count="todo.count" show-zero :number-style="{ backgroundColor: todo.count ? todo.color : '#d1d5db' }" />
            </div>
          </div>
        </Card>
      </section>

      <section class="grid gap-4 lg:grid-cols-2">
        <Card title="学习数据看板" :bordered="false">
          <div class="flex min-h-48 flex-col items-center justify-center rounded-xl bg-gray-50 text-center dark:bg-gray-800/50">
            <div class="flex size-14 items-center justify-center rounded-2xl bg-blue-100 text-blue-600 dark:bg-blue-950">
              <IconifyIcon icon="lucide:chart-spline" class="size-7" />
            </div>
            <div class="mt-4 font-medium">学习数据正在等待接入</div>
            <div class="mt-1 text-sm text-gray-400">课程、培训和考试产生数据后，这里将自动形成趋势分析</div>
          </div>
        </Card>
        <Card title="消息中心" :bordered="false">
          <div class="space-y-1">
            <div v-for="message in messages" :key="message.title" class="flex items-center gap-3 rounded-xl px-2 py-3 hover:bg-gray-50 dark:hover:bg-gray-800">
              <Avatar :style="{ backgroundColor: '#f3f4f6', color: '#4f46e5' }">
                <template #icon><IconifyIcon icon="lucide:bell-ring" /></template>
              </Avatar>
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium">{{ message.title }}</div>
                <div class="mt-1 text-xs text-gray-400">{{ message.time }}</div>
              </div>
              <Tag :color="message.color">{{ message.type }}</Tag>
            </div>
          </div>
        </Card>
      </section>
    </div>
  </Page>
</template>
