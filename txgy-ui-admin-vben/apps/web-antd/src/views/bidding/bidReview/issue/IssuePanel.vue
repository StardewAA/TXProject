<script lang="ts" setup>
/**
 * 管理端 · 标书审查 - 问题复核（菜单 60007450，本次新增）
 *
 * 方案依据：《标书审查-管理端重新设计》§4.3.2。
 *
 * 为什么要有这一页：改造前复核入口藏在「某个任务的问题清单抽屉」里，复核人必须先知道是哪个任务；
 * 结果就是库里 42 条问题项 100% 停在 OPEN、复核动作从未发生。
 * 这里把"待复核问题"做成跨任务工作台——打开即待办（默认 status=OPEN）。
 *
 * 后端复用 GET /issue/page（taskId 可空即跨任务），无需为跨任务查询加接口。
 */
import { onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { Alert, Button, Card, Space, Statistic } from 'ant-design-vue';

import {
  type BidReviewApi,
  getReviewIssueStats,
  getReviewTaskPage,
} from '#/api/bidding/bidReview';

import BidReviewIssueTable from '../components/BidReviewIssueTable.vue';
import { useBiddingDict } from '../composables/useBiddingDict';
import '../panel.css';

const { error: dictError } = useBiddingDict();

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const stats = ref<BidReviewApi.IssueStats | null>(null);
const taskOptions = ref<Array<{ label: string; value: number }>>([]);
const tableRef = ref<InstanceType<typeof BidReviewIssueTable> | null>(null);

async function loadStats() {
  try {
    stats.value = await getReviewIssueStats();
  } catch {
    // 统计是增强信息：接口不可用时页面仍应能复核
    stats.value = null;
  }
}

async function loadTaskOptions() {
  try {
    const page = await getReviewTaskPage({ pageNo: 1, pageSize: 50 });
    taskOptions.value = (page.list ?? []).map((task) => ({
      label: `${task.taskNo} · ${task.taskName}`,
      value: task.id,
    }));
  } catch {
    taskOptions.value = [];
  }
}

async function onReviewed() {
  await loadStats();
  // 复核会把任务结论重算，任务下拉里的名称/结论可能变化，一并刷新
  await loadTaskOptions();
}

function refreshAll() {
  void loadStats();
  tableRef.value?.search();
}

onMounted(() => {
  void loadStats();
  void loadTaskOptions();
});
</script>

<template>
  <div class="br-page">
    <Alert
      v-if="dictError"
      type="warning"
      show-icon
      style="margin-bottom: 12px"
      message="枚举字典未取到，已降级为本地文案"
      :description="`原因：${dictError}（枚举以 bidding_dict_data 的 bid_review_* 为准）`"
    />

    <div class="br-stats">
      <a-row :gutter="12">
        <a-col :span="4">
          <Statistic title="待复核" :value="stats?.openCount ?? 0" />
        </a-col>
        <a-col :span="4">
          <Statistic title="已确认" :value="stats?.confirmedCount ?? 0" />
        </a-col>
        <a-col :span="4">
          <Statistic title="已忽略（误报）" :value="stats?.ignoredCount ?? 0" />
        </a-col>
        <a-col :span="4">
          <Statistic title="已整改" :value="stats?.fixedCount ?? 0" />
        </a-col>
        <a-col :span="4">
          <Statistic title="证据不足" :value="stats?.evidenceLackCount ?? 0" />
        </a-col>
        <a-col :span="4">
          <Statistic title="问题项总数" :value="stats?.totalCount ?? 0" />
        </a-col>
      </a-row>
    </div>

    <Card size="small" title="待复核问题（跨任务）">
      <template #extra>
        <Space>
          <span class="br-sub">默认只显示「待复核」；忽略=判定为误报，任务结论会自动重算</span>
          <Button size="small" @click="refreshAll">刷新</Button>
        </Space>
      </template>

      <BidReviewIssueTable
        ref="tableRef"
        show-task
        :audit="can('bidding:bid-review:audit')"
        :task-options="taskOptions"
        default-status="OPEN"
        :page-size="10"
        @reviewed="onReviewed"
      />
    </Card>
  </div>
</template>
