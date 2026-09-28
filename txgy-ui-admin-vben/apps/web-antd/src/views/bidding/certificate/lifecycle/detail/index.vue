<script setup lang="ts">
/* 证件全生命周期 · 记录详情页 —— 独立路由 /bidding/certificate/lifecycle/detail/:id（hideInMenu）
 * 2026-09-28 整改：原来「详情」是抽屉，改为独立详情页 + 分区：基本信息 / 文件版本 / 变更历史。
 * 文件口径：佐证材料按 files[]（Q16）优先、旧数据回退 evidenceFileIds；**每份都能预览**——
 *   url（上传回执缓存命中）优先，未命中用 fileId → PdfPreview 走业务受控内联出口 /bidding/file/download。
 *   佐证材料随申请单提交（后端无"给已有记录追加附件"的接口），故本页不提供会 404 的上传入口。 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  auditCertLifecycle,
  CERT_ERR,
  certErrorCode,
  type CertificateApi,
  effectCertLifecycle,
  getCertLifecycle,
  getCertLifecycleByBiz,
  urgeCertLifecycle,
} from '#/api/bidding/certificate';
import { withBiddingFileToken } from '#/api/bidding/file';

import { type FileVersionRow } from '../../../components/fileVersion';
import FileVersionTable from '../../../components/FileVersionTable.vue';
import { recallFileRefs } from '../../../vo/fileRefCache';
import PdfPreview from '../../../vo/PdfPreview.vue';
import { fmtDateTime } from '../../format';
import { useCertMeta } from '../../useCertMeta';

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictLabel, dictOptions, dictTag, isTerminal } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const recordId = computed(() => String(route.params.id ?? ''));
const loading = ref(false);
const detail = ref<CertificateApi.Lifecycle | null>(null);
const tab = ref('basic');

async function load(): Promise<void> {
  if (!recordId.value) return;
  loading.value = true;
  try {
    detail.value = await getCertLifecycle(recordId.value);
    await loadHistory();
  } catch {
    detail.value = null;
  } finally {
    loading.value = false;
  }
}

function contentText(r: CertificateApi.Lifecycle | null): string {
  if (!r) return '—';
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

/** 审核可见性：终态由字典 lifecycle_status.terminal 判定，不写死状态集合 */
function auditable(r: CertificateApi.Lifecycle | null): boolean {
  return !!r && !isTerminal('lifecycle_status', r.status);
}

/* ===================== 文件版本（佐证材料） ===================== */
const preview = reactive<{ fileId?: number | string; name: string; open: boolean; url: string }>({
  fileId: undefined,
  name: '',
  open: false,
  url: '',
});
function openPreview(row: FileVersionRow): void {
  preview.fileId = row.url ? undefined : row.fileId;
  preview.url = row.url ?? '';
  preview.name = row.name;
  preview.open = true;
}

/** 汇总佐证材料附件行：files[]（Q16）优先；旧数据回退 evidenceFileIds（名称回退"附件 #id"） */
const fileRows = computed<FileVersionRow[]>(() => {
  const row = detail.value;
  if (!row) return [];
  const cache = recallFileRefs(row.evidenceFileIds ?? []);
  const fromApi = row.files ?? [];
  const source: Array<{ fileCategory?: string; fileId: number | string; fileName?: string; uploadTime?: null | number | string }> =
    fromApi.length > 0
      ? fromApi.map((f) => ({
          fileId: f.fileId,
          fileName: f.fileName,
          fileCategory: f.fileCategory,
          uploadTime: f.uploadTime,
        }))
      : (row.evidenceFileIds ?? []).map((id) => ({ fileId: id }));
  return source
    .filter((f) => f.fileId !== null && f.fileId !== undefined && f.fileId !== '')
    .map((f) => {
      const hit = cache.get(String(f.fileId));
      const url = hit?.url ?? undefined;
      return {
        fileId: f.fileId,
        name: f.fileName || hit?.name || `附件 #${f.fileId}`,
        categoryLabel: f.fileCategory ? dictLabel('file_category', f.fileCategory) : undefined,
        uploadTime: f.uploadTime,
        url: url ? withBiddingFileToken(url) : undefined,
      };
    });
});

/* ===================== 变更历史（同对象全部流程） ===================== */
const historyLoading = ref(false);
const history = ref<CertificateApi.Lifecycle[]>([]);
async function loadHistory(): Promise<void> {
  const row = detail.value;
  if (!row?.bizDomain || !row.bizId) {
    history.value = [];
    return;
  }
  historyLoading.value = true;
  try {
    history.value = await getCertLifecycleByBiz(row.bizDomain, row.bizId);
  } catch {
    history.value = [];
  } finally {
    historyLoading.value = false;
  }
}
function openRecord(id: number | string | undefined): void {
  if (id === undefined || id === null) return;
  void router.push(`/bidding/certificate/lifecycle/detail/${id}`);
}

/* ===================== 审核 / 生效 / 催办 ===================== */
const auditOpen = ref(false);
const auditSaving = ref(false);
const auditForm = reactive<{ remark?: string; targetStatus?: string }>({ remark: undefined, targetStatus: undefined });
const AUDIT_TARGETS = ['REVIEWING', 'APPROVED', 'REJECTED'];
const auditTargets = computed(() =>
  dictOptions('lifecycle_status')
    .filter((o) => AUDIT_TARGETS.includes(o.value))
    .map((o) => ({ label: o.label, value: o.value })),
);
function openAudit(): void {
  auditForm.targetStatus = undefined;
  auditForm.remark = undefined;
  auditOpen.value = true;
}
async function submitAudit(): Promise<void> {
  const row = detail.value;
  if (!row?.id) return;
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
      id: row.id,
      targetStatus: auditForm.targetStatus,
      version: row.version,
      remark: auditForm.remark,
    });
    message.success('审核已提交');
    auditOpen.value = false;
    await load();
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.LIFECYCLE_FLOW_DENIED) {
      message.warning('状态流转非法（待办→审核中→通过→生效），已刷新');
      auditOpen.value = false;
      await load();
    } else if (code === CERT_ERR.LIFECYCLE_OPTIMISTIC_LOCK) {
      message.warning('记录已被他人修改，已重新载入最新版本');
      await load();
    } else if (code === CERT_ERR.LIFECYCLE_NOT_FOUND) {
      message.error('生命周期记录不存在，已刷新');
      auditOpen.value = false;
    }
  } finally {
    auditSaving.value = false;
  }
}

const effectOpen = ref(false);
const effectSaving = ref(false);
const effectRemark = ref('');
function openEffect(): void {
  effectRemark.value = '';
  effectOpen.value = true;
}
async function submitEffect(): Promise<void> {
  const row = detail.value;
  if (!row?.id) return;
  if (row.status !== 'APPROVED') {
    message.warning('仅审核通过的申请可生效');
    return;
  }
  effectSaving.value = true;
  try {
    await effectCertLifecycle({ id: row.id, version: row.version, remark: effectRemark.value || undefined });
    message.success('已生效并回写台账');
    effectOpen.value = false;
    await load();
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.LIFECYCLE_FLOW_DENIED) {
      message.warning('仅审核通过的申请可生效，已刷新');
      effectOpen.value = false;
      await load();
    } else if (code === CERT_ERR.LIFECYCLE_OPTIMISTIC_LOCK || code === CERT_ERR.OPTIMISTIC_LOCK) {
      message.warning('数据已被他人修改，请刷新后重试');
      effectOpen.value = false;
    }
  } finally {
    effectSaving.value = false;
  }
}

const urging = ref(false);
async function doUrge(): Promise<void> {
  const row = detail.value;
  if (!row?.id) return;
  urging.value = true;
  try {
    await urgeCertLifecycle({ id: row.id });
    message.success('已催办，流程责任人会收到提醒');
  } catch {
    /* 后端未实现 / 无权限：错误由请求层提示 */
  } finally {
    urging.value = false;
  }
}

function goBack(): void {
  // 用浏览器历史返回，避免硬编码菜单路径（菜单 path 随部署而变，写死会跳 404）
  if (window.history.length > 1) router.back();
  else void router.push('/');
}
onMounted(load);
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">生命周期记录详情 · {{ detail?.applyNo || recordId }}</h2>
        <p class="ct-sub">基本信息 / 文件版本（佐证材料，可预览 · 下载）/ 变更历史 三个分区。</p>
      </div>
      <div class="ct-actions">
        <a-button @click="goBack">
          <IconifyIcon icon="lucide:arrow-left" class="mr-1 inline-block align-middle" />
          返回证件全生命周期
        </a-button>
        <a-button v-if="can('bidding:certificate:lifecycle:urge')" :loading="urging" @click="doUrge">催办</a-button>
        <a-button v-if="can('bidding:certificate:lifecycle:audit') && auditable(detail)" @click="openAudit">审核</a-button>
        <a-button
          v-if="can('bidding:certificate:lifecycle:audit') && detail?.status === 'APPROVED'"
          type="primary"
          @click="openEffect"
        >
          生效
        </a-button>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <a-tabs v-model:active-key="tab">
        <a-tab-pane key="basic" tab="基本信息">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">流程信息</span>
              <span class="ct-panel-meta">审核通过 ≠ 已生效；生效才回写台账</span>
            </div>
            <div class="ct-panel-b">
              <a-descriptions :column="3" size="small" bordered>
                <a-descriptions-item label="申请单号">{{ detail?.applyNo || '—' }}</a-descriptions-item>
                <a-descriptions-item label="流程类型">{{ dictLabel('lifecycle_type', detail?.lifecycleType) }}</a-descriptions-item>
                <a-descriptions-item label="流程状态">
                  <a-tag :color="dictTag('lifecycle_status', detail?.status).color">
                    {{ dictTag('lifecycle_status', detail?.status).label }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="对象域">{{ dictLabel('biz_domain', detail?.bizDomain) }}</a-descriptions-item>
                <a-descriptions-item label="作用对象" :span="2">
                  {{ detail?.refName || '—' }}
                  <span class="ct-id">{{ detail?.refNo || (detail?.bizId ? `#${detail.bizId}` : '') }}</span>
                </a-descriptions-item>
                <a-descriptions-item label="变更字段">{{ detail?.fieldName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="新旧值" :span="2">
                  {{ detail?.fieldName ? `${detail?.oldValue || '空'} → ${detail?.newValue || '空'}` : '—' }}
                </a-descriptions-item>
                <a-descriptions-item label="有效期（前 → 后）" :span="3">
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
                <a-descriptions-item label="变更归组号">{{ detail?.changeGroupId || '—' }}</a-descriptions-item>
                <a-descriptions-item label="记录版本">v{{ detail?.version ?? '—' }}</a-descriptions-item>
                <a-descriptions-item label="事由 / 备注" :span="2">
                  {{ detail?.remark || detail?.revokeReason || '—' }}
                </a-descriptions-item>
              </a-descriptions>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="files" tab="文件版本">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">佐证材料</span>
              <span class="ct-panel-meta">随申请单提交；每份均可预览（内联）与下载</span>
            </div>
            <div class="ct-panel-b">
              <a-alert
                class="mb-3"
                type="info"
                show-icon
                message="佐证材料在发起流程时一并提交"
                description="后端未提供「给已有记录追加附件」的接口，为避免点了报错，本页不放置上传入口；如需补材料，请在本台账发起新的变更申请并随单上传。"
              />
              <FileVersionTable
                :rows="fileRows"
                empty-text="该流程未上传佐证材料（旧数据可能只有文件编号，已按编号列出并支持预览）"
                @preview="openPreview"
              />
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="history" tab="变更历史">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">同一对象的全部流程记录</span>
              <span class="ct-panel-meta">{{ detail?.refName || '—' }} · 按申请时间倒序</span>
            </div>
            <div class="ct-panel-b">
              <a-spin :spinning="historyLoading">
                <a-timeline v-if="history.length">
                  <a-timeline-item v-for="h in history" :key="h.id">
                    <div class="flex flex-wrap items-center gap-2">
                      <a-tag>{{ dictLabel('lifecycle_type', h.lifecycleType) }}</a-tag>
                      <a-tag :color="dictTag('lifecycle_status', h.status).color">
                        {{ dictTag('lifecycle_status', h.status).label }}
                      </a-tag>
                      <span class="ct-id">{{ h.applyNo }}</span>
                      <a-tag v-if="h.id === detail?.id" color="blue">当前记录</a-tag>
                    </div>
                    <div class="mt-1">{{ contentText(h) }}</div>
                    <div class="ct-panel-meta">
                      申请 {{ fmtDateTime(h.appliedAt) }}
                      <template v-if="h.handledAt"> · 处理 {{ fmtDateTime(h.handledAt) }}</template>
                      <template v-if="h.effectiveAt"> · 生效 {{ fmtDateTime(h.effectiveAt) }}</template>
                    </div>
                    <div v-if="h.id !== detail?.id" class="mt-1">
                      <a @click="openRecord(h.id)">查看该次记录</a>
                    </div>
                  </a-timeline-item>
                </a-timeline>
                <div v-else class="ct-empty">该对象暂无其他流程记录</div>
              </a-spin>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-spin>

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
      <div class="mb-3"><span class="ct-id">{{ detail?.applyNo }}</span> · 当前 v{{ detail?.version }}</div>
      <div class="ct-lb"><span class="ct-ast">*</span>目标状态</div>
      <a-select
        v-model:value="auditForm.targetStatus"
        class="mb-3 w-full"
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
      <div class="ct-lb">生效备注（如决定书文号）</div>
      <a-textarea v-model:value="effectRemark" :rows="3" />
    </a-modal>

    <PdfPreview
      v-model:open="preview.open"
      :file-id="preview.fileId"
      :url="preview.url"
      :file-name="preview.name"
      :title="preview.name || '佐证材料预览'"
    />
  </div>
</template>
