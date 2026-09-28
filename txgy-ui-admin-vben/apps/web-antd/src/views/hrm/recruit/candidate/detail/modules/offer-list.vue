<script lang="ts" setup>
import type { HrmRecruitOfferApi } from '#/api/hrm/recruit/offer';

import { onMounted, ref } from 'vue';

import { getRecruitOfferListByCandidate, resendOfferNotice } from '#/api/hrm/recruit/offer';

import { message } from 'ant-design-vue';

const props = defineProps<{ candidateId: number }>();

const loading = ref(false);
const offers = ref<HrmRecruitOfferApi.Offer[]>([]);

/**
 * 状态文案与颜色沿用 offer-status-form.vue 的规范实现（阶段四 4.4），
 * 保证 Offer 在列表页与候选人详情里显示的语义完全一致。
 */
const statusLabels: Record<string, string> = {
  APPROVED: '审批通过',
  APPROVING: '审批中',
  CANDIDATE_REJECTED: '求职者已拒绝',
  CONFIRMED: '求职者已接受',
  DRAFT: '草稿',
  EXPIRED: '已过期',
  REJECTED: '审批未通过',
  SENT: '已发送',
  WITHDRAWN: '已撤回',
};

function statusLabel(status?: string) {
  return statusLabels[status || ''] || '未知状态';
}

function statusColor(status?: string) {
  if (['CANDIDATE_REJECTED', 'EXPIRED', 'REJECTED'].includes(status || '')) {
    return 'error';
  }
  if (status === 'APPROVING') {
    return 'warning';
  }
  if (['APPROVED', 'CONFIRMED'].includes(status || '')) {
    return 'success';
  }
  if (status === 'SENT') {
    return 'processing';
  }
  return 'default';
}

function formatSalary(offer: HrmRecruitOfferApi.Offer) {
  if (!offer.salaryAmount) {
    return '-';
  }
  return `${offer.salaryAmount} ${offer.salaryUnit === 2 ? '元/年' : '元/月'}`;
}

function formatDate(value?: string) {
  return value ? value.slice(0, 10) : '-';
}

/** 录用通知发送结果：HR 直接在 Offer 上看到成功/失败原因，并可一键重发（交接文档 ①） */
const notifyLabels: Record<number, string> = {
  0: '未发送',
  1: '已发送',
  2: '发送失败',
};

function notifyLabel(status?: number) {
  return status === undefined || status === null ? '未发送' : notifyLabels[status] || '未知';
}

function notifyColor(status?: number) {
  if (status === 1) return 'success';
  if (status === 2) return 'error';
  return 'default';
}

const resending = ref<number>();

async function handleResend(offer: HrmRecruitOfferApi.Offer) {
  resending.value = offer.id;
  try {
    await resendOfferNotice(offer.id);
    message.success('已重新发送录用通知（结果见下方通知状态）');
    await loadOffers();
  } catch {
    message.error('重发失败，请检查候选人邮箱与邮件配置');
  } finally {
    resending.value = undefined;
  }
}

function canResend(offer: HrmRecruitOfferApi.Offer) {
  return ['SENT', 'CONFIRMED'].includes(offer.approvalStatus || '');
}

async function loadOffers() {
  if (!props.candidateId) {
    return;
  }
  loading.value = true;
  try {
    offers.value = (await getRecruitOfferListByCandidate(props.candidateId)) || [];
  } finally {
    loading.value = false;
  }
}

onMounted(loadOffers);
</script>

<template>
  <a-spin :spinning="loading">
    <a-empty
      v-if="!loading && offers.length === 0"
      description="该候选人暂无 Offer 记录"
    />
    <div v-for="offer in offers" :key="offer.id" class="mb-4">
      <div class="mb-2 flex items-center justify-between">
        <strong>{{ offer.offerNo || `Offer #${offer.id}` }}</strong>
        <div class="flex items-center gap-2">
          <a-tag :color="statusColor(offer.approvalStatus)">
            {{ statusLabel(offer.approvalStatus) }}
          </a-tag>
          <a-tag :color="notifyColor(offer.notifyStatus)">
            通知：{{ notifyLabel(offer.notifyStatus) }}
          </a-tag>
          <a-button
            v-if="canResend(offer)"
            :loading="resending === offer.id"
            size="small"
            type="link"
            @click="handleResend(offer)"
          >
            重发通知
          </a-button>
        </div>
      </div>
      <a-descriptions
        bordered
        size="small"
        :column="{ lg: 2, md: 2, sm: 1, xs: 1 }"
      >
        <a-descriptions-item label="Offer 薪资">
          {{ formatSalary(offer) }}
        </a-descriptions-item>
        <a-descriptions-item label="计划入职">
          {{ formatDate(offer.plannedOnboardDate) }}
        </a-descriptions-item>
        <a-descriptions-item label="有效期至">
          {{ formatDate(offer.validUntil) }}
        </a-descriptions-item>
        <a-descriptions-item label="已转员工">
          {{ offer.employeeId ? `是（${offer.employeeId}）` : '否' }}
        </a-descriptions-item>
        <a-descriptions-item label="通知发送时间">
          {{ offer.notifyTime ? formatDate(String(offer.notifyTime)) : '-' }}
        </a-descriptions-item>
        <a-descriptions-item v-if="offer.notifyFailReason" label="发送失败原因" :span="2">
          <span class="text-red-500">{{ offer.notifyFailReason }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="备注" :span="2">
          {{ offer.remark || '-' }}
        </a-descriptions-item>
      </a-descriptions>
    </div>
  </a-spin>
</template>
