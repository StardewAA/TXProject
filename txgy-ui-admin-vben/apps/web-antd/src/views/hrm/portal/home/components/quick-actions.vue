<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Card } from 'ant-design-vue';

/**
 * 员工自助「我要」磁贴（QA 报告 I-022）
 *
 * <p>员工端此前要办一件事需要先在左侧菜单里找到对应页面，这里把高频自助入口
 * 收成磁贴，最多 2 次点击即可到达目标页面（手机端同样适用）。</p>
 */
defineOptions({ name: 'HrmPortalQuickActions' });

const router = useRouter();

const actions: Array<{
  description: string;
  icon: string;
  path: string;
  title: string;
}> = [
  {
    title: '我要请假',
    description: '提交请假申请并查看审批进度',
    icon: 'lucide:calendar-clock',
    path: '/hrm/portal/attendance/report',
  },
  {
    title: '我的工资条',
    description: '查看每月工资明细',
    icon: 'lucide:wallet',
    path: '/hrm/portal/salary/slip',
  },
  {
    title: '我的社保',
    description: '查看社保与公积金缴费',
    icon: 'lucide:shield-check',
    path: '/hrm/portal/insurance',
  },
  {
    title: '我的档案',
    description: '维护联系方式与个人资料',
    icon: 'lucide:user-round',
    path: '/hrm/portal/employee',
  },
  {
    title: '我的绩效',
    description: '填写绩效自评与查看结果',
    icon: 'lucide:target',
    path: '/hrm/portal/performance/assessment',
  },
  {
    title: '招聘应聘',
    description: '浏览职位、投递简历与查看进度',
    icon: 'lucide:briefcase-business',
    path: '/hrm/portal/recruit',
  },
];

function open(path: string) {
  router.push(path);
}
</script>

<template>
  <Card class="mt-4" size="small" title="我要办">
    <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
      <button
        v-for="action in actions"
        :key="action.path"
        class="flex items-start gap-2 rounded-md border p-3 text-left transition hover:border-blue-400"
        type="button"
        @click="open(action.path)"
      >
        <IconifyIcon class="mt-0.5 shrink-0 text-blue-500" :icon="action.icon" />
        <span class="min-w-0">
          <span class="block text-sm font-medium">{{ action.title }}</span>
          <span class="block text-xs text-muted-foreground">
            {{ action.description }}
          </span>
        </span>
      </button>
    </div>
  </Card>
</template>
