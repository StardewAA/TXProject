<script setup lang="ts">
/**
 * 报工单的 BPM 审批查看组件
 *
 * 由 BPM 流程详情页按模型元数据中的 formCustomViewPath 动态加载，
 * 入参 id 为流程的 businessKey —— 报工单 id（见 MesProFeedbackServiceImpl#submitFeedback）
 * 参考实现：views/wms/order/receipt/bpm-detail.vue
 */
import type { MesProFeedbackApi } from '#/api/mes/pro/feedback';

import { ref, watch } from 'vue';

import { Alert, Descriptions, Spin } from 'ant-design-vue';

import { getFeedback } from '#/api/mes/pro/feedback';

const props = defineProps<{ id?: number | string }>();
defineEmits<{ navigate: [] }>();

const feedback = ref<MesProFeedbackApi.Feedback>({});
const loading = ref(false);

/** 状态：0 草稿 / 2 审批中 / 3 待检验 / 4 已完成 / 5 已取消 */
const STATUS_LABELS: Record<number, string> = {
  0: '草稿',
  2: '审批中',
  3: '待检验',
  4: '已完成',
  5: '已取消',
};

/** 时间字段可能是时间戳或字符串，两种都兜住 */
function formatTime(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const date = new Date(
    typeof value === 'number' ? value : value.replaceAll('-', '/'),
  );
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/** 数量字段空值统一显示 —— */
function formatQty(value?: null | number) {
  return value === null || value === undefined ? '-' : String(value);
}

async function refresh() {
  if (!props.id) {
    return;
  }
  loading.value = true;
  try {
    feedback.value = await getFeedback(Number(props.id));
  } finally {
    loading.value = false;
  }
}

watch(() => props.id, refresh, { immediate: true });
</script>

<template>
  <Spin :spinning="loading">
    <div class="space-y-4 p-4">
      <Alert
        type="info"
        show-icon
        message="报工单信息（仅供参考，审批结果将自动回写至 MES 报工单）"
      />

      <Descriptions bordered size="small" :column="2" title="报工单">
        <Descriptions.Item label="报工单编号">
          {{ feedback.code || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="状态">
          {{ STATUS_LABELS[feedback.status ?? 0] || '未知' }}
        </Descriptions.Item>
        <Descriptions.Item label="生产工单">
          {{ feedback.workOrderCode || '-' }}
          {{ feedback.workOrderName ? `（${feedback.workOrderName}）` : '' }}
        </Descriptions.Item>
        <Descriptions.Item label="产品物料">
          {{ feedback.itemCode || '-' }}
          {{ feedback.itemName ? `（${feedback.itemName}）` : '' }}
        </Descriptions.Item>
        <Descriptions.Item label="规格型号">
          {{ feedback.itemSpecification || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="工序">
          {{ feedback.processName || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="工作站">
          {{ feedback.workstationName || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="报工人">
          {{ feedback.feedbackUserNickname || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="报工时间">
          {{ formatTime(feedback.feedbackTime) }}
        </Descriptions.Item>
        <Descriptions.Item label="是否需检验">
          {{
            feedback.checkFlag === null || feedback.checkFlag === undefined
              ? '-'
              : feedback.checkFlag
                ? '需检验'
                : '免检'
          }}
        </Descriptions.Item>
        <Descriptions.Item label="本次报工数量">
          {{ formatQty(feedback.feedbackQuantity) }}
          {{ feedback.unitMeasureName || '' }}
        </Descriptions.Item>
        <Descriptions.Item label="排产数量">
          {{ formatQty(feedback.scheduledQuantity) }}
        </Descriptions.Item>
      </Descriptions>

      <Descriptions bordered size="small" :column="2" title="数量明细">
        <Descriptions.Item label="合格品数量">
          {{ formatQty(feedback.qualifiedQuantity) }}
        </Descriptions.Item>
        <Descriptions.Item label="不良品数量">
          {{ formatQty(feedback.unqualifiedQuantity) }}
        </Descriptions.Item>
        <Descriptions.Item label="工废数量">
          {{ formatQty(feedback.laborScrapQuantity) }}
        </Descriptions.Item>
        <Descriptions.Item label="料废数量">
          {{ formatQty(feedback.materialScrapQuantity) }}
        </Descriptions.Item>
        <Descriptions.Item label="其他废品数量">
          {{ formatQty(feedback.otherScrapQuantity) }}
        </Descriptions.Item>
        <Descriptions.Item label="待检测数量">
          {{ formatQty(feedback.uncheckQuantity) }}
        </Descriptions.Item>
      </Descriptions>

      <Descriptions bordered size="small" :column="1" title="备注">
        <Descriptions.Item label="备注">
          {{ feedback.remark || '-' }}
        </Descriptions.Item>
      </Descriptions>
    </div>
  </Spin>
</template>
