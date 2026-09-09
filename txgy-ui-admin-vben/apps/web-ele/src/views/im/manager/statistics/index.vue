<script lang="ts" setup>
import type { ImManagerStatisticsApi } from '#/api/im/manager/statistics';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { Page } from '@vben/common-ui';

import { ElButton } from 'element-plus';

import { getStatisticsOverview } from '#/api/im/manager/statistics';

import { DistributionChart, OverviewCards, TrendChart } from './components';

defineOptions({ name: 'ImManagerStatistics' });

const router = useRouter();

const overview = ref<ImManagerStatisticsApi.Overview>();

/** 加载概览数据 */
async function loadOverview() {
  overview.value = await getStatisticsOverview();
}

/** 新标签页打开 IM 聊天工作台 */
function handleOpenWorkbench() {
  const { href } = router.resolve({ name: 'ImHome' });
  window.open(href, '_blank');
}

onMounted(loadOverview);
</script>

<template>
  <Page auto-content-height>
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="text-base font-medium">IM 数据统计</div>
        <ElButton type="primary" plain @click="handleOpenWorkbench">
          <IconifyIcon
            class="mr-1 size-4"
            icon="lucide:message-circle"
          />
          聊天工作台
        </ElButton>
      </div>

      <OverviewCards v-if="overview" :overview="overview" />

      <div class="grid grid-cols-2 gap-4 max-xl:grid-cols-1">
        <TrendChart type="message" />
        <TrendChart type="user" />
      </div>

      <div
        class="grid grid-cols-3 gap-4 max-2xl:grid-cols-2 max-xl:grid-cols-1"
      >
        <DistributionChart type="messageType" />
        <DistributionChart type="groupSize" />
        <DistributionChart type="topSenders" />
      </div>
    </div>
  </Page>
</template>
