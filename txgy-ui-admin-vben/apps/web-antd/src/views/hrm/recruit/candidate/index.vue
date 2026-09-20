<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitCandidateApi } from '#/api/hrm/recruit/candidate';
import type { HrmRecruitCandidateStatusValue } from '#/views/hrm/utils/constants';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { confirm, DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel, getDictOptions } from '@vben/hooks';

import { Button, Dropdown, Menu, message, Tabs } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  enterRecruitApplicationInterview,
  passRecruitApplicationScreening,
  rejectRecruitApplicationResume,
  skipRecruitApplicationInterview,
  startRecruitApplicationScreening,
} from '#/api/hrm/recruit/application';
import {
  deleteRecruitCandidate,
  getRecruitCandidatePage,
  getRecruitCandidateStatusCount,
  updateRecruitCandidateStatus,
} from '#/api/hrm/recruit/candidate';
import { getRecruitInterview, startRecruitInterview } from '#/api/hrm/recruit/interview';
import { DictTag } from '#/components/dict-tag';
import { $t } from '#/locales';
import EmployeeForm from '#/views/hrm/employee/modules/form.vue';
import { executeBatch } from '#/views/hrm/utils/batch';
import {
  HRM_RECRUIT_CANDIDATE_EMPLOYEE_EDUCATION_MAP,
  HRM_RECRUIT_MAX_INTERVIEW_ROUNDS,
  HrmEmployeeEntryStatus,
  HrmEmployeeStatus,
  HrmEmployeeType,
  HrmRecruitApplicationStatus,
  HrmRecruitCandidateStatus,
  HrmRecruitInterviewResult,
} from '#/views/hrm/utils/constants';

import { useGridColumns, useGridFormSchema } from './data';
import ChannelBatchForm from './modules/channel-batch-form.vue';
import CleanForm from './modules/clean-form.vue';
import EliminateForm from './modules/eliminate-form.vue';
import Form from './modules/form.vue';
import InterviewEvaluationForm from './modules/interview-evaluation-form.vue';
import InterviewForm from './modules/interview-form.vue';
import InterviewResultForm from './modules/interview-result-form.vue';
import OfferForm from './modules/offer-form.vue';
import OfferStatusForm from './modules/offer-status-form.vue';
import PostBatchForm from './modules/post-batch-form.vue';
import StatusBatchForm from './modules/status-batch-form.vue';

defineOptions({ name: 'HrmRecruitCandidate' });

const { push } = useRouter();
const route = useRoute();
const { hasAccessByCodes } = useAccess();

const activeStatus = ref('all');
const statusCounts = ref<HrmRecruitCandidateApi.StatusCount[]>([]);
const checkedIds = ref<number[]>([]);

const offerApprovalStatusLabels: Record<string, string> = {
  APPROVED: '审批通过',
  APPROVING: '审批中',
  CANDIDATE_REJECTED: '求职者拒绝',
  CONFIRMED: '已接受',
  DRAFT: '草稿',
  EXPIRED: '已过期',
  REJECTED: '审批未通过',
  SENT: '已发送',
  WITHDRAWN: '已撤回',
};

const statusTabOptions = computed(() => {
  const countMap: Record<number, number> = {};
  for (const item of statusCounts.value) {
    countMap[item.status] = item.count;
  }
  return getDictOptions(DICT_TYPE.HRM_RECRUIT_CANDIDATE_STATUS, 'number').map(
    (item) => ({
      label: item.label,
      value: String(item.value),
      count: countMap[Number(item.value)] ?? 0,
    }),
  );
});

const allStatusCount = computed(() => {
  let total = 0;
  for (const item of statusCounts.value) {
    total += item.count;
  }
  return total;
});

const activeStatusValue = computed<HrmRecruitCandidateStatusValue | undefined>(
  () =>
    activeStatus.value === 'all'
      ? undefined
      : (Number(activeStatus.value) as HrmRecruitCandidateStatusValue),
);

/** 判断当前是否为指定候选人状态 */
function isActiveStatusIn(statuses: number[]) {
  return (
    activeStatusValue.value !== undefined &&
    statuses.includes(activeStatusValue.value)
  );
}

function offerApprovalStatusLabel(status?: string) {
  return status ? offerApprovalStatusLabels[status] || status : '-';
}

const canBatchUpdateStatus = computed(() =>
  isActiveStatusIn([
    HrmRecruitCandidateStatus.NEW,
    HrmRecruitCandidateStatus.PRIMARY_PASS,
    HrmRecruitCandidateStatus.INTERVIEW_PASS,
  ]),
);
const canBatchInterview = computed(() => canBatchUpdateStatus.value);
const canBatchUpdatePostOrChannel = computed(() => canBatchUpdateStatus.value);
const canBatchEliminate = computed(() =>
  isActiveStatusIn([
    HrmRecruitCandidateStatus.NEW,
    HrmRecruitCandidateStatus.PRIMARY_PASS,
    HrmRecruitCandidateStatus.INTERVIEW,
    HrmRecruitCandidateStatus.INTERVIEW_PASS,
    HrmRecruitCandidateStatus.OFFER_SENT,
    HrmRecruitCandidateStatus.PENDING_ENTRY,
  ]),
);
const candidateDeleteStatuses: number[] = [
  HrmRecruitCandidateStatus.NEW,
  HrmRecruitCandidateStatus.PRIMARY_PASS,
  HrmRecruitCandidateStatus.INTERVIEW,
  HrmRecruitCandidateStatus.INTERVIEW_PASS,
  HrmRecruitCandidateStatus.ELIMINATED,
];
const canBatchDelete = computed(() =>
  isActiveStatusIn(candidateDeleteStatuses),
);

const hasBatchPermission = computed(
  () =>
    hasAccessByCodes(['hrm:recruit:candidate:update']) ||
    hasAccessByCodes(['hrm:recruit:interview:create']) ||
    hasAccessByCodes(['hrm:recruit:candidate:delete']),
);

const hasBatchNonDeleteOperations = computed(
  () =>
    (hasAccessByCodes(['hrm:recruit:candidate:update']) &&
      (canBatchUpdateStatus.value ||
        canBatchUpdatePostOrChannel.value ||
        canBatchEliminate.value)) ||
    (hasAccessByCodes(['hrm:recruit:interview:create']) &&
      canBatchInterview.value),
);

const hasBatchOperations = computed(
  () =>
    hasBatchNonDeleteOperations.value ||
    (hasAccessByCodes(['hrm:recruit:candidate:delete']) &&
      canBatchDelete.value),
);

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [StatusBatchModal, statusBatchModalApi] = useVbenModal({
  connectedComponent: StatusBatchForm,
  destroyOnClose: true,
});
const [PostBatchModal, postBatchModalApi] = useVbenModal({
  connectedComponent: PostBatchForm,
  destroyOnClose: true,
});
const [ChannelBatchModal, channelBatchModalApi] = useVbenModal({
  connectedComponent: ChannelBatchForm,
  destroyOnClose: true,
});
const [EliminateModal, eliminateModalApi] = useVbenModal({
  connectedComponent: EliminateForm,
  destroyOnClose: true,
});
const [CleanModal, cleanModalApi] = useVbenModal({
  connectedComponent: CleanForm,
  destroyOnClose: true,
});
const [InterviewModal, interviewModalApi] = useVbenModal({
  connectedComponent: InterviewForm,
  destroyOnClose: true,
});
const [InterviewResultModal, interviewResultModalApi] = useVbenModal({
  connectedComponent: InterviewResultForm,
  destroyOnClose: true,
});
const [InterviewEvaluationModal, interviewEvaluationModalApi] = useVbenModal({
  connectedComponent: InterviewEvaluationForm,
  destroyOnClose: true,
});
const [OfferModal, offerModalApi] = useVbenModal({
  connectedComponent: OfferForm,
  destroyOnClose: true,
});
const [OfferStatusModal, offerStatusModalApi] = useVbenModal({
  connectedComponent: OfferStatusForm,
  destroyOnClose: true,
});
const [EmployeeFormModal, employeeFormModalApi] = useVbenModal({
  connectedComponent: EmployeeForm,
  destroyOnClose: true,
});

/** 刷新列表和状态统计 */
async function handleRefresh() {
  await Promise.all([gridApi.query(), getStatusCounts()]);
}

/** 查询状态统计（不带 status，避免切换页签后数量被当前状态过滤） */
async function getStatusCounts() {
  const formValues = await gridApi.formApi.getValues();
  statusCounts.value = await getRecruitCandidateStatusCount({
    ...formValues,
  });
}

/** 切换状态页签 */
function handleStatusTabChange(key: number | string) {
  activeStatus.value = String(key);
  checkedIds.value = [];
  handleRefresh();
}

/** 新增 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑 */
function handleEdit(row: HrmRecruitCandidateApi.RecruitCandidate) {
  formModalApi.setData(row).open();
}

/** 详情 */
function handleDetail(id?: number) {
  if (!id) {
    return;
  }
  push({ name: 'HrmRecruitCandidateDetail', params: { id } });
}

/** 安排面试 */
function openInterview(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!canArrangeInterview(candidate)) {
    message.warning(`面试轮次已达上限（最多 ${HRM_RECRUIT_MAX_INTERVIEW_ROUNDS} 轮）`);
    return;
  }
  interviewModalApi
    .setData({
      type: 'create',
      candidateIdOrIds: candidate.id!,
      applicationId: candidate.applicationId,
    })
    .open();
}

/** 安排复试 */
function openReinterview(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!canArrangeInterview(candidate)) {
    message.warning(`面试轮次已达上限（最多 ${HRM_RECRUIT_MAX_INTERVIEW_ROUNDS} 轮）`);
    return;
  }
  interviewModalApi
    .setData({
      type: 'create',
      candidateIdOrIds: candidate.id!,
      applicationId: candidate.applicationId,
      createTitle: '安排复试',
    })
    .open();
}

/** 批量面试 */
function openBatchInterview() {
  if (checkedIds.value.length === 0) {
    return;
  }
  interviewModalApi
    .setData({ type: 'batch', candidateIdOrIds: checkedIds.value })
    .open();
}

/** 登记面试结果 */
async function openInterviewResult(
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
) {
  if (!candidate.interviewId) {
    message.warning('请先安排面试');
    return;
  }
  const interview = await getRecruitInterview(candidate.interviewId);
  interviewResultModalApi.setData({ interview }).open();
}

function openInterviewEvaluation(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!candidate.interviewId) return;
  interviewEvaluationModalApi
    .setData({
      id: candidate.interviewId,
      allowNextRound: canArrangeInterview(candidate),
    })
    .open();
}

function openOfferForm(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!candidate.applicationId || !candidate.postId) return;
  offerModalApi.setData({ applicationId: candidate.applicationId, postId: candidate.postId }).open();
}

function openOfferStatus(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!candidate.offerId) return;
  offerStatusModalApi.setData({ offerId: candidate.offerId }).open();
}

async function handleApplicationFlowAction(
  command: string,
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
) {
  if (!candidate.applicationId) return;
  if (command === 'start-screening') {
    await startRecruitApplicationScreening(candidate.applicationId);
  } else if (command === 'pass-screening') {
    await passRecruitApplicationScreening(candidate.applicationId);
  } else if (command === 'reject-resume') {
    await rejectRecruitApplicationResume(candidate.applicationId, '简历不符合岗位要求');
  } else if (command === 'enter-interview') {
    if (!canArrangeInterview(candidate)) {
      message.warning(`面试轮次已达上限（最多 ${HRM_RECRUIT_MAX_INTERVIEW_ROUNDS} 轮）`);
      return;
    }
    await enterRecruitApplicationInterview(candidate.applicationId);
    openInterview(candidate);
    return;
  } else if (command === 'skip-interview') {
    await skipRecruitApplicationInterview(candidate.applicationId);
  } else if (command === 'start-interview' && candidate.interviewId) {
    await startRecruitInterview(candidate.interviewId);
  } else if (command === 'evaluate-interview') {
    openInterviewEvaluation(candidate);
    return;
  }
  await handleRefresh();
  message.success($t('ui.actionMessage.operationSuccess'));
}

/** 更改面试安排 */
async function openInterviewChange(
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
) {
  if (!candidate.id || !candidate.interviewId) {
    return;
  }
  const interview = await getRecruitInterview(candidate.interviewId);
  interviewModalApi
    .setData({
      type: 'update',
      candidateIdOrIds: candidate.id,
      interview,
    })
    .open();
}

/** 取消面试 */
async function openInterviewCancel(
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
) {
  if (!candidate.interviewId) {
    return;
  }
  const interview = await getRecruitInterview(candidate.interviewId);
  interviewResultModalApi
    .setData({ interview, result: HrmRecruitInterviewResult.CANCELED })
    .open();
}

/** 批量流转 */
function openBatchStatusForm() {
  if (!activeStatusValue.value) {
    return;
  }
  statusBatchModalApi
    .setData({ ids: checkedIds.value, status: activeStatusValue.value })
    .open();
}

/** 批量改职位 */
function openBatchPostForm() {
  postBatchModalApi.setData({ ids: checkedIds.value }).open();
}

/** 批量改渠道 */
function openBatchChannelForm() {
  channelBatchModalApi.setData({ ids: checkedIds.value }).open();
}

/** 淘汰 */
function openEliminateForm(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!candidate.id) {
    return;
  }
  eliminateModalApi
    .setData({ ids: candidate.id, applicationId: candidate.applicationId, name: candidate.name })
    .open();
}

/** 批量淘汰 */
function openBatchEliminateForm() {
  eliminateModalApi.setData({ ids: checkedIds.value }).open();
}

/** 转员工 */
function openEntryForm(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!candidate.id) {
    return;
  }
  const entryTime = candidate.entryTime
    ? Number(candidate.entryTime)
    : Date.now();
  employeeFormModalApi
    .setData({
      type: 'candidate',
      defaultData: {
        candidateId: candidate.id,
        applicationId: candidate.applicationId,
        name: candidate.name,
        mobile: candidate.mobile,
        sex: candidate.sex,
        age: candidate.age,
        email: candidate.email,
        highestEducation:
          candidate.education === undefined || candidate.education === null
            ? undefined
            : HRM_RECRUIT_CANDIDATE_EMPLOYEE_EDUCATION_MAP[candidate.education],
        deptId: candidate.deptId,
        postName: candidate.postName,
        channelId: candidate.channelId,
        entryStatus: HrmEmployeeEntryStatus.PENDING_ENTRY,
        status: HrmEmployeeStatus.PROBATION,
        type: HrmEmployeeType.FORMAL,
        entryTime,
        companyAgeStartTime: entryTime,
        probation: 3,
        remark: candidate.remark,
      },
    })
    .open();
}

/** 确认入职 */
function handleConfirmEntry(
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
) {
  if (!candidate.employeeId) {
    return;
  }
  employeeFormModalApi
    .setData({ type: 'confirm', id: candidate.employeeId, applicationId: candidate.applicationId })
    .open();
}

/** 一键清理 */
function openCleanForm() {
  cleanModalApi.open();
}

function canArrangeInterview(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  return (candidate.stageNumber ?? 0) < HRM_RECRUIT_MAX_INTERVIEW_ROUNDS;
}

/** 获得候选人的主操作 */
function getPrimaryAction(
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
): undefined | { command: string; label: string } {
  if (candidate.applicationId) {
    if (candidate.applicationStatus === HrmRecruitApplicationStatus.SUBMITTED) {
      return { command: 'start-screening', label: '开始筛选' };
    }
    if (candidate.applicationStatus === HrmRecruitApplicationStatus.SCREENING) {
      return { command: 'pass-screening', label: '筛选通过' };
    }
    if (
      candidate.applicationStatus === HrmRecruitApplicationStatus.SCREENING_PASSED ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.PENDING_INTERVIEW ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.NEXT_INTERVIEW
    ) {
      return canArrangeInterview(candidate)
        ? { command: 'enter-interview', label: '安排面试' }
        : undefined;
    }
    if (candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEW_SCHEDULED) {
      return { command: 'start-interview', label: '开始面试' };
    }
    if (candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEWING) {
      return candidate.interviewId
        ? { command: 'interview-result', label: '登记结果' }
        : undefined;
    }
    if (candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEW_EVALUATING) {
      return { command: 'evaluate-interview', label: '面试评估' };
    }
    if (
      candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEW_CANCELED ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEW_ABSENT
    ) {
      return canArrangeInterview(candidate)
        ? { command: 'interview-change', label: '重新安排' }
        : undefined;
    }
    if (
      candidate.offerId &&
      hasAccessByCodes(['hrm:recruit:offer:query'])
    ) {
      return { command: 'offer-status', label: '查看 Offer' };
    }
    if (
      candidate.applicationStatus === HrmRecruitApplicationStatus.FINAL_INTERVIEW_PASSED ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.PENDING_OFFER ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.PASSED
    ) {
      return hasAccessByCodes(['hrm:recruit:offer:create'])
        ? { command: 'offer', label: '创建 Offer' }
        : undefined;
    }
    if (candidate.applicationStatus === HrmRecruitApplicationStatus.PENDING_ONBOARD) {
      if (candidate.status === HrmRecruitCandidateStatus.PENDING_ENTRY && candidate.employeeId) {
        return { command: 'confirm-entry', label: '确认入职' };
      }
      if (!candidate.employeeId) {
        return { command: 'convert-employee', label: '转为员工' };
      }
    }
    return undefined;
  }
  if (
    hasAccessByCodes(['hrm:recruit:interview:update']) &&
    candidate.status === HrmRecruitCandidateStatus.INTERVIEW &&
    candidate.interviewId &&
    (candidate.interviewResult === HrmRecruitInterviewResult.CANCELED ||
      candidate.interviewResult === HrmRecruitInterviewResult.ABSENT) &&
    canArrangeInterview(candidate)
  ) {
    return { command: 'interview-change', label: '重新安排' };
  }
  if (
    hasAccessByCodes(['hrm:recruit:interview:update']) &&
    candidate.status === HrmRecruitCandidateStatus.INTERVIEW &&
    candidate.interviewId &&
    candidate.interviewResult === HrmRecruitInterviewResult.UNFINISHED
  ) {
    return { command: 'interview-result', label: '登记结果' };
  }
  if (
    hasAccessByCodes(['hrm:employee:update']) &&
    candidate.status === HrmRecruitCandidateStatus.PENDING_ENTRY &&
    candidate.employeeId
  ) {
    return { command: 'confirm-entry', label: '确认入职' };
  }
  if (
    hasAccessByCodes(['hrm:recruit:candidate:update']) &&
    (candidate.status === HrmRecruitCandidateStatus.INTERVIEW_PASS ||
      candidate.status === HrmRecruitCandidateStatus.OFFER_SENT) &&
    !candidate.employeeId
  ) {
    return { command: 'convert-employee', label: '转为员工' };
  }
  if (
    hasAccessByCodes(['hrm:recruit:interview:create']) &&
    (candidate.status === HrmRecruitCandidateStatus.NEW ||
      candidate.status === HrmRecruitCandidateStatus.PRIMARY_PASS ||
      candidate.status === HrmRecruitCandidateStatus.INTERVIEW_PASS)
  ) {
    return { command: 'interview', label: '安排面试' };
  }
  return undefined;
}

/** 获得候选人的更多操作 */
function getMoreActions(candidate: HrmRecruitCandidateApi.RecruitCandidate) {
  const actions: Array<{
    command: string;
    label: string;
    disabled?: boolean;
    tooltip?: string;
  }> = [];
  if (candidate.applicationId) {
    if (
      hasAccessByCodes(['hrm:recruit:application:eliminate']) &&
      candidate.applicationStatus &&
      ![
        HrmRecruitApplicationStatus.ELIMINATED,
        HrmRecruitApplicationStatus.INTERVIEW_REJECTED,
        HrmRecruitApplicationStatus.OFFER_CANDIDATE_REJECTED,
        HrmRecruitApplicationStatus.OFFER_EXPIRED,
        HrmRecruitApplicationStatus.OFFER_REJECTED,
        HrmRecruitApplicationStatus.OFFER_SENT,
        HrmRecruitApplicationStatus.OFFER_WITHDRAWN,
        HrmRecruitApplicationStatus.ONBOARDED,
        HrmRecruitApplicationStatus.PENDING_ONBOARD,
        HrmRecruitApplicationStatus.RESUME_REJECTED,
        HrmRecruitApplicationStatus.WITHDRAWN,
      ].includes(candidate.applicationStatus as never)
    ) {
      actions.push({ command: 'application-eliminate', label: '淘汰本次应聘' });
    }
    if (
      hasAccessByCodes(['hrm:recruit:application:eliminate']) &&
      (candidate.applicationStatus === HrmRecruitApplicationStatus.SUBMITTED ||
        candidate.applicationStatus === HrmRecruitApplicationStatus.SCREENING)
    ) {
      actions.push({ command: 'reject-resume', label: '简历淘汰' });
    }
    if (
      hasAccessByCodes(['hrm:recruit:application:interview']) &&
      (candidate.applicationStatus === HrmRecruitApplicationStatus.SCREENING_PASSED ||
        candidate.applicationStatus === HrmRecruitApplicationStatus.PENDING_INTERVIEW)
    ) {
      actions.push({ command: 'skip-interview', label: '免面试通过' });
    }
    if (
      hasAccessByCodes(['hrm:recruit:interview:update']) &&
      candidate.status === HrmRecruitCandidateStatus.INTERVIEW &&
      candidate.interviewId &&
      candidate.interviewResult === HrmRecruitInterviewResult.UNFINISHED &&
      (candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEW_SCHEDULED ||
        candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEWING)
    ) {
      actions.push(
        { command: 'interview-change', label: '更改面试安排' },
        { command: 'interview-cancel', label: '取消面试' },
      );
    }
    if (hasAccessByCodes(['hrm:recruit:offer:query']) && candidate.offerId) {
      actions.push({ command: 'offer-status', label: '查看 Offer 状态' });
    }
    return actions;
  }
  if (
    hasAccessByCodes(['hrm:recruit:application:interview']) &&
    candidate.applicationId &&
    (candidate.applicationStatus === HrmRecruitApplicationStatus.SCREENING_PASSED ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.PENDING_INTERVIEW)
  ) {
    actions.push({ command: 'skip-interview', label: '免面试通过' });
  }
  const canManageCurrentInterview =
    candidate.status === HrmRecruitCandidateStatus.INTERVIEW &&
    candidate.interviewId &&
    candidate.interviewResult === HrmRecruitInterviewResult.UNFINISHED &&
    (!candidate.applicationId ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEW_SCHEDULED ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.INTERVIEWING);
  if (
    hasAccessByCodes(['hrm:recruit:interview:update']) &&
    canManageCurrentInterview
  ) {
    actions.push(
      { command: 'interview-change', label: '更改面试安排' },
      { command: 'interview-cancel', label: '取消面试' },
    );
  }
  if (
    hasAccessByCodes(['hrm:recruit:interview:create']) &&
    !candidate.applicationId &&
    canArrangeInterview(candidate) &&
    candidate.status === HrmRecruitCandidateStatus.INTERVIEW_PASS
  ) {
    actions.push({ command: 'reinterview', label: '安排复试' });
  }
  if (hasAccessByCodes(['hrm:recruit:candidate:update'])) {
    if (
      candidate.applicationStatus === HrmRecruitApplicationStatus.SUBMITTED ||
      candidate.applicationStatus === HrmRecruitApplicationStatus.SCREENING
    ) {
      actions.push({ command: 'reject-resume', label: '简历淘汰' });
    }
    if (candidate.status === HrmRecruitCandidateStatus.NEW) {
      actions.push({ command: 'primary-pass', label: '初选通过' });
    }
    if (
      candidate.status === HrmRecruitCandidateStatus.INTERVIEW_PASS &&
      hasAccessByCodes(['hrm:recruit:offer:create'])
    ) {
      // 发 Offer 必须挂在一条应聘记录上（Offer 的薪资、职位、入职日期都取自应聘记录）。
      // 后台手工新增的候选人不经过门户投递，没有 applicationId，点击必报
      // 「应聘编号不能为空」，因此这里直接置灰并说明原因，避免无效点击。
      if (candidate.applicationId) {
        actions.push({ command: 'offer', label: '发 Offer' });
      } else {
        actions.push({
          command: 'offer',
          // 菜单项被禁用时 antd 不会触发 hover，tooltip 看不到，因此把原因写进文案
          label: '发 Offer（该候选人无应聘记录）',
          disabled: true,
          tooltip:
            '该候选人没有应聘记录，无法发 Offer。请让其在招聘门户投递职位，或在「招聘沟通」中把求职者转为候选人后再操作。',
        });
      }
    }
    if (candidate.status === HrmRecruitCandidateStatus.ELIMINATED) {
      actions.push({ command: 'restore', label: '恢复为新候选人' });
    }
    if (
      candidate.status !== HrmRecruitCandidateStatus.ELIMINATED &&
      candidate.status !== HrmRecruitCandidateStatus.JOINED
    ) {
      actions.push({ command: 'eliminate', label: '淘汰' });
    }
  }
  if (
    hasAccessByCodes(['hrm:recruit:candidate:delete']) &&
    !candidate.employeeId &&
    candidate.status !== undefined &&
    candidate.status !== null &&
    candidateDeleteStatuses.includes(candidate.status)
  ) {
    actions.push({ command: 'delete', label: '删除' });
  }
  return actions;
}

/** 主操作 */
async function handlePrimaryAction(
  command: string,
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
) {
  if (
    ['enter-interview', 'evaluate-interview', 'pass-screening', 'start-interview', 'start-screening'].includes(command)
  ) {
    await handleApplicationFlowAction(command, candidate);
    return;
  }
  if (command === 'interview-result') {
    await openInterviewResult(candidate);
    return;
  }
  if (command === 'interview-change') {
    await openInterviewChange(candidate);
    return;
  }
  if (command === 'confirm-entry') {
    handleConfirmEntry(candidate);
    return;
  }
  if (command === 'convert-employee') {
    openEntryForm(candidate);
    return;
  }
  if (command === 'offer') {
    openOfferForm(candidate);
    return;
  }
  if (command === 'offer-status') {
    openOfferStatus(candidate);
    return;
  }
  if (command === 'interview' && candidate.id) {
    openInterview(candidate);
  }
}

/** 更多操作 */
async function handleMoreCommand(
  command: string,
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
) {
  if (command === 'reject-resume') {
    await handleApplicationFlowAction(command, candidate);
    return;
  }
  if (command === 'application-eliminate') {
    openEliminateForm(candidate);
    return;
  }
  if (command === 'skip-interview') {
    await handleApplicationFlowAction(command, candidate);
    return;
  }
  if (command === 'offer') {
    openOfferForm(candidate);
    return;
  }
  if (command === 'primary-pass') {
    await handleStatus(candidate, HrmRecruitCandidateStatus.PRIMARY_PASS);
    return;
  }
  if (command === 'restore') {
    await handleStatus(candidate, HrmRecruitCandidateStatus.NEW);
    return;
  }
  if (command === 'interview-change') {
    await openInterviewChange(candidate);
    return;
  }
  if (command === 'interview-cancel') {
    await openInterviewCancel(candidate);
    return;
  }
  if (command === 'reinterview' && candidate.id) {
    openReinterview(candidate);
    return;
  }
  if (command === 'eliminate') {
    openEliminateForm(candidate);
    return;
  }
  if (command === 'delete') {
    await handleDelete(candidate);
  }
}

/** 修改状态 */
async function handleStatus(
  candidate: HrmRecruitCandidateApi.RecruitCandidate,
  status: HrmRecruitCandidateStatusValue,
) {
  if (!candidate.id) {
    return;
  }
  await updateRecruitCandidateStatus({ id: candidate.id, status });
  message.success($t('ui.actionMessage.operationSuccess'));
  await handleRefresh();
}

/** 删除 */
async function handleDelete(row: HrmRecruitCandidateApi.RecruitCandidate) {
  if (!row.id) {
    return;
  }
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteRecruitCandidate(row.id);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    await handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除 */
async function handleBatchDelete() {
  if (checkedIds.value.length === 0) {
    return;
  }
  try {
    await confirm(`确认删除选中的 ${checkedIds.value.length} 位候选人吗？`);
    const hasSuccess = await executeBatch(
      checkedIds.value.map((id) => deleteRecruitCandidate(id)),
    );
    if (!hasSuccess) {
      return;
    }
    await handleBatchSuccess();
  } catch {
    // 取消确认
  }
}

/** 批量操作成功 */
async function handleBatchSuccess() {
  checkedIds.value = [];
  await handleRefresh();
}

/** 行操作 actions */
function getRowActions(row: HrmRecruitCandidateApi.RecruitCandidate) {
  const primary = getPrimaryAction(row);
  const actions = [
    {
      label: $t('common.edit'),
      type: 'link' as const,
      icon: ACTION_ICON.EDIT,
      auth: ['hrm:recruit:candidate:update'],
      onClick: handleEdit.bind(null, row),
    },
  ];
  if (primary) {
    actions.push({
      label: primary.label,
      type: 'link' as const,
      icon: ACTION_ICON.EDIT,
      auth: [],
      onClick: () => handlePrimaryAction(primary.command, row),
    });
  }
  return actions;
}

/** 行更多操作 */
function getRowDropDownActions(row: HrmRecruitCandidateApi.RecruitCandidate) {
  return getMoreActions(row).map((action) => ({
    label: action.label,
    auth: [] as string[],
    danger: action.command === 'delete',
    // 透传禁用态与说明：例如「发 Offer」在候选人没有应聘记录时必须置灰并给出原因，
    // 否则用户点下去只会拿到后端「应聘编号不能为空」的报错。
    disabled: action.disabled,
    tooltip: action.tooltip,
    ...(action.command === 'delete'
      ? {
          popConfirm: {
            title: $t('ui.actionMessage.deleteConfirm', [row.name]),
            confirm: () => handleMoreCommand(action.command, row),
          },
        }
      : {
          onClick: () => handleMoreCommand(action.command, row),
        }),
  }));
}

/** 工具栏批量操作 */
function getBatchDropDownActions() {
  const actions: Array<{
    danger?: boolean;
    label: string;
    onClick?: () => void;
  }> = [];
  if (
    canBatchUpdateStatus.value &&
    hasAccessByCodes(['hrm:recruit:candidate:update'])
  ) {
    actions.push({
      label: '批量流转',
      onClick: openBatchStatusForm,
    });
  }
  if (
    canBatchInterview.value &&
    hasAccessByCodes(['hrm:recruit:interview:create'])
  ) {
    actions.push({
      label: '批量面试',
      onClick: openBatchInterview,
    });
  }
  if (
    canBatchUpdatePostOrChannel.value &&
    hasAccessByCodes(['hrm:recruit:candidate:update'])
  ) {
    actions.push(
      {
        label: '修改职位',
        onClick: openBatchPostForm,
      },
      {
        label: '修改渠道',
        onClick: openBatchChannelForm,
      },
    );
  }
  if (
    canBatchEliminate.value &&
    hasAccessByCodes(['hrm:recruit:candidate:update'])
  ) {
    actions.push({
      label: '批量淘汰',
      onClick: openBatchEliminateForm,
    });
  }
  if (
    canBatchDelete.value &&
    hasAccessByCodes(['hrm:recruit:candidate:delete'])
  ) {
    actions.push({
      label: '批量删除',
      danger: true,
      onClick: handleBatchDelete,
    });
  }
  return actions;
}

function handleRowCheckboxChange({
  records,
}: {
  records: HrmRecruitCandidateApi.RecruitCandidate[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    collapsed: true,
    schema: useGridFormSchema(),
  },
  gridOptions: {
    checkboxConfig: {
      checkMethod: () => activeStatus.value !== 'all',
    },
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getRecruitCandidatePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            status: activeStatusValue.value,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<HrmRecruitCandidateApi.RecruitCandidate>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});

/** 初始化 */
onMounted(async () => {
  if (route.query.status) {
    const status = Number(route.query.status) as HrmRecruitCandidateStatusValue;
    activeStatus.value = String(status);
  }
  await getStatusCounts();
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【招聘】招聘管理"
        url="https://doc.iocoder.cn/hrm/recruit/"
      />
    </template>
    <FormModal @success="handleRefresh" />
    <StatusBatchModal @success="handleBatchSuccess" />
    <PostBatchModal @success="handleBatchSuccess" />
    <ChannelBatchModal @success="handleBatchSuccess" />
    <EliminateModal @success="handleBatchSuccess" />
    <CleanModal @success="handleRefresh" />
    <InterviewModal @success="handleBatchSuccess" />
    <InterviewResultModal @success="handleRefresh" />
    <InterviewEvaluationModal @success="handleRefresh" />
    <OfferModal @success="handleRefresh" />
    <OfferStatusModal @success="handleRefresh" />
    <EmployeeFormModal @success="handleRefresh" />

    <Grid class="candidate-grid">
      <template #toolbar-actions>
        <div class="candidate-status-tabs">
          <Tabs
            :active-key="activeStatus"
            class="w-full"
            @change="handleStatusTabChange"
          >
            <Tabs.TabPane key="all" :tab="`全部（${allStatusCount}）`" />
            <Tabs.TabPane
              v-for="item in statusTabOptions"
              :key="item.value"
              :tab="`${item.label}（${item.count}）`"
            />
          </Tabs>
        </div>
      </template>
      <template #toolbar-tools>
        <div class="flex shrink-0 flex-nowrap items-center gap-2">
          <TableAction
            :actions="[
              {
                label: '新增',
                type: 'primary',
                icon: ACTION_ICON.ADD,
                auth: ['hrm:recruit:candidate:create'],
                onClick: handleCreate,
              },
              {
                label: '一键清理',
                type: 'primary',
                danger: true,
                icon: ACTION_ICON.DELETE,
                auth: ['hrm:recruit:candidate:delete'],
                onClick: openCleanForm,
              },
            ]"
          />
          <Dropdown
            v-if="hasBatchPermission"
            :disabled="
              !hasBatchOperations ||
              checkedIds.length === 0 ||
              activeStatus === 'all'
            "
          >
            <Button
              type="primary"
              :disabled="
                !hasBatchOperations ||
                checkedIds.length === 0 ||
                activeStatus === 'all'
              "
            >
              批量操作
            </Button>
            <template #overlay>
              <Menu>
                <Menu.Item
                  v-for="action in getBatchDropDownActions()"
                  :key="action.label"
                  :danger="action.danger"
                  @click="action.onClick?.()"
                >
                  {{ action.label }}
                </Menu.Item>
              </Menu>
            </template>
          </Dropdown>
        </div>
      </template>
      <template #name="{ row }">
        <Button type="link" @click="handleDetail(row.id)">
          {{ row.name }}
        </Button>
      </template>
      <template #status="{ row }">
        <DictTag
          :type="DICT_TYPE.HRM_RECRUIT_CANDIDATE_STATUS"
          :value="row.status"
        />
        <span
          v-if="
            row.status === HrmRecruitCandidateStatus.INTERVIEW &&
            row.interviewResult &&
            row.interviewResult !== HrmRecruitInterviewResult.UNFINISHED
          "
        >
          （面试{{
            getDictLabel(
              DICT_TYPE.HRM_RECRUIT_INTERVIEW_RESULT,
              row.interviewResult,
            )
          }}）
        </span>
      </template>
      <template #offerStatus="{ row }">
        <span>{{ offerApprovalStatusLabel(row.offerApprovalStatus) }}</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="getRowActions(row)"
          :drop-down-actions="getRowDropDownActions(row)"
        />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
/*
 * 状态 Tabs 独占一行（对齐源 vue3），右侧按钮在下一行右对齐。
 * 避免与 toolbar-tools 抢宽度导致折行 / 切换页签时抖动。
 */
.candidate-grid :deep(.vxe-toolbar) {
  flex-wrap: wrap;
  row-gap: 0;
  align-items: center;
}

.candidate-grid :deep(.vxe-buttons--wrapper) {
  flex: 1 0 100%;
  width: 100%;
  min-width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.candidate-grid :deep(.vxe-tools--wrapper),
.candidate-grid :deep(.vxe-tools--operate) {
  flex: 1 1 auto;
  flex-wrap: nowrap;
  justify-content: flex-end;
  margin-left: auto;
}

.candidate-status-tabs {
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.candidate-status-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.candidate-status-tabs :deep(.ant-tabs-nav::before) {
  border-bottom: none;
}
</style>
