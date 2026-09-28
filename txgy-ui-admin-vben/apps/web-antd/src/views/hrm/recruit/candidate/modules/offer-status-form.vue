<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenModal } from '@vben/common-ui';

import { Modal as AntModal, message } from 'ant-design-vue';

import {
  approveRecruitOffer,
  getRecruitOffer,
  type HrmRecruitOfferApi,
  resendOfferNotice,
  sendRecruitOffer,
  submitRecruitOffer,
  withdrawRecruitOffer,
} from '#/api/hrm/recruit/offer';
import {
  exportEmployeeContract,
  getEmployeeContractList,
  type HrmEmployeeContractApi,
} from '#/api/hrm/employee/contract';

import OnboardForm from './onboard-form.vue';

const emit = defineEmits(['success']);
const offerId = ref<number>();
const offer = ref<HrmRecruitOfferApi.Offer>();
const loading = ref(false);
const actionLoading = ref(false);
const contract = ref<HrmEmployeeContractApi.EmployeeContract>();
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
// 未接入 BPM 流程时，由具备审批权限的人直接审批（审批中状态可操作）
const canApprove = computed(
  () =>
    offer.value?.approvalStatus === 'APPROVING' &&
    hasAccessByCodes(['hrm:recruit:offer:approve']),
);
// 求职者已接受且尚未转为员工时，可一键转员工
const canOnboard = computed(
  () =>
    offer.value?.approvalStatus === 'CONFIRMED' &&
    !offer.value?.employeeId &&
    hasAccessByCodes(['hrm:recruit:offer:onboard']),
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
    await loadContract();
  } catch (error) {
    offer.value = undefined;
    message.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

/** 加载该 Offer 生成的劳务合同（转员工后存在） */
async function loadContract() {
  contract.value = undefined;
  const employeeId = offer.value?.employeeId;
  if (!employeeId) return;
  try {
    const list = await getEmployeeContractList(employeeId);
    contract.value = list?.find((item) => item.sourceOfferId === offerId.value);
  } catch {
    contract.value = undefined;
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

function handleApprove() {
  if (!offerId.value) return;
  AntModal.confirm({
    title: '确认审批通过该 Offer？',
    content: '通过后即可发送给求职者。',
    okText: '审批通过',
    cancelText: '取消',
    onOk: () =>
      runAction(
        () => approveRecruitOffer(offerId.value!, true),
        'Offer 已审批通过',
      ),
  });
}

function handleReject() {
  if (!offerId.value) return;
  AntModal.confirm({
    title: '确认驳回该 Offer？',
    content: '驳回后该 Offer 审批不通过，需要撤回后重新发起。',
    okText: '确认驳回',
    okType: 'danger',
    cancelText: '取消',
    onOk: () =>
      runAction(
        () => approveRecruitOffer(offerId.value!, false, '审批未通过'),
        'Offer 已驳回',
      ),
  });
}

const [OnboardModal, onboardModalApi] = useVbenModal({
  connectedComponent: OnboardForm,
  destroyOnClose: true,
});

function handleOnboard() {
  if (!offerId.value || !offer.value) return;
  // 转员工必须先选「用工形式」：它决定签哪份合同、按什么方式计薪
  onboardModalApi
    .setData({
      offerId: offerId.value,
      plannedOnboardDate: offer.value.plannedOnboardDate,
    })
    .open();
}

/** 转员工成功：刷新 Offer 状态并通知外层刷新列表 */
function handleOnboardSuccess() {
  emit('success');
  loadOffer();
}

/** 已发出的 Offer 可以重发录用通知邮件（带 PDF 附件） */
const canResendNotice = computed(
  () =>
    ['SENT', 'CONFIRMED'].includes(offer.value?.approvalStatus || '') &&
    hasAccessByCodes(['hrm:recruit:offer:send']),
);

function handleResendNotice() {
  if (!offerId.value) return;
  runAction(
    () => resendOfferNotice(offerId.value!),
    '录用通知邮件已重新发送（附件为录用通知书 PDF）',
  );
}

async function handleDownloadContract() {
  if (!contract.value?.id) return;
  try {
    await exportEmployeeContract(contract.value.id);
  } catch {
    message.error('合同下载失败，请检查用工主体信息与合同模板');
  }
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
    <OnboardModal @success="handleOnboardSuccess" />
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
            v-if="canOnboard"
            type="primary"
            :loading="actionLoading"
            @click="handleOnboard"
          >
            转为员工
          </a-button>
          <a-button
            v-if="contract?.id"
            :loading="actionLoading"
            @click="handleDownloadContract"
          >
            下载劳务合同
          </a-button>
          <a-button
            v-if="canResendNotice"
            :loading="actionLoading"
            @click="handleResendNotice"
          >
            重发录用通知邮件
          </a-button>
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
          <a-button
            v-if="canApprove"
            type="primary"
            :loading="actionLoading"
            @click="handleApprove"
          >
            审批通过
          </a-button>
          <a-button
            v-if="canApprove"
            danger
            :loading="actionLoading"
            @click="handleReject"
          >
            审批驳回
          </a-button>
          <a-button v-if="canWithdraw" danger :loading="actionLoading" @click="handleWithdraw">
            撤回
          </a-button>
        </div>
      </template>
    </a-spin>
  </Modal>
</template>
