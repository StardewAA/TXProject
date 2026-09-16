<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenModal } from '@vben/common-ui';

import { Modal as AntModal, message } from 'ant-design-vue';

import {
  getRecruitOffer,
  type HrmRecruitOfferApi,
  sendRecruitOffer,
  submitRecruitOffer,
  withdrawRecruitOffer,
} from '#/api/hrm/recruit/offer';

const emit = defineEmits(['success']);
const offerId = ref<number>();
const offer = ref<HrmRecruitOfferApi.Offer>();
const loading = ref(false);
const actionLoading = ref(false);
const { hasAccessByCodes } = useAccess();

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

const canSubmit = computed(
  () =>
    offer.value?.approvalStatus === 'DRAFT' &&
    hasAccessByCodes(['hrm:recruit:offer:submit']),
);
const canSend = computed(
  () =>
    offer.value?.approvalStatus === 'APPROVED' &&
    hasAccessByCodes(['hrm:recruit:offer:send']),
);
const canWithdraw = computed(() =>
  hasAccessByCodes(['hrm:recruit:offer:withdraw']) &&
  ['APPROVED', 'APPROVING', 'DRAFT', 'SENT'].includes(
    offer.value?.approvalStatus || '',
  ),
);

function statusLabel(status?: string) {
  return statusLabels[status || ''] || '未知状态';
}

function statusColor(status?: string) {
  if (['CANDIDATE_REJECTED', 'EXPIRED', 'REJECTED'].includes(status || '')) {
    return 'error';
  }
  if (status === 'APPROVING') return 'warning';
  if (['APPROVED', 'CONFIRMED'].includes(status || '')) return 'success';
  if (status === 'SENT') return 'processing';
  return 'default';
}

function formatDate(value?: string) {
  return value ? value.slice(0, 10) : '-';
}

function formatSalary() {
  if (!offer.value?.salaryAmount) return '-';
  return `${offer.value.salaryAmount} ${offer.value.salaryUnit === 2 ? '元/年' : '元/月'}`;
}

function errorMessage(error: unknown) {
  return (error as { message?: string })?.message || 'Offer 操作失败，请刷新后重试';
}

async function loadOffer(id = offerId.value) {
  if (!id) return;
  loading.value = true;
  try {
    offer.value = await getRecruitOffer(id);
  } catch (error) {
    offer.value = undefined;
    message.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function runAction(action: () => Promise<unknown>, successText: string) {
  actionLoading.value = true;
  try {
    await action();
    await loadOffer();
    emit('success');
    message.success(successText);
  } catch (error) {
    message.error(errorMessage(error));
  } finally {
    actionLoading.value = false;
  }
}

function handleWithdraw() {
  if (!offerId.value) return;
  AntModal.confirm({
    title: '确认撤回 Offer？',
    content: '撤回后该 Offer 不能继续发送，请确认操作。',
    okText: '确认撤回',
    cancelText: '取消',
    onOk: () =>
      runAction(
        () => withdrawRecruitOffer(offerId.value!, '招聘方撤回 Offer'),
        'Offer 已撤回',
      ),
  });
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      offerId.value = undefined;
      offer.value = undefined;
      return;
    }
    const data = modalApi.getData<{ offerId: number }>();
    offerId.value = data?.offerId;
    await loadOffer();
  },
});
</script>

<template>
  <Modal title="Offer 状态" class="w-[640px]">
    <a-spin :spinning="loading">
      <a-empty v-if="!offer && !loading" description="Offer 不存在或已删除" />
      <template v-else-if="offer">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="Offer 编号">{{ offer.offerNo || '-' }}</a-descriptions-item>
          <a-descriptions-item label="审批状态">
            <a-tag :color="statusColor(offer.approvalStatus)">{{ statusLabel(offer.approvalStatus) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="Offer 薪资">{{ formatSalary() }}</a-descriptions-item>
          <a-descriptions-item label="计划入职">{{ formatDate(offer.plannedOnboardDate) }}</a-descriptions-item>
          <a-descriptions-item label="有效期至">{{ formatDate(offer.validUntil) }}</a-descriptions-item>
          <a-descriptions-item label="审批版本">{{ offer.approvalVersion ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="审批流程实例" :span="2">{{ offer.processInstanceId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">{{ offer.remark || '-' }}</a-descriptions-item>
        </a-descriptions>
        <div class="mt-4 flex justify-end gap-2">
          <a-button :loading="loading" @click="loadOffer()">重新加载状态</a-button>
          <a-button
            v-if="canSubmit"
            :loading="actionLoading"
            @click="runAction(() => submitRecruitOffer(offerId!), 'Offer 已重新提交审批')"
          >
            提交审批
          </a-button>
          <a-button
            v-if="canSend"
            type="primary"
            :loading="actionLoading"
            @click="runAction(() => sendRecruitOffer(offerId!), 'Offer 已发送')"
          >
            发送 Offer
          </a-button>
          <a-button v-if="canWithdraw" danger :loading="actionLoading" @click="handleWithdraw">
            撤回
          </a-button>
        </div>
      </template>
    </a-spin>
  </Modal>
</template>
