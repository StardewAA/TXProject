<script setup lang="ts">
/* 证件全生命周期台账（契约 §6.2 审核 / §6.3 生效 / §6.4 详情 / §6.5 分页 + §7 材料完备性自查入口）
 * 状态机：PENDING → REVIEWING → APPROVED → EFFECTIVE（可 REJECTED）；**审核通过 ≠ 已生效**，故"生效"独立成动作。
 * 权限：bidding:certificate:lifecycle:{query,apply,audit}、bidding:qualification:material:query */
import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  auditCertLifecycle,
  CERT_ERR,
  certErrorCode,
  type CertificateApi,
  effectCertLifecycle,
  getCertLifecycle,
  getCertLifecyclePage,
} from '#/api/bidding/certificate';

import AttrFilter from '../../components/AttrFilter.vue';
import CopyText from '../../components/CopyText.vue';
import { fmtDateTime } from '../format';
import LifecycleApply from '../modules/lifecycle-apply.vue';
import MaterialCheck from '../modules/material-check.vue';
import { useCertMeta } from '../useCertMeta';

import '../cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictLabel, dictOptions, dictTag, isTerminal, metaError } = useCertMeta();

/** 审核按钮可见性：非终态（终态由字典 lifecycle_status.terminal 判定，不写死状态集合） */
function auditable(r: CertificateApi.Lifecycle): boolean {
  return !isTerminal('lifecycle_status', r.status);
}

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<CertificateApi.LifecyclePageReq>({
  pageNo: 1,
  pageSize: 10,
  bizDomain: undefined,
  bizId: undefined,
  lifecycleType: undefined,
  status: undefined,
  applyNo: undefined,
});
const loading = ref(false);
const rows = ref<CertificateApi.Lifecycle[]>([]);
const total = ref(0);

const applyOpen = ref(false);
const materialOpen = ref(false);
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<CertificateApi.Lifecycle | null>(null);

/* 审核弹窗 */
const auditOpen = ref(false);
const auditRow = ref<CertificateApi.Lifecycle | null>(null);
const auditSaving = ref(false);
const auditForm = reactive<{ remark?: string; targetStatus?: string }>({ remark: undefined, targetStatus: undefined });

/* 生效弹窗 */
const effectOpen = ref(false);
const effectRow = ref<CertificateApi.Lifecycle | null>(null);
const effectSaving = ref(false);
const effectRemark = ref('');

async function load() {
  loading.value = true;
  try {
    const page = await getCertLifecyclePage({ ...query });
    rows.value = page.list ?? [];
    total.value = page.total ?? 0;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}
function resetQuery() {
  query.bizDomain = undefined;
  query.bizId = undefined;
  query.lifecycleType = undefined;
  query.status = undefined;
  query.applyNo = undefined;
  query.pageNo = 1;
  void load();
}
onMounted(load);

const columns = [
  { title: '申请单号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '申请单号' }),  dataIndex: 'applyNo', key: 'applyNo', width: 190 },
  { title: '作用对象', dataIndex: 'refName', key: 'ref', width: 230 },
  { title: '类型', dataIndex: 'lifecycleType', key: 'lifecycleType', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '变更/续期内容', dataIndex: 'fieldName', key: 'content', width: 260 },
  { title: '申请', dataIndex: 'appliedAt', key: 'applied', width: 165 },
  { title: '处理', dataIndex: 'handledAt', key: 'handled', width: 165 },
  { title: '生效时间', dataIndex: 'effectiveAt', key: 'effectiveAt', width: 165 },
  { title: '操作', key: 'action', width: 190, fixed: 'right' },
];

/** 契约 §6.2 明示的审核目标状态（REVIEWING 受理 / APPROVED 通过 / REJECTED 驳回） */
const AUDIT_TARGETS = ['REVIEWING', 'APPROVED', 'REJECTED'];
const auditTargets = computed(() =>
  dictOptions('lifecycle_status')
    .filter((o) => AUDIT_TARGETS.includes(o.value))
    .map((o) => ({ label: o.label, value: o.value })),
);

function contentText(r: CertificateApi.Lifecycle): string {
  if (r.lifecycleType === 'CHANGE') {
    if (r.fieldName) return `${r.fieldName}：${r.oldValue || '空'} → ${r.newValue || '空'}`;
    return r.changeSummary || '—';
  }
  if (r.lifecycleType === 'RENEW') {
    return `${dictLabel('renewal_type', r.renewalType)}；新有效期 ${r.newValidFrom || '—'} ~ ${r.newValidTo || '—'}`;
  }
  if (r.lifecycleType === 'REVOKE') {
    return `${dictLabel('revoke_type', r.revokeType)}；${r.revokeReason || '—'}`;
  }
  if (r.lifecycleType === 'ANNUAL') {
    return `${r.reviewYear ?? '—'} 年度年检，${r.reviewDate || '—'}`;
  }
  return '—';
}

async function openDetail(r: CertificateApi.Lifecycle) {
  detailOpen.value = true;
  detail.value = r;
  detailLoading.value = true;
  try {
    detail.value = await getCertLifecycle(r.id);
  } catch {
    /* 用列表行兜底 */
  } finally {
    detailLoading.value = false;
  }
}

function openAudit(r: CertificateApi.Lifecycle) {
  auditRow.value = r;
  auditForm.targetStatus = undefined;
  auditForm.remark = undefined;
  auditOpen.value = true;
  getCertLifecycle(r.id)
    .then((d) => {
      auditRow.value = d;
    })
    .catch(() => {
      /* 冲突时后端给 1060107017 */
    });
}

async function submitAudit() {
  if (!auditRow.value?.id) return;
  if (!auditForm.targetStatus) {
    message.warning('请选择目标状态');
    return;
  }
  if (auditForm.targetStatus === 'REJECTED' && !auditForm.remark?.trim()) {
    message.warning('驳回必须填写审批意见');
    return;
  }
  auditSaving.value = true;
  try {
    await auditCertLifecycle({
      id: auditRow.value.id,
      targetStatus: auditForm.targetStatus,
      version: auditRow.value.version,
      remark: auditForm.remark,
    });
    message.success('审核已提交');
    auditOpen.value = false;
    void load();
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.LIFECYCLE_FLOW_DENIED) {
      message.warning('状态流转非法（待办→审核中→通过→生效），已刷新');
      auditOpen.value = false;
      void load();
    } else if (code === CERT_ERR.LIFECYCLE_OPTIMISTIC_LOCK) {
      message.warning('记录已被他人修改，已重新载入最新版本');
      if (auditRow.value) {
        const latest = await getCertLifecycle(auditRow.value.id).catch(() => null);
        if (latest) auditRow.value = latest;
      }
    } else if (code === CERT_ERR.LIFECYCLE_NOT_FOUND) {
      message.error('生命周期记录不存在，已刷新列表');
      auditOpen.value = false;
      void load();
    }
  } finally {
    auditSaving.value = false;
  }
}

async function openEffect(r: CertificateApi.Lifecycle) {
  effectRow.value = r;
  effectRemark.value = '';
  effectOpen.value = true;
  // 乐观锁：生效前重取 §6.4 详情，拿最新 version（列表行 version 可能已过期）
  try {
    effectRow.value = await getCertLifecycle(r.id);
  } catch {
    // 详情取失败时保留列表行数据，由后端乐观锁兜底
  }
}

async function submitEffect() {
  if (!effectRow.value?.id) return;
  if (effectRow.value.status !== 'APPROVED') {
    message.warning('仅审核通过的申请可生效');
    return;
  }
  effectSaving.value = true;
  try {
    await effectCertLifecycle({
      id: effectRow.value.id,
      version: effectRow.value.version,
      remark: effectRemark.value || undefined,
    });
    message.success('已生效并回写台账');
    effectOpen.value = false;
    void load();
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.LIFECYCLE_FLOW_DENIED) {
      message.warning('仅审核通过的申请可生效，已刷新');
      effectOpen.value = false;
      void load();
    } else if (code === CERT_ERR.LIFECYCLE_OPTIMISTIC_LOCK || code === CERT_ERR.OPTIMISTIC_LOCK) {
      message.warning('数据已被他人修改，请刷新后重试');
      effectOpen.value = false;
      void load();
    }
  } finally {
    effectSaving.value = false;
  }
}

const domainFilters = computed(() => dictOptions('biz_domain'));
const typeFilters = computed(() => dictOptions('lifecycle_type'));
const statusFilters = computed(() => dictOptions('lifecycle_status'));
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">证件全生命周期</h2>
        <p class="ct-sub">
          变更 / 续期 / 注销吊销 / 年检 四类流程的统一台账：先申请、后审核、再
          <b>生效（生效才回写台账）</b>。审批留痕可追溯"谁批的、什么时候批的"。
        </p>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:certificate:lifecycle:apply')" type="primary" @click="applyOpen = true">
          发起流程
        </a-button>
        <a-button v-if="can('bidding:qualification:material:query')" @click="materialOpen = true">
          材料完备性自查
        </a-button>
        <a-button @click="load">刷新</a-button>
      </div>
    </div>

    <a-alert
      v-if="metaError"
      type="warning"
      show-icon
      class="mb-3"
      message="字典/配置未加载，状态与类型将按 code 展示"
      :description="`${metaError}｜/bidding/meta/* 需电子证照域任一页面权限（后端 hasAnyPermissions，已含 certificate:lifecycle:query）；补齐后可恢复中文名。`"
    />

    <div class="ct-panel">
      <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
        <AttrFilter v-model="query.bizDomain" label="对象域" :options="domainFilters" />
        <AttrFilter v-model="query.lifecycleType" label="流程类型" :options="typeFilters" />
        <AttrFilter v-model="query.status" label="流程状态" :options="statusFilters" />
        <AttrFilter label="申请单号（精确）"><a-input v-model:value="query.applyNo" placeholder="申请单号（精确）" style="width: 200px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="对象主键 bizId"><a-input-number v-model:value="query.bizId" :min="1" placeholder="对象主键 bizId" style="width: 160px" /></AttrFilter>
            <div class="mt-2 flex flex-wrap items-center gap-2"><a-button type="primary" @click="load">查询</a-button> <a-button @click="resetQuery">重置</a-button></div>
          </div>
      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="{
            current: query.pageNo,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          row-key="id"
          size="small"
          :scroll="{ x: 1700 }"
          @change="
            (p: any) => {
              query.pageNo = p.current;
              query.pageSize = p.pageSize;
              load();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'applyNo'">
              <div class="ct-id">{{ record.applyNo }}</div>
              <div class="ct-panel-meta">#{{ record.id }} · v{{ record.version }}</div>
            </template>
            <template v-else-if="column.key === 'ref'">
              <div style="font-weight: 700">{{ record.refName || '—' }}</div>
              <div class="ct-panel-meta">
                {{ dictLabel('biz_domain', record.bizDomain) }} · {{ record.refNo || `#${record.bizId}` }}
              </div>
            </template>
            <template v-else-if="column.key === 'lifecycleType'">
              <a-tag>{{ dictLabel('lifecycle_type', record.lifecycleType) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="dictTag('lifecycle_status', record.status).color">
                {{ dictTag('lifecycle_status', record.status).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'content'">{{ contentText(record) }}</template>
            <template v-else-if="column.key === 'effectiveAt'">
              {{ record.effectiveAt ? fmtDateTime(record.effectiveAt) : '未生效' }}
            </template>
            <template v-else-if="column.key === 'applied'">{{ fmtDateTime(record.appliedAt) }}</template>
            <template v-else-if="column.key === 'handled'">{{ fmtDateTime(record.handledAt) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="openDetail(record)">详情</a>
                <!-- §6.2④/§6.3④：审核仅待办/审核中可点；生效仅审核通过可点（终态/已生效不再展示） -->
                <a
                  v-if="can('bidding:certificate:lifecycle:audit') && auditable(record)"
                  @click="openAudit(record)"
                >
                  审核
                </a>
                <a
                  v-if="can('bidding:certificate:lifecycle:audit') && record.status === 'APPROVED'"
                  @click="openEffect(record)"
                >
                  生效
                </a>
              </a-space>
            </template>
          </template>
          <template #emptyText>
            <div class="ct-empty">暂无流程记录</div>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 流程详情：新旧值对照 -->
    <a-drawer v-model:open="detailOpen" class="bid-ind" :width="820" title="生命周期记录详情" destroy-on-close>
      <a-spin :spinning="detailLoading">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="记录 ID"><span class="ct-id">#{{ detail?.id }}</span></a-descriptions-item>
          <a-descriptions-item label="乐观锁版本">v{{ detail?.version ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="申请单号">{{ detail?.applyNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="dictTag('lifecycle_status', detail?.status).color">
              {{ dictTag('lifecycle_status', detail?.status).label }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="对象域">
            {{ dictLabel('biz_domain', detail?.bizDomain) }}
          </a-descriptions-item>
          <a-descriptions-item label="对象">
            {{ detail?.refName || '—' }} <span class="ct-id">{{ detail?.refNo || `#${detail?.bizId}` }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="流程类型">
            {{ dictLabel('lifecycle_type', detail?.lifecycleType) }}
          </a-descriptions-item>
          <a-descriptions-item label="变更归组号">{{ detail?.changeGroupId || '—' }}</a-descriptions-item>
          <a-descriptions-item label="变更字段">{{ detail?.fieldName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="新旧值">
            {{ detail?.fieldName ? `${detail?.oldValue || '空'} → ${detail?.newValue || '空'}` : '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="有效期（前 → 后）">
            {{ detail?.oldValidTo || '—' }} → {{ detail?.newValidTo || '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="续期方式">{{ dictLabel('renewal_type', detail?.renewalType) }}</a-descriptions-item>
          <a-descriptions-item label="注销吊销方式">{{ dictLabel('revoke_type', detail?.revokeType) }}</a-descriptions-item>
          <a-descriptions-item label="决定机关">{{ detail?.revokeAuthority || '—' }}</a-descriptions-item>
          <a-descriptions-item label="年检">
            {{ detail?.reviewYear ? `${detail.reviewYear} 年度 · ${detail.reviewDate || '—'}` : '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="申请人 / 时间">
            {{ detail?.appliedBy || '—' }} / {{ fmtDateTime(detail?.appliedAt) }}
          </a-descriptions-item>
          <a-descriptions-item label="处理人 / 时间">
            {{ detail?.handledBy || '—' }} / {{ fmtDateTime(detail?.handledAt) }}
          </a-descriptions-item>
          <a-descriptions-item label="生效时间">
            {{ detail?.effectiveAt ? fmtDateTime(detail.effectiveAt) : '未生效' }}
          </a-descriptions-item>
          <a-descriptions-item label="事由 / 备注" :span="2">{{ detail?.remark || detail?.revokeReason || '—' }}</a-descriptions-item>
          <a-descriptions-item label="佐证材料 fileId" :span="2">
            {{ detail?.evidenceFileIds?.length ? detail.evidenceFileIds.join('、') : '—' }}
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-drawer>

    <!-- 审核 -->
    <a-modal
      v-model:open="auditOpen"
      title="审核流转"
      :width="560"
      :confirm-loading="auditSaving"
      ok-text="提交审核"
      cancel-text="取消"
      destroy-on-close
      @ok="submitAudit"
    >
      <div class="ct-lb">申请单号</div>
      <div class="mb-3"><span class="ct-id">{{ auditRow?.applyNo }}</span> · 当前 v{{ auditRow?.version }}</div>
      <div class="ct-lb"><span class="ct-ast">*</span>目标状态</div>
      <a-select
        v-model:value="auditForm.targetStatus"
        class="w-full mb-3"
        :options="auditTargets"
        :placeholder="auditTargets.length ? '受理 / 通过 / 驳回' : '字典 lifecycle_status 为空'"
        :disabled="auditTargets.length === 0"
      />
      <div class="ct-lb">审批意见<span v-if="auditForm.targetStatus === 'REJECTED'" class="ct-ast">*</span></div>
      <a-textarea
        v-model:value="auditForm.remark"
        :rows="3"
        :placeholder="auditForm.targetStatus === 'REJECTED' ? '驳回必须填写意见' : '选填'"
      />
      <div class="ct-tip">提交时回传乐观锁版本；状态流转非法（如待办直接生效）会被后端拒绝并自动刷新。</div>
    </a-modal>

    <!-- 生效 -->
    <a-modal
      v-model:open="effectOpen"
      title="流程生效（回写台账）"
      :width="560"
      :confirm-loading="effectSaving"
      ok-text="确认生效"
      cancel-text="取消"
      destroy-on-close
      @ok="submitEffect"
    >
      <a-alert
        class="mb-3"
        type="info"
        show-icon
        message="生效会把新有效期/变更字段/注销状态/下次年检日一次性回写台账，并驱动下一轮到期预警。"
      />
      <div class="ct-lb">申请单号</div>
      <div class="mb-3">
        <span class="ct-id">{{ effectRow?.applyNo }}</span> · 当前状态
        {{ dictLabel('lifecycle_status', effectRow?.status) }}
      </div>
      <div class="ct-lb">生效备注（如决定书文号）</div>
      <a-textarea v-model:value="effectRemark" :rows="3" />
    </a-modal>

    <a-drawer v-model:open="materialOpen" class="bid-ind" :width="980" title="企业材料完备性自查" destroy-on-close>
      <MaterialCheck />
    </a-drawer>

    <LifecycleApply v-model:open="applyOpen" :cert="null" @applied="load" />
  </div>
</template>
