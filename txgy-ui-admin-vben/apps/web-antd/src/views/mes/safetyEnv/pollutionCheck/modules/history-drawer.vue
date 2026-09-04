<script lang="ts" setup>
import type { MesPollutionLedgerApi } from '#/api/mes/safetyEnv/pollutionLedger';
import type { MesSetPollutionCheckApi } from '#/api/mes/safetyEnv/pollutionCheck';

import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Alert, Empty, Spin, Tag, Timeline, TimelineItem } from 'ant-design-vue';

import { getPollutionCheckHistory } from '#/api/mes/safetyEnv/pollutionCheck';
import { getPollutionLedgerPage } from '#/api/mes/safetyEnv/pollutionLedger';

import { LEDGER_STATUS_MAP } from '../../pollutionLedger/data';
import {
  AI_RESULT_MAP,
  DISPOSITION_MAP,
  REVIEW_RESULT_MAP,
  STAGE_MAP,
} from '../data';

/** 操作类型展示 */
const OP_TYPE_META: Record<string, { text: string; color: string }> = {
  CREATE: { text: '创建 · AI 初筛', color: 'blue' },
  UPDATE: { text: '改单 · AI 重筛', color: 'orange' },
  REVIEW: { text: '人工复核', color: 'green' },
};

/** 当前判定行(来自列表) */
const row = ref<MesSetPollutionCheckApi.PollutionCheck | null>(null);
/** 履历(创建→改单→复核，时间正序) */
const logs = ref<MesSetPollutionCheckApi.CheckHistoryLog[]>([]);
/** 若复核=有污染，联动展示自动登记的台账行 */
const linkedLedger = ref<MesPollutionLedgerApi.Ledger | null>(null);
const loading = ref(false);

function fmt(t?: number): string {
  return t != null ? formatDateTime(t) : '-';
}

function disLabel(v?: string): string {
  return v ? (DISPOSITION_MAP[v] ?? v) : '-';
}

async function loadLogs(checkId?: number) {
  logs.value = [];
  if (!checkId) {
    return;
  }
  try {
    logs.value = (await getPollutionCheckHistory(checkId)) ?? [];
  } catch {
    logs.value = [];
  }
}

async function loadLinkedLedger(recordNo?: string) {
  linkedLedger.value = null;
  if (!recordNo) {
    return;
  }
  try {
    const page = await getPollutionLedgerPage({
      pageNo: 1,
      pageSize: 1,
      sourceRecordNo: recordNo,
    });
    linkedLedger.value = page.list?.[0] ?? null;
  } catch {
    linkedLedger.value = null;
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  showConfirmButton: false,
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    const data = drawerApi.getData<{ row: MesSetPollutionCheckApi.PollutionCheck }>();
    row.value = data?.row ?? null;
    loading.value = true;
    try {
      await Promise.all([loadLogs(row.value?.id), loadLinkedLedger(row.value?.recordNo)]);
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Drawer title="污染判定 · 全链追溯" class="w-[680px]">
    <Spin :spinning="loading">
      <!-- 判定概要 -->
      <div v-if="row" class="mb-4 rounded-md border border-border bg-card px-4 py-3 text-sm leading-7">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-medium">{{ row.recordNo ?? '-' }}</span>
          <span class="text-muted-foreground">{{ row.itemName ?? '-' }}</span>
          <Tag v-if="row.stage" color="blue">{{ STAGE_MAP[row.stage] ?? row.stage }}</Tag>
          <Tag v-if="row.reviewResult" :color="REVIEW_RESULT_MAP[row.reviewResult]?.color">
            已复核 · {{ REVIEW_RESULT_MAP[row.reviewResult]?.text }}
          </Tag>
          <Tag v-else>待复核</Tag>
          <Tag v-if="row.marked" color="orange">已标记</Tag>
        </div>
        <div class="text-xs text-muted-foreground">
          关联单号 {{ row.bizNo ?? '-' }}｜批次 {{ row.batchNo ?? '-' }}｜创建于
          {{ fmt(row.createTime) }}
        </div>
      </div>

      <!-- 有污染复核 → 联动台账 -->
      <Alert
        v-if="linkedLedger"
        type="success"
        show-icon
        class="mb-4"
        message="本记录复核判为「有污染」，已自动登记暂存台账"
        :description="`台账状态：${LEDGER_STATUS_MAP[linkedLedger.status ?? '']?.text ?? linkedLedger.status ?? '-'} ｜ 去向/库位：${linkedLedger.location ?? '-'} ｜ 最近流转：${linkedLedger.statusBy ?? '-'} @ ${fmt(linkedLedger.statusTime)}`"
      />

      <div class="mb-2 text-sm font-medium">操作履历（AI 判断与人工审核每一步留痕，只增不改）</div>
      <Timeline v-if="logs.length">
        <TimelineItem
          v-for="log in logs"
          :key="log.id"
          :color="OP_TYPE_META[log.opType ?? '']?.color ?? 'gray'"
        >
          <div class="mb-1 flex flex-wrap items-center gap-2">
            <Tag :color="OP_TYPE_META[log.opType ?? '']?.color ?? 'default'">
              {{ OP_TYPE_META[log.opType ?? '']?.text ?? log.opType ?? '-' }}
            </Tag>
            <span class="text-xs text-muted-foreground">{{ log.opBy ?? '-' }}</span>
            <span class="text-xs text-muted-foreground">{{ fmt(log.opTime) }}</span>
          </div>

          <!-- AI 建议/判断快照 -->
          <div
            v-if="log.aiResult || log.aiReason || log.suggestedStorage"
            class="rounded-md border-l-4 border-l-blue-500/70 bg-card px-3 py-2 text-xs leading-6"
          >
            <div v-if="log.aiResult" class="flex items-center gap-2">
              <b>AI 判断</b>
              <Tag :color="AI_RESULT_MAP[log.aiResult]?.color" class="!mr-0">
                {{ AI_RESULT_MAP[log.aiResult]?.text ?? log.aiResult }}
              </Tag>
              <span class="text-muted-foreground">置信度 {{ log.aiConfidence ?? '-' }}%</span>
            </div>
            <div v-if="log.aiReason" class="text-muted-foreground">判定依据：{{ log.aiReason }}</div>
            <div v-if="log.suggestedStorage" class="text-muted-foreground">
              AI 推荐存储方法：{{ log.suggestedStorage }}
            </div>
          </div>

          <!-- 人工审核结果快照(仅复核后有) -->
          <div
            v-if="log.reviewResult"
            class="mt-1 rounded-md border-l-4 border-l-green-500/70 bg-card px-3 py-2 text-xs leading-6"
          >
            <div class="flex flex-wrap items-center gap-2">
              <b>人工审核</b>
              <Tag :color="REVIEW_RESULT_MAP[log.reviewResult]?.color" class="!mr-0">
                {{ REVIEW_RESULT_MAP[log.reviewResult]?.text ?? log.reviewResult }}
              </Tag>
              <span class="text-muted-foreground">处置方式：{{ disLabel(log.disposition) }}</span>
              <Tag v-if="log.marked" color="orange">已标记</Tag>
            </div>
            <div v-if="log.storageMethod" class="text-muted-foreground">
              最终存储方法：{{ log.storageMethod }}
            </div>
            <div v-if="log.location" class="text-muted-foreground">去向/库位：{{ log.location }}</div>
            <div v-if="log.remark" class="text-muted-foreground">备注：{{ log.remark }}</div>
          </div>
        </TimelineItem>
      </Timeline>
      <Empty
        v-else-if="!loading"
        description="该记录早于留痕功能上线，暂无操作履历"
      />
    </Spin>
  </Drawer>
</template>
