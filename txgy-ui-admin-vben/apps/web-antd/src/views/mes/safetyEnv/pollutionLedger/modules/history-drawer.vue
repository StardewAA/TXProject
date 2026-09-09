<script lang="ts" setup>
import type { MesPollutionLedgerApi } from '#/api/mes/safetyEnv/pollutionLedger';
import type { MesSetPollutionCheckApi } from '#/api/mes/safetyEnv/pollutionCheck';

import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Empty, Spin, Tag, Timeline, TimelineItem } from 'ant-design-vue';

import { getPollutionCheck } from '#/api/mes/safetyEnv/pollutionCheck';
import { getPollutionLedgerHistory } from '#/api/mes/safetyEnv/pollutionLedger';

import {
  AI_RESULT_MAP,
  DISPOSITION_MAP,
  REVIEW_RESULT_MAP,
  STAGE_MAP,
} from '../../pollutionCheck/data';
import { LEDGER_STATUS_MAP } from '../data';

/** 当前台账行(来自列表) */
const row = ref<MesPollutionLedgerApi.Ledger | null>(null);
/** 流转历史(登记→处置→闭环，时间正序) */
const logs = ref<MesPollutionLedgerApi.LedgerHistoryLog[]>([]);
/** 来源判定记录(AI 初筛 + 人工复核全文) */
const sourceCheck = ref<MesSetPollutionCheckApi.PollutionCheck | null>(null);
const loading = ref(false);

function fmt(t?: number): string {
  return t != null ? formatDateTime(t) : '-';
}

function disLabel(v?: string): string {
  return v ? (DISPOSITION_MAP[v] ?? v) : '-';
}

async function loadLogs(ledgerId?: number) {
  logs.value = [];
  if (!ledgerId) {
    return;
  }
  try {
    logs.value = (await getPollutionLedgerHistory(ledgerId)) ?? [];
  } catch {
    logs.value = [];
  }
}

async function loadSourceCheck(sourceCheckId?: number) {
  sourceCheck.value = null;
  if (!sourceCheckId) {
    return;
  }
  try {
    sourceCheck.value = (await getPollutionCheck(sourceCheckId)) ?? null;
  } catch {
    sourceCheck.value = null;
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  showConfirmButton: false,
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    const data = drawerApi.getData<{ row: MesPollutionLedgerApi.Ledger }>();
    row.value = data?.row ?? null;
    loading.value = true;
    try {
      await Promise.all([loadLogs(row.value?.id), loadSourceCheck(row.value?.sourceCheckId)]);
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Drawer title="污染/危废台账 · 流转追溯" class="w-[680px]">
    <Spin :spinning="loading">
      <!-- 台账概要 -->
      <div v-if="row" class="mb-4 rounded-md border border-border bg-card px-4 py-3 text-sm leading-7">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-medium">{{ row.itemName ?? '-' }}</span>
          <Tag v-if="row.stage" color="blue">{{ STAGE_MAP[row.stage] ?? row.stage }}</Tag>
          <Tag v-if="row.status" :color="LEDGER_STATUS_MAP[row.status]?.color">
            {{ LEDGER_STATUS_MAP[row.status]?.text ?? row.status }}
          </Tag>
          <Tag v-if="row.marked" color="orange">已标记</Tag>
        </div>
        <div class="text-xs text-muted-foreground">
          来源判定 {{ row.sourceRecordNo ?? '-' }}｜关联单号 {{ row.bizNo ?? '-' }}｜批次
          {{ row.batchNo ?? '-' }}｜去向/库位 {{ row.location ?? '-' }}｜登记于
          {{ fmt(row.createTime) }}
        </div>
      </div>

      <!-- 来源判定(判=有污染 触发登记) -->
      <div
        v-if="sourceCheck"
        class="mb-4 rounded-md border-l-4 border-l-blue-500/70 bg-card px-4 py-3 text-xs leading-6"
      >
        <div class="mb-1 flex flex-wrap items-center gap-2 text-sm">
          <b>来源污染判定</b>
          <span class="text-muted-foreground">{{ sourceCheck.recordNo ?? '-' }}</span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-muted-foreground">AI 初筛</span>
          <Tag :color="AI_RESULT_MAP[sourceCheck.aiResult ?? '']?.color" class="!mr-0">
            {{ AI_RESULT_MAP[sourceCheck.aiResult ?? '']?.text ?? sourceCheck.aiResult ?? '-' }}
          </Tag>
          <span class="text-muted-foreground">置信度 {{ sourceCheck.aiConfidence ?? '-' }}%</span>
          <span v-if="sourceCheck.aiReason" class="text-muted-foreground">
            ｜依据：{{ sourceCheck.aiReason }}
          </span>
        </div>
        <div v-if="sourceCheck.reviewResult" class="mt-1 flex flex-wrap items-center gap-2">
          <span class="text-muted-foreground">人工复核</span>
          <Tag
            :color="REVIEW_RESULT_MAP[sourceCheck.reviewResult]?.color"
            class="!mr-0"
          >
            {{ REVIEW_RESULT_MAP[sourceCheck.reviewResult]?.text }}
          </Tag>
          <span class="text-muted-foreground">
            处置方式：{{ disLabel(sourceCheck.disposition) }} ｜ 去向：{{ sourceCheck.location ?? '-' }}
          </span>
          <span v-if="sourceCheck.reviewBy" class="text-muted-foreground">
            复核人 {{ sourceCheck.reviewBy }} @ {{ fmt(sourceCheck.reviewTime) }}
          </span>
        </div>
        <div v-if="sourceCheck.remark" class="mt-1 text-muted-foreground">
          判定备注：{{ sourceCheck.remark }}
        </div>
      </div>

      <div class="mb-2 text-sm font-medium">流转历史（登记 → 处置 → 闭环，只增不改）</div>
      <Timeline v-if="logs.length">
        <TimelineItem
          v-for="log in logs"
          :key="log.id"
          color="blue"
        >
          <div class="flex flex-wrap items-center gap-1 text-sm">
            <template v-if="log.fromStatus">
              <Tag :color="LEDGER_STATUS_MAP[log.fromStatus]?.color" class="!mr-0">
                {{ LEDGER_STATUS_MAP[log.fromStatus]?.text ?? log.fromStatus }}
              </Tag>
              <span class="text-muted-foreground">→</span>
            </template>
            <template v-else>
              <span class="text-muted-foreground">初登</span>
              <span class="text-muted-foreground">→</span>
            </template>
            <Tag :color="LEDGER_STATUS_MAP[log.toStatus ?? '']?.color" class="!mr-0">
              {{ LEDGER_STATUS_MAP[log.toStatus ?? '']?.text ?? log.toStatus ?? '-' }}
            </Tag>
            <span class="ml-1 text-xs text-muted-foreground">{{ log.operator ?? '-' }}</span>
            <span class="text-xs text-muted-foreground">{{ fmt(log.opTime) }}</span>
          </div>
          <div v-if="log.remark" class="mt-1 text-xs text-muted-foreground">{{ log.remark }}</div>
        </TimelineItem>
      </Timeline>
      <Empty
        v-else-if="!loading"
        description="该台账早于留痕功能上线，暂无流转历史"
      />
    </Spin>
  </Drawer>
</template>
