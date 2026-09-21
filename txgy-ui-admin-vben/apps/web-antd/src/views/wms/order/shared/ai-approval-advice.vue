<script lang="ts" setup>
/**
 * AI 辅助审批建议面板
 *
 * 审批人在 BPM 审批详情页点击「AI 审批建议」后展示：单据摘要、风险点、建议结论与理由。
 * 只做参考，不出单据状态、不代替人工审批 —— 是否通过仍由审批人自行决定。
 */
import type { WmsAiApi } from '#/api/wms/ai';

import { ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Alert, Button, message, Spin } from 'ant-design-vue';

import { approvalAdvice } from '#/api/wms/ai';

const props = defineProps<{
  /** 单据主键 */
  orderId?: number | string;
  /** RECEIPT 入库 / SHIPMENT 出库 */
  orderType: 'RECEIPT' | 'SHIPMENT';
}>();

const loading = ref(false);
const advice = ref<WmsAiApi.ApprovalAdviceRespVO>();
const requested = ref(false);

/** 结论 → 展示样式 */
function suggestionStyle(s?: string) {
  switch (s) {
    case 'APPROVE': {
      return {
        alert: 'success' as const,
        icon: 'lucide:circle-check-big',
        text: 'text-green-600',
      };
    }
    case 'REJECT': {
      return {
        alert: 'error' as const,
        icon: 'lucide:circle-x',
        text: 'text-red-600',
      };
    }
    default: {
      return {
        alert: 'warning' as const,
        icon: 'lucide:circle-alert',
        text: 'text-amber-600',
      };
    }
  }
}

/** 风险级别 → 标签色 */
function riskClass(level?: string) {
  switch (level) {
    case 'HIGH': {
      return 'border-red-200 bg-red-50 text-red-700';
    }
    case 'MEDIUM': {
      return 'border-amber-200 bg-amber-50 text-amber-700';
    }
    default: {
      return 'border-gray-200 bg-gray-50 text-gray-600';
    }
  }
}

function riskLabel(level?: string) {
  return { HIGH: '高', MEDIUM: '中', LOW: '低' }[level ?? ''] ?? '低';
}

async function load() {
  if (!props.orderId) {
    message.warning('缺少单据编号，无法生成建议');
    return;
  }
  loading.value = true;
  requested.value = true;
  try {
    advice.value = await approvalAdvice({
      orderId: Number(props.orderId),
      orderType: props.orderType,
    });
  } catch (error: any) {
    message.error(error?.message || 'AI 审批建议获取失败');
    advice.value = undefined;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="rounded border border-gray-200 bg-white p-3">
    <div class="flex flex-wrap items-center gap-3">
      <Button :loading="loading" type="primary" @click="load">
        <IconifyIcon class="mr-1 size-4" icon="lucide:sparkles" />
        AI 审批建议
      </Button>
      <span class="text-xs text-gray-400">
        读取本单与账面库存做分析，仅供审批参考，不会自动通过或驳回
      </span>
    </div>

    <div v-if="loading" class="mt-3 flex items-center gap-2 text-sm text-gray-400">
      <Spin size="small" />
      <span>AI 正在分析单据与库存，请稍候…</span>
    </div>

    <template v-else-if="requested && advice">
      <!-- 结论 -->
      <Alert
        v-if="advice.ok !== false"
        class="mt-3"
        :type="suggestionStyle(advice.suggestion).alert"
        show-icon
      >
        <template #message>
          <span class="font-medium">
            {{ advice.suggestionLabel || '需要人工判断' }}
          </span>
          <span v-if="advice.detailCount !== undefined" class="ml-2 text-xs opacity-70">
            （参与分析明细 {{ advice.detailCount }} 行）
          </span>
        </template>
        <template #description>
          <div>{{ advice.reason }}</div>
        </template>
      </Alert>

      <Alert
        v-else
        class="mt-3"
        type="error"
        show-icon
        :message="advice.errorMessage || 'AI 建议不可用'"
      />

      <!-- 摘要 -->
      <div v-if="advice.summary" class="mt-3 text-sm text-gray-700">
        <span class="text-gray-400">单据摘要：</span>{{ advice.summary }}
      </div>

      <!-- 风险列表 -->
      <div v-if="advice.risks?.length" class="mt-3">
        <div class="mb-1 text-sm font-medium text-gray-700">
          需关注事项（{{ advice.risks.length }}）
        </div>
        <ul class="space-y-1.5">
          <li
            v-for="(r, i) in advice.risks"
            :key="i"
            class="flex items-start gap-2 rounded border px-2 py-1.5 text-xs"
            :class="riskClass(r.level)"
          >
            <span class="mt-0.5 shrink-0 rounded px-1 font-medium">
              {{ riskLabel(r.level) }}
            </span>
            <span>
              <span class="font-medium">{{ r.title }}</span>
              <span v-if="r.detail"> —— {{ r.detail }}</span>
            </span>
          </li>
        </ul>
      </div>
      <div v-else-if="advice.ok !== false" class="mt-3 text-sm text-green-600">
        <IconifyIcon class="mr-1 inline size-4" icon="lucide:check" />未发现明显异常。
      </div>

      <div
        v-if="advice.errorMessage && advice.ok !== false"
        class="mt-2 text-xs text-amber-600"
      >
        {{ advice.errorMessage }}
      </div>
    </template>
  </div>
</template>
