<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Card, Spin } from 'ant-design-vue';

import { getSiteOverview } from '#/api/mes/pro/andon/record';
import type { MesProAndonRecordApi } from '#/api/mes/pro/andon/record';

defineOptions({ name: 'MesProAndonSiteOverview' });

const router = useRouter();
const loading = ref(false);
const overview = ref<MesProAndonRecordApi.MesProAndonRecordOverview>({
  pendingCount: 0,
  overdueCount: 0,
  escalatedCount: 0,
  handledTodayCount: 0,
  statisticsTime: '',
});

const hasOverdue = computed(() => (overview.value.overdueCount ?? 0) > 0);

/** 加载现场异常总览（未处置/超时/已升级/今日已处置） */
async function loadData() {
  loading.value = true;
  try {
    overview.value = await getSiteOverview();
  } finally {
    loading.value = false;
  }
}

/** 跳转到安灯呼叫记录列表，便于处置异常 */
function goRecord() {
  router.push('/mes/pro/andon/record');
}

onMounted(loadData);
</script>

<template>
  <div class="p-4">
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span class="text-base font-semibold">现场过程状态统一看板</span>
        <span
          v-if="hasOverdue"
          class="rounded bg-red-500 px-2 py-0.5 text-xs text-white"
        >
          存在超时未处置异常
        </span>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-400">
          统计时间：{{ overview.statisticsTime || '-' }}
        </span>
        <Button size="small" @click="loadData">刷新</Button>
        <Button type="primary" size="small" @click="goRecord">
          查看安灯记录
        </Button>
      </div>
    </div>

    <Spin :spinning="loading">
      <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Card :bordered="hasOverdue" :class="hasOverdue ? 'border-red-500' : ''">
          <div class="text-center">
            <div
              class="text-3xl font-bold"
              :class="hasOverdue ? 'text-red-500' : 'text-gray-800'"
            >
              {{ overview.overdueCount }}
            </div>
            <div class="mt-1 text-sm text-gray-500">超时未处置</div>
            <div v-if="hasOverdue" class="mt-1 text-xs text-red-500">
              已超 SLA，需立即处理
            </div>
          </div>
        </Card>

        <Card>
          <div class="text-center">
            <div class="text-3xl font-bold text-red-500">
              {{ overview.pendingCount }}
            </div>
            <div class="mt-1 text-sm text-gray-500">未处置总数</div>
          </div>
        </Card>

        <Card>
          <div class="text-center">
            <div class="text-3xl font-bold text-orange-500">
              {{ overview.escalatedCount }}
            </div>
            <div class="mt-1 text-sm text-gray-500">已超时升级</div>
          </div>
        </Card>

        <Card>
          <div class="text-center">
            <div class="text-3xl font-bold text-green-600">
              {{ overview.handledTodayCount }}
            </div>
            <div class="mt-1 text-sm text-gray-500">今日已处置</div>
          </div>
        </Card>
      </div>
    </Spin>
  </div>
</template>
