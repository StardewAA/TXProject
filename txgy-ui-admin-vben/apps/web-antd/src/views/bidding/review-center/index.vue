<script setup lang="ts">
/* 平台审查中心（共享界面，菜单 60005600 / component bidding/review-center/index）
 * 企业录入证照/资质后自动触发审查任务；超管公司统一筛选并审查，租户只读查看本企业任务、结果与原因。
 * 租户视角由后端按当前租户隔离；权限码未下发时保守按租户只读，避免误暴露审查操作。
 * 契约：docs/电子证照前前端契约/后端变更请求-平台审查任务中心-2026-09-20.md */
import { computed, h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import {
  approveReviewTask,
  getReviewTaskPage,
  getReviewTaskStats,
  rejectReviewTask,
  type ReviewTaskApi,
} from '#/api/bidding/review-task';
import {
  authorizeSubject,
  fetchSubjectAuthPage,
  type SubjectApi,
  updateSubjectAuthStatus,
} from '#/api/bidding/subject';

import { fmtDateTime } from '../certificate/format';
import { useCertMeta } from '../certificate/useCertMeta';
import AttrFilter from '../components/AttrFilter.vue';

import '../certificate/cert.css';

const router = useRouter();
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictOptions, dictTag } = useCertMeta();

/** 超管视角：有明确审查权限才开启筛选与操作；码未下发时安全降级为租户只读 */
const isPlatform = computed(() => {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return false;
  return (
    codes.includes('*') ||
    codes.includes('*:*:*') ||
    hasAccessByCodes(['bidding:review:approve']) ||
    hasAccessByCodes(['bidding:review:reject'])
  );
});

const REVIEW_PENDING = '审查任务接口待后端实现（见契约缺口 A1~A4）';
function reviewErrorText(error: any): string {
  const status = error?.status ?? error?.response?.status;
  const code = error?.code ?? error?.response?.data?.code;
  if (status === 404 || code === 404) return REVIEW_PENDING;
  return String(error?.msg ?? error?.message ?? '加载失败');
}

const query = reactive<ReviewTaskApi.PageReq>({ pageNo: 1, pageSize: 10 });
const loading = ref(false);
const rows = ref<ReviewTaskApi.Row[]>([]);
const total = ref(0);
const notice = ref('');

const statsOpen = ref(false);
const stats = ref<ReviewTaskApi.Stats>({ pending: 0, todayNew: 0, approved: 0, rejected: 0 });
async function loadStats(): Promise<void> {
  statsOpen.value = false;
  try {
    stats.value = await getReviewTaskStats();
    statsOpen.value = true;
  } catch {
    statsOpen.value = false;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  notice.value = '';
  try {
    const page = await getReviewTaskPage({ ...query });
    rows.value = page.list ?? [];
    total.value = page.total ?? 0;
  } catch (error: any) {
    rows.value = [];
    total.value = 0;
    notice.value = reviewErrorText(error);
  } finally {
    loading.value = false;
  }
}

/** 「详情」→ 独立详情页（/bidding/review-center/detail/:id）；不再用抽屉/表格下方占位展示 */
function openDetail(row: ReviewTaskApi.Row): void {
  void router.push(`/bidding/review-center/detail/${row.id}`);
}

function statusTag(value?: string) {
  const tag = dictTag('review_task_status', value);
  const color = value === 'PENDING' ? 'orange' : value === 'APPROVED' ? 'green' : value === 'REJECTED' ? 'red' : tag.color;
  return { ...tag, color };
}
function bizTypeTag(value?: string) {
  return dictTag('review_biz_type', value);
}

function confirmApprove(row: ReviewTaskApi.Detail | ReviewTaskApi.Row): void {
  if (row.version === undefined || row.version === null) {
    message.warning('该任务缺少 version（乐观锁），请刷新后重试');
    return;
  }
  Modal.confirm({
    title: '审查通过 #' + row.id,
    okText: '通过',
    content: '通过后该证照/资质方可被其他接口引用；请确认已核对企业录入信息。',
    async onOk() {
      try {
        await approveReviewTask({ taskId: row.id, version: row.version as number });
        message.success('已通过审查，结果已回传企业');
        await Promise.all([load(), loadStats()]);
      } catch (error: any) {
        message.error(reviewErrorText(error));
        throw new Error('approve failed');
      }
    },
  });
}
function confirmReject(row: ReviewTaskApi.Detail | ReviewTaskApi.Row): void {
  if (row.version === undefined || row.version === null) {
    message.warning('该任务缺少 version（乐观锁），请刷新后重试');
    return;
  }
  let reason = '';
  Modal.confirm({
    title: '审查驳回 #' + row.id,
    okText: '驳回',
    okType: 'danger',
    content: () => h('textarea', {
      class: 'ant-input',
      placeholder: '驳回原因（必填，将展示给提交企业）',
      rows: 3,
      style: 'width:100%',
      onInput: (event: any) => { reason = event.target.value; },
    }),
    async onOk() {
      if (!reason.trim()) {
        message.warning('请填写驳回原因');
        throw new Error('reason required');
      }
      try {
        await rejectReviewTask({ taskId: row.id, version: row.version as number, reason: reason.trim() });
        message.success('已驳回，原因已留痕并展示给企业');
        await Promise.all([load(), loadStats()]);
      } catch (error: any) {
        message.error(reviewErrorText(error));
        throw new Error('reject failed');
      }
    },
  });
}

function onRangeChange(range: any): void {
  if (Array.isArray(range) && range.length === 2 && range[0] && range[1]) {
    query.beginTime = range[0].valueOf();
    query.endTime = range[1].valueOf();
  } else {
    query.beginTime = undefined;
    query.endTime = undefined;
  }
}

const columns = [
  { title: '任务ID', dataIndex: 'id', key: 'id', width: 90 },
  { title: '标记', dataIndex: 'status', key: 'newFlag', width: 80 },
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType', width: 110 },
  { title: '提交企业', dataIndex: 'entName', key: 'entName', width: 200 },
  { title: '统一社会信用代码', dataIndex: 'entUscc', key: 'entUscc', width: 185 },
  { title: '关联编号', dataIndex: 'refNo', key: 'refNo', width: 165 },
  { title: '提交时间', dataIndex: 'submitTime', key: 'submitTime', width: 165 },
  { title: '任务状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '审查人', dataIndex: 'reviewer', key: 'reviewer', width: 110 },
  { title: '审查时间', dataIndex: 'reviewTime', key: 'reviewTime', width: 165 },
  { title: '驳回原因', dataIndex: 'reason', key: 'reason', width: 220 },
  { title: '操作', key: 'action', width: 170, fixed: 'right' },
];

/* ===== 主体授权（平台侧台账，2026-10-02 用户口径：从「主体档案」页迁到审查中心） =====
 * 为什么放这：授权/审查是**平台侧**动作（企业提交 → 平台授权/审查），
 * 主体档案页是企业自维护页，不该出现平台台账。
 * 权限：bidding:subject-auth:{query,update}（权限点 60006300/60006301 目前仍挂在「主体档案」菜单下，
 * 要彻底归位需把这两个权限点改挂本菜单 60005600 —— 见契约核对清单）。
 * 契约：GET /bidding/subject-auth/page、POST /bidding/subject-auth/authorize、PUT /bidding/subject-auth/status */
const canSubjectAuth = hasAccessByCodes(['bidding:subject-auth:query']);
const canSubjectAuthUpdate = hasAccessByCodes(['bidding:subject-auth:update']);

const authLoading = ref(false);
const authRows = ref<SubjectApi.AuthRow[]>([]);
const authTotal = ref(0);
const authQuery = reactive({
  tenantName: '',
  subjectType: undefined as string | undefined,
  authStatus: undefined as string | undefined,
  pageNo: 1,
  pageSize: 10,
});
const subjectTypeOptions = [
  { label: '投标人', value: 'BIDDER' },
  { label: '招标人', value: 'TENDERER' },
];
const authStatusOptions = [
  { label: '已授权', value: 'ENABLED' },
  { label: '已停用', value: 'DISABLED' },
];
const authColumns = [
  { title: '租户编号', dataIndex: 'tenantId', key: 'tenantId', width: 110 },
  { title: '租户名称', dataIndex: 'tenantName', key: 'tenantName', width: 200 },
  { title: '主体类型', dataIndex: 'subjectType', key: 'subjectType', width: 100 },
  { title: '授权状态', dataIndex: 'authStatus', key: 'authStatus', width: 100 },
  { title: '主体档案', key: 'profile', width: 150 },
  { title: '操作', key: 'action', width: 110 },
];

/** 主体类型中文 */
function subjectTypeText(t?: string): string {
  if (t === 'BIDDER') return '投标人';
  if (t === 'TENDERER') return '招标人';
  return t || '-';
}

async function loadAuth(): Promise<void> {
  if (!canSubjectAuth) return;
  authLoading.value = true;
  try {
    const res = await fetchSubjectAuthPage({
      pageNo: authQuery.pageNo,
      pageSize: authQuery.pageSize,
      tenantName: authQuery.tenantName || undefined,
      subjectType: authQuery.subjectType,
      authStatus: authQuery.authStatus,
    });
    authRows.value = res?.list ?? [];
    authTotal.value = res?.total ?? 0;
  } catch {
    authRows.value = [];
    authTotal.value = 0;
  } finally {
    authLoading.value = false;
  }
}

function searchAuth(): void {
  authQuery.pageNo = 1;
  void loadAuth();
}

const authOpen = ref(false);
const authBusy = ref(false);
const authForm = reactive({
  tenantId: undefined as number | string | undefined,
  tenantName: '',
  subjectType: 'BIDDER',
  remark: '',
});

function openAuthorize(): void {
  authForm.tenantId = undefined;
  authForm.tenantName = '';
  authForm.subjectType = 'BIDDER';
  authForm.remark = '';
  authOpen.value = true;
}

async function doAuthorize(): Promise<void> {
  const idText = authForm.tenantId === undefined || authForm.tenantId === null ? '' : String(authForm.tenantId).trim();
  if (!idText) {
    message.warning('请填写租户编号（企业租户 ID）');
    return;
  }
  authBusy.value = true;
  try {
    await authorizeSubject({
      tenantId: idText,
      tenantName: authForm.tenantName || undefined,
      subjectType: authForm.subjectType,
      remark: authForm.remark || undefined,
    });
    message.success('授权成功');
    authOpen.value = false;
    void loadAuth();
  } finally {
    authBusy.value = false;
  }
}

async function toggleAuthStatus(row: SubjectApi.AuthRow, next: 'DISABLED' | 'ENABLED'): Promise<void> {
  const label = next === 'DISABLED' ? '停用' : '重新启用';
  Modal.confirm({
    title: '确认' + label + '该主体授权？',
    content: '租户：' + (row.tenantName || row.tenantId) + '（' + subjectTypeText(row.subjectType) + '）',
    okText: label,
    cancelText: '取消',
    async onOk() {
      await updateSubjectAuthStatus(row.id!, next);
      message.success('已' + label);
      void loadAuth();
    },
  });
}

onMounted(() => {
  void load();
  void loadStats();
  void loadAuth();
});
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">平台审查中心</div>
        <div class="ct-sub">企业录入证照/资质后自动触发审查任务 · 超管审查与租户共享（租户只读本企业）</div>
      </div>
      <div class="ct-actions"><a-button :loading="loading" @click="load">刷新</a-button></div>
    </div>

    <a-alert type="info" show-icon class="mb-3">
      <template #message>{{ isPlatform ? '超管审查视角' : '租户只读视角' }}</template>
      <template #description>
        <template v-if="isPlatform">企业录入证照/资质后<b>自动生成审查任务</b>；可按企业、业务类型、状态和时间筛选，待审查项通过/驳回均留痕，结果与驳回原因对租户可见。</template>
        <template v-else>仅显示<b>本企业</b>的审查任务（含状态与驳回原因），<b>只读不可修改</b>；录入侧修正后重新提交会生成新的审查任务。</template>
      </template>
    </a-alert>

    <a-space v-if="statsOpen" wrap class="mb-3">
      <a-tag color="orange">待审查 {{ stats.pending }}</a-tag>
      <a-tag color="processing">今日新增 {{ stats.todayNew }}</a-tag>
      <a-tag color="green">已通过 {{ stats.approved }}</a-tag>
      <a-tag color="red">已驳回 {{ stats.rejected }}</a-tag>
      <span class="ct-panel-meta">超管为平台全局，租户为本企业</span>
    </a-space>

    <div v-if="isPlatform" class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
      <AttrFilter label="企业">
        <a-input v-model:value="query.keyword" placeholder="企业名称 / 统一社会信用代码" style="width: 220px" allow-clear @press-enter="load" />
      </AttrFilter>
      <AttrFilter v-model="query.bizType" label="业务类型" :options="dictOptions('review_biz_type')" />
      <AttrFilter v-model="query.status" label="任务状态" :options="dictOptions('review_task_status')" />
      <AttrFilter label="提交时间"><a-range-picker @change="onRangeChange" /></AttrFilter>
      <a-button type="primary" :loading="loading" @click="load">查询</a-button>
      <span class="ct-panel-meta">跨企业查看任务；「新」代表新录入且尚未完成审查</span>
    </div>

    <a-alert v-if="notice" :message="notice" type="warning" show-icon class="mb-3" />

    <div class="ct-panel">
      <div class="ct-panel-h">
        <span class="ct-panel-t">审查任务</span>
        <span class="ct-panel-meta">{{ isPlatform ? '一眼区分 待审查 / 已通过 / 已驳回' : '本企业任务：状态与原因回传在此展示' }}</span>
      </div>
      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="{ current: query.pageNo, pageSize: query.pageSize, total, showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条` }"
          :scroll="{ x: 1450 }"
          row-key="id"
          size="middle"
          @change="(page: any) => { query.pageNo = page.current; query.pageSize = page.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'bizType'"><a-tag :color="bizTypeTag(record.bizType).color">{{ bizTypeTag(record.bizType).label }}</a-tag></template>
            <template v-else-if="column.key === 'newFlag'"><a-tag v-if="record.status === 'PENDING'" color="processing">新</a-tag><span v-else class="ct-panel-meta">—</span></template>
            <template v-else-if="column.key === 'submitTime'">{{ record.submitTime ? fmtDateTime(record.submitTime) : '—' }}</template>
            <template v-else-if="column.key === 'status'"><a-tag :color="statusTag(record.status).color">{{ statusTag(record.status).label }}</a-tag></template>
            <template v-else-if="column.key === 'reviewTime'">{{ record.reviewTime ? fmtDateTime(record.reviewTime) : '—' }}</template>
            <template v-else-if="column.key === 'reason'"><span v-if="record.reason">{{ record.reason }}</span><span v-else class="ct-panel-meta">—</span></template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a-tooltip title="企业信息、业务信息与审查记录"><a @click="openDetail(record)">详情</a></a-tooltip>
                <template v-if="isPlatform && record.status === 'PENDING'">
                  <a-divider type="vertical" />
                  <a-tooltip title="通过后该证照/资质方可被其他接口引用"><a @click="confirmApprove(record)">通过</a></a-tooltip>
                  <a-tooltip title="驳回需填写原因（企业可见）"><a class="text-red-600" @click="confirmReject(record)">驳回</a></a-tooltip>
                </template>
              </a-space>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">{{ isPlatform ? '该筛选条件下暂无审查任务：可调整筛选条件；企业新录入证照/资质后会自动生成待审查任务' : '本企业暂无审查任务：录入证照/资质后会自动生成，审查结果（通过/驳回与原因）会显示在这里' }}</div></template>
        </a-table>
      </div>
    </div>

    <div class="ct-panel" style="margin-top: 12px">
      <div class="ct-panel-h">
        <span class="ct-panel-t">主体授权</span>
        <span class="ct-panel-meta">平台侧授权台账（投标 / 招标资格的前置条件，授权由平台完成）</span>
        <a-button v-if="canSubjectAuthUpdate" type="primary" size="small" @click="openAuthorize">授权企业主体</a-button>
      </div>
      <div class="ct-panel-b">
        <a-space wrap class="mb-2">
          <a-input v-model:value="authQuery.tenantName" allow-clear placeholder="按租户名称筛选" style="width: 200px" @press-enter="searchAuth" />
          <a-select v-model:value="authQuery.subjectType" allow-clear :options="subjectTypeOptions" placeholder="主体类型" style="width: 130px" />
          <a-select v-model:value="authQuery.authStatus" allow-clear :options="authStatusOptions" placeholder="授权状态" style="width: 130px" />
          <a-button type="primary" @click="searchAuth">查询</a-button>
        </a-space>
        <a-table
          :columns="authColumns"
          :data-source="authRows"
          :loading="authLoading"
          :pagination="{ current: authQuery.pageNo, pageSize: authQuery.pageSize, total: authTotal, showSizeChanger: true }"
          row-key="id"
          size="small"
          @change="(page: any) => { authQuery.pageNo = page.current; authQuery.pageSize = page.pageSize; void loadAuth(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'subjectType'">{{ subjectTypeText(record.subjectType) }}</template>
            <template v-else-if="column.key === 'authStatus'">
              <a-tag :color="record.authStatus === 'ENABLED' ? 'green' : 'red'">{{ record.authStatus === 'ENABLED' ? '已授权' : '已停用' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'profile'">
              <a-tag v-if="!record.profileExists" color="orange">未建档</a-tag>
              <a-tag v-else-if="record.profileComplete" color="green">已完善</a-tag>
              <a-tag v-else color="orange">待完善</a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a v-if="canSubjectAuthUpdate && record.authStatus === 'ENABLED'" class="text-red-600" @click="toggleAuthStatus(record, 'DISABLED')">停用</a>
              <a v-else-if="canSubjectAuthUpdate" @click="toggleAuthStatus(record, 'ENABLED')">重新启用</a>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无授权记录：点击右上「授权企业主体」填租户编号即可授权（授权后该企业方可参与投标 / 招标流程）</div></template>
        </a-table>
      </div>

      <a-modal v-model:open="authOpen" :confirm-loading="authBusy" title="授权企业主体" @ok="doAuthorize">
        <a-form layout="vertical">
          <a-form-item label="租户编号" required>
            <a-input v-model:value="authForm.tenantId" placeholder="企业租户 ID（如 1）" />
          </a-form-item>
          <a-form-item label="租户名称">
            <a-input v-model:value="authForm.tenantName" placeholder="企业名称（可留空）" />
          </a-form-item>
          <a-form-item label="主体类型" required>
            <a-select v-model:value="authForm.subjectType" :options="subjectTypeOptions" style="width: 100%" />
          </a-form-item>
          <a-form-item label="备注">
            <a-textarea v-model:value="authForm.remark" :rows="3" placeholder="授权依据 / 备注（选填）" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
</div>
</template>
