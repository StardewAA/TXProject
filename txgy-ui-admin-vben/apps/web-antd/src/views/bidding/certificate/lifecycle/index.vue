<script setup lang="ts">
/* 证件全生命周期台账（契约 §6.2 审核 / §6.3 生效 / §6.4 详情 / §6.5 分页 + §7 材料完备性自查入口）
 * 状态机：PENDING → REVIEWING → APPROVED → EFFECTIVE（可 REJECTED）；**审核通过 ≠ 已生效**，故"生效"独立成动作。
 * 权限：bidding:certificate:lifecycle:{query,apply,audit}、bidding:qualification:material:query */
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  auditCertLifecycle,
  CERT_ERR,
  certErrorCode,
  type CertificateApi,
  effectCertLifecycle,
  getCertificatePage,
  getCertLifecycle,
  getCertLifecycleByBiz,
  getCertLifecyclePage,
  urgeCertLifecycle,
} from '#/api/bidding/certificate';

import AttrFilter from '../../components/AttrFilter.vue';
import CopyText from '../../components/CopyText.vue';
import { recallFileRefs } from '../../vo/fileRefCache';
import PdfPreview from '../../vo/PdfPreview.vue';
import { fmtDateTime } from '../format';
import LifecycleApply from '../modules/lifecycle-apply.vue';
import MaterialCheck from '../modules/material-check.vue';
import { useCertMeta } from '../useCertMeta';

import '../cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const router = useRouter();
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
  certOptions.value = [];
  void load();
}

/* 作用对象：不让用户手填无从得知的主键 bizId，改为按证照名称/编号检索选择 */
const certOptions = ref<Array<{ label: string; value: number }>>([]);
let certSearching = false;
async function searchCertObjects(kw: string) {
  if (!kw || certSearching) return;
  certSearching = true;
  try {
    const page = await getCertificatePage({ certName: kw, pageNo: 1, pageSize: 20 });
    certOptions.value = (page.list ?? []).map((c) => ({
      label: (c.certName || '') + (c.certNo ? '（' + c.certNo + '）' : ''),
      value: c.id,
    }));
  } catch {
    certOptions.value = [];
  } finally {
    certSearching = false;
  }
}
function onPickCertObject(id: number) {
  query.bizId = id;
  query.pageNo = 1;
  void load();
}
onMounted(() => {
  void load();
  void loadBoard();
});

const columns = [
  { title: '申请单号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '申请单号' }),  dataIndex: 'applyNo', key: 'applyNo', width: 190 },
  { title: '作用对象', dataIndex: 'refName', key: 'ref', width: 230, ellipsis: true },
  { title: '类型', dataIndex: 'lifecycleType', key: 'lifecycleType', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '变更/续期内容', dataIndex: 'fieldName', key: 'content', width: 260, ellipsis: true },
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

/** 「详情」→ 独立详情页（/bidding/certificate/lifecycle/detail/:id）；不再用抽屉/表格下方占位展示 */
function openDetail(r: CertificateApi.Lifecycle): void {
  void router.push(`/bidding/certificate/lifecycle/detail/${r.id}`);
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

/* ================= 2026-09-18 原型改造新增：看板 / 时间轴 / 列表 三视图 + 催办 =================
 * 契约出处：docs/电子证照前前端契约/后端接口契约-资质证书五页改造-2026-09-18.md
 * 视图分工：
 *   · 看板：按**流程状态**分列（lifecycle_status），一屏看"哪些卡在哪一步"——这就是流程视角；
 *     分列口径用 lifecycle_status（待办/审核中/已通过/已生效/已驳回），**不混用** cert_status
 *     （证照台账的"待验真/正常/临期/已注销"是另一套枚举，混列会让两套状态机对不上，见契约 Q14）。
 *   · 时间轴：选一张证照，倒序看它经历过什么（建档→变更→续期→审批→生效），逐节点可查附件/审批/催办。
 *   · 列表：既有台账视图，保留不动。 */

const viewMode = ref<'board' | 'list' | 'timeline'>('board');

/** 看板数据：一次取一批（页面 100 条上限，取自契约 §4.6 pageSize≤100），前端按状态分列。
 *  注：看板是"概览"，不做服务端分页；需要精确分页请切「列表」视图。 */
const boardLoading = ref(false);
const boardRows = ref<CertificateApi.Lifecycle[]>([]);
const BOARD_CAP = 100;

async function loadBoard(): Promise<void> {
  boardLoading.value = true;
  try {
    const page = await getCertLifecyclePage({ pageNo: 1, pageSize: BOARD_CAP });
    boardRows.value = page.list ?? [];
  } catch {
    boardRows.value = [];
  } finally {
    boardLoading.value = false;
  }
}

/** 看板分列：**从字典取状态集合**（不硬编码状态码），每列内为该状态的记录 */
const boardColumns = computed(() => {
  const dict = dictOptions('lifecycle_status');
  const cols = dict.length > 0 ? dict : [{ label: '待办', value: 'PENDING' }];
  return cols.map((c) => ({
    label: c.label,
    value: c.value,
    rows: boardRows.value.filter((r) => r.status === c.value),
  }));
});

/** 点看板列/卡片 → 带状态跳列表视图（保证"看到的数 = 列表里的条数"） */
function drillDown(status?: string): void {
  query.status = status;
  query.pageNo = 1;
  viewMode.value = 'list';
  void load();
}

/* ===== 时间轴视图：选证照 → 倒序节点 ===== */
const timelineCertId = ref<number | undefined>(undefined);
const timelineCertOptions = ref<Array<{ label: string; value: number }>>([]);
const timelineLoading = ref(false);
const timelineRows = ref<CertificateApi.Lifecycle[]>([]);

async function searchTimelineCerts(kw: string): Promise<void> {
  if (!kw) return;
  try {
    const page = await getCertificatePage({ certName: kw, pageNo: 1, pageSize: 20 });
    timelineCertOptions.value = (page.list ?? []).map((c) => ({
      label: (c.certName || '') + (c.certNo ? `（${c.certNo}）` : ''),
      value: Number(c.id),
    }));
  } catch {
    timelineCertOptions.value = [];
  }
}

async function loadTimeline(): Promise<void> {
  if (!timelineCertId.value) {
    timelineRows.value = [];
    return;
  }
  timelineLoading.value = true;
  try {
    timelineRows.value = await getCertLifecycleByBiz('CERT', timelineCertId.value);
  } catch {
    timelineRows.value = [];
  } finally {
    timelineLoading.value = false;
  }
}

/** 同一次变更（changeGroupId 相同）合并为一个节点，避免一屏全是同一批字段变更 */
const timelineNodes = computed(() => {
  const out: Array<{ groupId: string; head: CertificateApi.Lifecycle; members: CertificateApi.Lifecycle[] }> = [];
  const index = new Map<string, (typeof out)[number]>();
  for (const r of timelineRows.value) {
    const key = r.changeGroupId || `#${r.id}`;
    const hit = index.get(key);
    if (hit) {
      hit.members.push(r);
      continue;
    }
    const node = { groupId: key, head: r, members: [r] };
    index.set(key, node);
    out.push(node);
  }
  return out;
});

/* ===== 催办（契约 POST /bidding/cert-lifecycle/urge） ===== */
const urging = ref(false);
async function doUrge(row: CertificateApi.Lifecycle): Promise<void> {
  urging.value = true;
  try {
    await urgeCertLifecycle({ id: row.id });
    message.success('已催办，流程责任人会收到提醒');
  } catch {
    // 后端未实现 / 无权限：错误由请求层提示
  } finally {
    urging.value = false;
  }
}

/* ============ 佐证材料：附件行（可预览/下载） ============
 * 数据来源（优先级）：
 *   ① 后端 files[]（契约 Q16 / CertLifecycleFileRespVO：fileId/文件名/类别/上传时间）；
 *   ② 旧数据仅有 evidenceFileIds 时，逐 id 构造占位行（名称回退"附件 #id"）；
 *   ③ 可访问 URL 与真实文件名：优先取「上传回执缓存」（fileRefCache，本浏览器上传过的文件），
 *      未命中则由 PdfPreview 走 fileId 分支 —— 该分支已改为**业务受控出口**
 *      GET /bidding/file/download?id=（内联预览，无需 infra:file:query）。
 *      （2026-09-20 变更：原走 /infra/file/get，需 infra 权限且是强制下载不是预览；
 *        见《后端变更通知-电子证照域字典治理》§六-5、《电子证照域-权限与操作全表》§三）
 * 为什么不再只拼编号：原先佐证材料仅渲染"文件编号：x、y"文本，用户点不开也看不出是什么文件
 *   （对账文档 31 号 §二「有编号无预览」；后端 CertLifecycleFileRespVO 注释亦指向前端补齐展示）。 */
interface EvidenceRow {
  fileId: number | string;
  name: string;
  categoryLabel?: string;
  uploadTime?: number | string;
  /** 上传回执缓存命中的可访问 URL（未命中则空，由 PdfPreview 按 fileId 兜底） */
  url?: string;
}

/** 汇总某条生命周期的佐证材料附件行 */
function evidenceRows(row: CertificateApi.Lifecycle | null): EvidenceRow[] {
  if (!row) return [];
  const cache = recallFileRefs(row.evidenceFileIds ?? []);
  const fromApi = row.files ?? [];
  /** 统一源类型：后端 files[]（Q16）优先；缺省回退 evidenceFileIds（旧数据只有 id） */
  interface EvidenceSource {
    fileId: number | string;
    fileName?: string;
    fileCategory?: string;
    uploadTime?: number | string;
  }
  const list: EvidenceSource[] =
    fromApi.length > 0
      ? fromApi.map((f) => ({
          fileId: f.fileId,
          fileName: f.fileName,
          fileCategory: f.fileCategory,
          uploadTime: f.uploadTime,
        }))
      : (row.evidenceFileIds ?? []).map((id) => ({ fileId: id }));
  return list
    .filter((f) => f.fileId !== null && f.fileId !== undefined && f.fileId !== '')
    .map((f) => {
      const hit = cache.get(String(f.fileId));
      return {
        fileId: f.fileId,
        name: f.fileName || hit?.name || `附件 #${f.fileId}`,
        categoryLabel: f.fileCategory ? dictLabel('file_category', f.fileCategory) : undefined,
        uploadTime: f.uploadTime,
        url: hit?.url,
      };
    });
}

/** 佐证材料预览（url 优先；无 url 时交给 PdfPreview 的 fileId 分支） */
const evPreview = reactive<{ fileId?: number | string; name: string; open: boolean; url: string; }>({
  open: false,
  url: '',
  fileId: undefined,
  name: '',
});
function openEvidence(f: EvidenceRow | undefined): void {
  if (!f) return;
  evPreview.url = f.url ?? '';
  evPreview.fileId = f.url ? undefined : f.fileId;
  evPreview.name = f.name;
  evPreview.open = true;
}

/** 时间轴节点入口：多份时提示并打开第一份（其余在"查看审批详情"内逐份预览） */
function openEvidenceList(row: CertificateApi.Lifecycle): void {
  const rows = evidenceRows(row);
  if (!rows.length) return;
  if (rows.length > 1) message.info(`共 ${rows.length} 份佐证材料，已打开第 1 份；其余可在「查看审批详情」中逐份预览`);
  openEvidence(rows[0]);
}

watch(viewMode, (v) => {
  if (v === 'board' && boardRows.value.length === 0) void loadBoard();
  if (v === 'timeline' && timelineCertId.value) void loadTimeline();
});
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
        <a-radio-group v-model:value="viewMode" button-style="solid">
          <a-radio-button value="board">看板</a-radio-button>
          <a-radio-button value="timeline">时间轴</a-radio-button>
          <a-radio-button value="list">列表</a-radio-button>
        </a-radio-group>
        <!-- 入口文案要说清"能发起哪几类"：四类流程（lifecycle_type 字典）在本弹窗里是同一入口的四个分页选项，
             原写「发起流程」看不出点进去能干什么，使用者得点开才知道（#8 整改） -->
        <a-button v-if="can('bidding:certificate:lifecycle:apply')" type="primary" @click="applyOpen = true">
          发起变更 / 续期 / 注销 / 年检
        </a-button>
        <a-button v-if="can('bidding:qualification:material:query')" @click="materialOpen = true">
          材料完备性自查
        </a-button>
        <a-button @click="load(); loadBoard();">刷新</a-button>
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

    <!-- ========== 看板视图（默认）：按**流程状态**分列，一屏看"卡在哪一步" ========== -->
    <div v-if="viewMode === 'board'" class="ct-panel">
      <div class="ct-panel-h">
        <span class="ct-panel-t">流程看板</span>
        <span class="ct-panel-meta">分列口径 = 流程状态（lifecycle_status）；点列头进「列表」看该状态的精确分页</span>
        <a-button size="small" :loading="boardLoading" @click="loadBoard">刷新看板</a-button>
      </div>
      <div class="ct-panel-b">
        <a-spin :spinning="boardLoading">
          <div class="lc-board">
            <div v-for="col in boardColumns" :key="col.value" class="lc-col">
              <div class="lc-col-h">
                <a @click="drillDown(col.value)">{{ col.label }}</a>
                <a-tag>{{ col.rows.length }}</a-tag>
              </div>
              <div class="lc-col-b">
                <div v-for="r in col.rows" :key="r.id" class="lc-card" @click="openDetail(r)">
                  <div class="lc-card-t">{{ r.refName || `#${r.bizId}` }}</div>
                  <div class="lc-card-m">
                    <a-tag>{{ dictLabel('lifecycle_type', r.lifecycleType) }}</a-tag>
                    <span class="ct-panel-meta">{{ dictLabel('biz_domain', r.bizDomain) }}</span>
                  </div>
                  <div class="lc-card-m ct-panel-meta">
                    申请 {{ fmtDateTime(r.appliedAt) }}
                    <template v-if="r.newValidTo">；新有效期至 {{ r.newValidTo }}</template>
                  </div>
                </div>
                <div v-if="col.rows.length === 0" class="ct-empty">空</div>
              </div>
            </div>
          </div>
          <div class="ct-panel-meta" style="margin-top: 8px">
            看板一次取前 {{ BOARD_CAP }} 条做概览（不分页）；需要精确分页请切「列表」。两套状态不可混列——
            台账的「待验真 / 正常 / 临期 / 已注销」属 cert_status，与流程状态是不同的状态机（见契约 Q14）。
          </div>
        </a-spin>
      </div>
    </div>

    <!-- ========== 时间轴视图：选证照 → 倒序节点（建档 / 变更 / 续期 / 审批 / 生效） ========== -->
    <div v-else-if="viewMode === 'timeline'" class="ct-panel">
      <div class="ct-panel-h">
        <span class="ct-panel-t">证照生命周期时间轴</span>
        <a-select
          v-model:value="timelineCertId"
          :options="timelineCertOptions"
          show-search
          option-filter-prop="label"
          :filter-option="false"
          placeholder="按证照名称/编号检索选择"
          style="width: 320px"
          allow-clear
          @search="searchTimelineCerts"
          @change="loadTimeline"
        />
      </div>
      <div class="ct-panel-b">
        <a-spin :spinning="timelineLoading">
          <ul v-if="timelineNodes.length" class="ct-tl">
            <li v-for="node in timelineNodes" :key="node.groupId">
              <div class="tl-t">
                <a-tag>{{ dictLabel('lifecycle_type', node.head.lifecycleType) }}</a-tag>
                <a-tag :color="dictTag('lifecycle_status', node.head.status).color">
                  {{ dictTag('lifecycle_status', node.head.status).label }}
                </a-tag>
                <span class="ct-id">{{ node.head.applyNo }}</span>
                <a-tag v-if="node.members.length > 1" color="blue">{{ node.members.length }} 项变更</a-tag>
              </div>
              <div class="tl-m">
                <div v-for="m in node.members" :key="m.id">
                  <span class="tl-k">变更：</span>
                  {{ m.fieldName || m.changeSummary || '—' }}
                  <template v-if="m.fieldName">
                    ：<span class="tl-k">{{ m.oldValue || '空' }}</span> → <b>{{ m.newValue || '空' }}</b>
                  </template>
                </div>
                <div>
                  <span class="tl-k">申请：</span>{{ fmtDateTime(node.head.appliedAt) }}
                  <template v-if="node.head.handledAt">；处理：{{ fmtDateTime(node.head.handledAt) }}</template>
                  <template v-if="node.head.effectiveAt">；生效：{{ fmtDateTime(node.head.effectiveAt) }}</template>
                </div>
                <div v-if="node.head.remark"><span class="tl-k">备注：</span>{{ node.head.remark }}</div>
              </div>
              <div class="tl-ops">
                <a v-if="evidenceRows(node.head).length" @click="openEvidenceList(node.head)">
                  查看附件（{{ evidenceRows(node.head).length }}）
                </a>
                <a @click="openDetail(node.head)">查看报告</a>
                <a @click="openDetail(node.head)">查看审批详情</a>
                <a v-if="can('bidding:certificate:lifecycle:urge')" @click="doUrge(node.head)">催办</a>
              </div>
            </li>
          </ul>
          <div v-else class="ct-empty">
            {{ timelineCertId ? '该证照暂无生命周期记录' : '先选择一张证照，再看它的建档 / 变更 / 续期 / 审批轨迹' }}
          </div>
        </a-spin>
      </div>
    </div>

    <!-- ========== 列表视图（既有台账，保留不动） ========== -->
    <div v-else class="ct-panel">
      <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
        <AttrFilter v-model="query.bizDomain" label="对象域" :options="domainFilters" />
        <AttrFilter v-model="query.lifecycleType" label="流程类型" :options="typeFilters" />
        <AttrFilter v-model="query.status" label="流程状态" :options="statusFilters" />
        <AttrFilter label="申请单号（精确）"><a-input v-model:value="query.applyNo" placeholder="申请单号（精确）" style="width: 200px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="作用对象">
          <a-select
            v-model:value="query.bizId"
            :options="certOptions"
            show-search
            option-filter-prop="label"
            :filter-option="false"
            placeholder="按证照名称/编号检索选择"
            style="width: 240px"
            allow-clear
            @search="searchCertObjects"
            @change="onPickCertObject"
          />
        </AttrFilter>
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
            <div class="ct-empty">暂无流程记录：先发起「变更 / 续期 / 注销吊销 / 年检」申请，审批通过并生效后这里才会显示流转</div>
          </template>
        </a-table>
      </div>
    </div>

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

    <!-- 佐证材料预览：url 优先（上传回执缓存命中）；未命中时按 fileId 走业务出口 /bidding/file/download 兜底 -->
    <PdfPreview
      v-model:open="evPreview.open"
      :url="evPreview.url"
      :file-id="evPreview.fileId"
      :file-name="evPreview.name"
      :title="evPreview.name || '佐证材料预览'"
    />
  </div>
</template>

<style scoped>
/* ===== 佐证材料附件行（详情抽屉内：文件名 + 类别 + 上传时间 + 预览/下载） ===== */
.lc-ev-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
}

.lc-ev-name {
  font-weight: 600;
}

.lc-ev-tag {
  margin-inline-end: 0;
}

.lc-ev-time {
  font-size: 12px;
  color: var(--ind-weak-text, hsl(var(--muted-foreground)));
}

.lc-ev-op {
  cursor: pointer;
}

/* ===== 看板视图：按流程状态分列，列内为证照卡片（与 cert.css 的工业风线条一致） ===== */
.lc-board {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  overflow-x: auto;
}

/* 看板列/卡片颜色全部走主题令牌（2026-09-28）：原先 #fafbfc/#f4f5f7/#fff 在深色下是白块 */
.lc-col {
  flex: 1 0 210px;
  min-width: 210px;
  border: 1.5px solid var(--ind-line, hsl(var(--border)));
  border-radius: 3px;
  background: var(--ind-bone, hsl(var(--muted)));
}

.lc-col-h {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-weight: 700;
  border-bottom: 1.5px solid var(--ind-line, hsl(var(--border)));
  background: var(--ind-bone, hsl(var(--muted)));
}

.lc-col-b {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 80px;
  padding: 8px;
}

.lc-card {
  padding: 8px 10px;
  cursor: pointer;
  background: var(--ind-paper, hsl(var(--card)));
  border: 1px solid var(--ind-line-soft, hsl(var(--border)));
  border-radius: 2px;
  box-shadow: var(--ind-shadow, 0 1px 2px hsl(var(--foreground) / 6%));
}

.lc-card:hover {
  box-shadow: var(--ind-shadow-lg, 0 3px 8px hsl(var(--foreground) / 14%));
}

.lc-card-t {
  font-weight: 700;
  color: var(--ind-ink-text, hsl(var(--foreground)));
}

.lc-card-m {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 4px;
  font-size: 12px;
}

/* ===== 时间轴节点的操作行 ===== */
.tl-ops {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
}
</style>
