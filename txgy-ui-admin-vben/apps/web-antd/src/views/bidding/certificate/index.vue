<script setup lang="ts">
/* 证照台账（契约 §2 全部 + §4 验真 + §5 预警补跑 + §3 文件版本 + §6.6 轨迹入口）
 * 权限：bidding:certificate:{query,create,update,verify,file,alert-run}、bidding:certificate:lifecycle:apply
 * 口径：状态/类型/来源等标签一律取字典；"即将到期"天数取配置 cert.expiring.window.days（不硬编码阈值） */
import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import {
  certErrorMsg,
  type CertificateApi,
  getCertificate,
  getCertificatePage,
  getCertVerifyHistoryPage,
  runCertAlertDispatch,
  runCertAlertScan,
} from '#/api/bidding/certificate';
import { getPersonnelCertPage, type PersonnelCertApi } from '#/api/bidding/personnel';

import { fmtDate, fmtDateTime } from './format';
import CertFiles from './modules/cert-files.vue';
import CertForm from './modules/cert-form.vue';
import CertTimeline from './modules/cert-timeline.vue';
import CertVerify from './modules/cert-verify.vue';
import LifecycleApply from './modules/lifecycle-apply.vue';
import StatusModal from './modules/status-modal.vue';
import { useCertMeta } from './useCertMeta';

import './cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { configNumber, dictItems, dictLabel, dictOptions, dictTag, metaError, metaLoaded, reloadMeta } =
  useCertMeta();

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决），避免界面被锁死 */
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<CertificateApi.PageReq>({
  pageNo: 1,
  pageSize: 10,
  certName: undefined,
  certNo: undefined,
  certType: undefined,
  certStatus: undefined,
  holderName: undefined,
  verificationStatus: undefined,
});

const loading = ref(false);
const rows = ref<CertificateApi.Certificate[]>([]);
const total = ref(0);
const maskNo = ref(true);

const formOpen = ref(false);
const formRecord = ref<CertificateApi.Certificate | null>(null);
const statusOpen = ref(false);
const statusRecord = ref<CertificateApi.Certificate | null>(null);
const applyOpen = ref(false);
const applyRecord = ref<CertificateApi.Certificate | null>(null);

const drawerOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<CertificateApi.Certificate | null>(null);
const tab = ref('basic');

/* ===== 验真历史（读出口） =====
 * 后端 /verify 与 /verify-file 每次都返回"验真历史主键"，但此前**没有接口能把它读回来**
 * （后端 Javadoc 原话："留痕只进不出，主键等于一个再也打不开的句柄"）。本抽屉消费
 * GET /bidding/certificate-verify/history/page：谁在什么时候验了哪张证、结论与渠道是什么，一屏可查。
 * 口径：L3 报文原文不下发（含证照编号等可识别信息，列表是高频读取面），只给 snapshotAvailable，
 *       界面据此提示"可申请调阅"——既不泄露，也不丢可追溯性。 */
const historyOpen = ref(false);
const historyLoading = ref(false);
const historyRows = ref<CertificateApi.VerifyHistoryItem[]>([]);
const historyTotal = ref(0);
/** 从行内进入时带 certId，从工具栏进入时为空（= 本租户全部验真留痕） */
const historyCert = ref<CertificateApi.Certificate | null>(null);
const historyQuery = reactive<CertificateApi.VerifyHistoryPageReq>({
  pageNo: 1,
  pageSize: 10,
  certId: undefined,
  verifyChannel: undefined,
  verifyResult: undefined,
});
const historyChannelOptions = computed(() => dictOptions('verify_channel'));
const historyResultOptions = computed(() => dictOptions('verify_result'));

function openHistory(record?: CertificateApi.Certificate) {
  historyCert.value = record ?? null;
  historyQuery.certId = record?.id;
  historyQuery.verifyChannel = undefined;
  historyQuery.verifyResult = undefined;
  historyQuery.pageNo = 1;
  historyOpen.value = true;
  void loadHistory();
}

function reloadHistory() {
  historyQuery.pageNo = 1;
  void loadHistory();
}

async function loadHistory() {
  historyLoading.value = true;
  try {
    const page = await getCertVerifyHistoryPage({ ...historyQuery });
    historyRows.value = page.list ?? [];
    historyTotal.value = page.total ?? 0;
  } catch (e: unknown) {
    historyRows.value = [];
    historyTotal.value = 0;
    message.error(certErrorMsg(e));
  } finally {
    historyLoading.value = false;
  }
}


const expiringDays = computed(() => configNumber('cert.expiring.window.days', 60));

/* ===== 证照范围：企业证照 / 员工证书 / 全部（用户 2026-09-14："找不到全的，包括企业证照和个人证照"） =====
 * · 企业证照：走 certificate/page（服务端分页，保留原有行操作：详情/编辑/验真/文件/生命周期）；
 * · 员工证书：走 personnel-certificate/page（同样的关键字可传 certName/certNo；**状态字典不同**
 *   —— 企业用 cert_status、员工用 person_cert_status，故员工侧不转发状态筛选，避免把两套码混用）；
 * · 全部：两侧各取前 200 条在前端合并（服务端没有跨域合并接口），**本地分页**并显式提示上限，
 *   只读展示（不提供行操作，行操作仍回到各自的专属页）。
 * 这是"一处看全"的折中实现；真正的跨域合并分页需后端出接口，已记入契约。 */
type UnifiedRow = {
  holder: string;
  key: string;
  name: string;
  source: '企业证照' | '员工证书';
  statusText: string;
  typeText: string;
  validTo: string;
};
const scope = ref<'ALL' | 'ORG' | 'PERSON'>('ORG');
const unifiedRows = ref<UnifiedRow[]>([]);
/** ALL 模式的合并结果（本地分页用） */
const mergedRows = ref<UnifiedRow[]>([]);
const MERGE_CAP = 200;

function orgToUnified(r: CertificateApi.Certificate): UnifiedRow {
  return {
    holder: r.holderName ?? '—',
    key: 'ORG-' + String(r.id),
    name: r.certName ?? '—',
    source: '企业证照',
    statusText: dictLabel('cert_status', r.certStatus) || (r.certStatus ?? '—'),
    typeText: dictLabel('cert_type', r.certType) || (r.certType ?? '—'),
    validTo: r.validTo ? fmtDate(r.validTo) : '长期',
  };
}
function personToUnified(r: PersonnelCertApi.Cert): UnifiedRow {
  return {
    holder: r.personName ?? '—',
    key: 'PER-' + String(r.id),
    name: r.certName ?? '—',
    source: '员工证书',
    statusText: dictLabel('person_cert_status', r.certStatus) || (r.certStatus ?? '—'),
    typeText: dictLabel('person_cert_type', r.certType) || (r.certType ?? '—'),
    validTo: r.validTo ? fmtDate(r.validTo) : '长期',
  };
}
const unifiedColumns = computed(() => {
  const base = [
    { dataIndex: 'name', key: 'name', title: '证照 / 证书', width: 260 },
    { dataIndex: 'typeText', key: 'type', title: '类型', width: 140 },
    { dataIndex: 'holder', key: 'holder', title: '持证主体 / 员工', width: 180 },
    { dataIndex: 'validTo', key: 'validTo', title: '有效期至', width: 130 },
    { dataIndex: 'statusText', key: 'status', title: '状态', width: 130 },
  ];
  return scope.value === 'ALL'
    ? [...base, { dataIndex: 'source', key: 'source', title: '来源', width: 110 }]
    : base;
});
/** 切范围：回第 1 页重查 */
function onScopeChange(): void {
  query.pageNo = 1;
  void load();
}

async function load() {
  loading.value = true;
  try {
    if (scope.value === 'ORG') {
      const page = await getCertificatePage({ ...query });
      rows.value = page.list ?? [];
      total.value = page.total ?? 0;
      return;
    }
    if (scope.value === 'PERSON') {
      const page = await getPersonnelCertPage({
        certName: query.certName,
        certNo: query.certNo,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      });
      unifiedRows.value = (page.list ?? []).map(personToUnified);
      total.value = page.total ?? 0;
      return;
    }
    // ALL：两侧各取前 200 条合并，本地分页（上限显式提示，不假装是全量）
    const [org, per] = await Promise.all([
      getCertificatePage({ ...query, pageNo: 1, pageSize: MERGE_CAP }),
      getPersonnelCertPage({ certName: query.certName, certNo: query.certNo, pageNo: 1, pageSize: MERGE_CAP }),
    ]);
    mergedRows.value = [...(org.list ?? []).map(orgToUnified), ...(per.list ?? []).map(personToUnified)].sort((a, b) =>
      a.validTo.localeCompare(b.validTo),
    );
    total.value = mergedRows.value.length;
    const pageNo = query.pageNo ?? 1;
    const pageSize = query.pageSize ?? MERGE_CAP;
    const start = (pageNo - 1) * pageSize;
    unifiedRows.value = mergedRows.value.slice(start, start + pageSize);
  } catch {
    rows.value = [];
    unifiedRows.value = [];
    mergedRows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 筛选变化 → 回第 1 页重查（前台样式的文字选项点一下即筛） */
function applyFilter(): void {
  query.pageNo = 1;
  void load();
}

function resetQuery() {
  query.certName = undefined;
  query.certNo = undefined;
  query.certType = undefined;
  query.certStatus = undefined;
  query.holderName = undefined;
  query.verificationStatus = undefined;
  query.pageNo = 1;
  void load();
}

onMounted(load);

/* ===== 展示派生 =====
 * 契约 §2.1④：前端**不得**拿 validTo 与本地时间比较派生"临期/已过期"——
 * 该结论由后端按 cert.expiring.window.days / cert.alert.legal.floor.days 计算后写入 certStatus，
 * 前端只做"字典 → 展示"的映射（本文件下方的 KPI 亦按字典 tagType 归集）。 */
function validityText(r: CertificateApi.Certificate): string {
  if (!r.validFrom && !r.validTo) return '—';
  return `${fmtDate(r.validFrom)} ~ ${r.validTo ? fmtDate(r.validTo) : '长期'}`;
}
/** 长期有效：仅依据后端字段是否为空，不做日期推算 */
function isLongTerm(r: CertificateApi.Certificate): boolean {
  return !r.validTo;
}
function maskCertNo(no?: string): string {
  if (!no) return '—';
  if (!maskNo.value || no.length <= 8) return no;
  return `${no.slice(0, 4)}${'*'.repeat(Math.min(6, no.length - 8))}${no.slice(-4)}`;
}

/** 本页按字典 tagType 归集（success=正常 / warning=需关注 / error=异常），不自行判定业务口径 */
const kpi = computed(() => {
  const list = rows.value;
  const typeOf = (code?: null | string) =>
    dictItems('cert_status').find((i) => i.value === code)?.tagType ?? '';
  let ok = 0;
  let warn = 0;
  let bad = 0;
  for (const r of list) {
    const t = typeOf(r.certStatus);
    if (t === 'success') ok += 1;
    else if (t === 'warning') warn += 1;
    else if (t === 'error') bad += 1;
  }
  const unverified = list.filter((r) => !r.lastVerifiedAt).length;
  const longTerm = list.filter((r) => isLongTerm(r)).length;
  return { bad, longTerm, ok, unverified, warn };
});

const columns = [
  { title: '证照', dataIndex: 'certName', key: 'certName', width: 260 },
  { title: '类型', dataIndex: 'certType', key: 'certType', width: 130 },
  { title: '持证主体', dataIndex: 'holderName', key: 'holderName', width: 180 },
  { title: '有效期', dataIndex: 'validTo', key: 'validity', width: 240 },
  { title: '状态', dataIndex: 'certStatus', key: 'certStatus', width: 120 },
  { title: '验真', dataIndex: 'verificationStatus', key: 'verificationStatus', width: 150 },
  { title: '来源', dataIndex: 'sourceChannel', key: 'sourceChannel', width: 100 },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 165 },
  /* 时间列统一在 bodyCell 用 fmtDateTime 渲染（后端 LocalDateTime = epoch 毫秒） */
  { title: '操作', key: 'action', width: 230, fixed: 'right' },
];

/* ===== 行为 ===== */
function onCreate() {
  formRecord.value = null;
  formOpen.value = true;
}
function onEdit(r: CertificateApi.Certificate) {
  formRecord.value = r;
  formOpen.value = true;
}
function onStatus(r: CertificateApi.Certificate) {
  statusRecord.value = r;
  statusOpen.value = true;
}
function onApply(r: CertificateApi.Certificate) {
  applyRecord.value = r;
  applyOpen.value = true;
}

async function openDetail(r: CertificateApi.Certificate) {
  drawerOpen.value = true;
  tab.value = 'basic';
  detail.value = r;
  detailLoading.value = true;
  try {
    detail.value = await getCertificate(r.id);
  } catch {
    /* 用列表行兜底展示 */
  } finally {
    detailLoading.value = false;
  }
}
async function refreshDetail() {
  if (!detail.value?.id) return;
  try {
    detail.value = await getCertificate(detail.value.id);
  } catch {
    /* 忽略 */
  }
  void load();
}

function confirmScan() {
  Modal.confirm({
    title: '手动触发到期预警扫描',
    content: '平时由定时任务执行；此处用于补跑（同日幂等，不会产生重复预警）。',
    okText: '开始扫描',
    cancelText: '取消',
    async onOk() {
      try {
        const n = await runCertAlertScan();
        message.success(`扫描完成，本次生成预警 ${n} 条`);
      } catch (error: unknown) {
        message.warning(certErrorMsg(error));
      }
    },
  });
}
function confirmDispatch() {
  Modal.confirm({
    title: '手动派发通知发件箱',
    content: '把 PENDING/FAILED 到点的通知立刻投递（已发送记录不会重复发送）。',
    okText: '立即派发',
    cancelText: '取消',
    async onOk() {
      try {
        const n = await runCertAlertDispatch();
        message.success(`派发完成，共 ${n} 条`);
      } catch (error: unknown) {
        message.warning(certErrorMsg(error));
      }
    },
  });
}

function onSaved(id?: number) {
  void load();
  if (id && drawerOpen.value && detail.value?.id === id) void refreshDetail();
}

const typeFilters = computed(() => dictOptions('cert_type'));
const statusFilters = computed(() => dictOptions('cert_status'));
const verifyFilters = computed(() => dictOptions('verification_status'));
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">证照台账</h2>
        <p class="ct-sub">
          企业名下全部证照的统一台账：是否有效、是否即将到期、是否已验真一目了然；建档后可继续走「文件版本 →
          验真 → 变更/续期/注销年检」全生命周期。枚举与阈值均来自后台字典/配置，调整后无需前端发版。
        </p>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:certificate:create')" type="primary" @click="onCreate">证照建档</a-button>
        <a-button v-if="can('bidding:certificate:alert-run')" @click="confirmScan">预警扫描</a-button>
        <a-button v-if="can('bidding:certificate:alert-run')" @click="confirmDispatch">派发通知</a-button>
        <a-button v-if="can('bidding:certificate:query')" @click="openHistory()">验真历史</a-button>
        <a-button @click="load">刷新</a-button>
        <a-button v-if="!metaLoaded" type="link" @click="reloadMeta">重载字典/配置</a-button>
      </div>
    </div>

    <a-alert
      v-if="metaError"
      type="warning"
      show-icon
      class="mb-3"
      message="字典/配置未加载，页面按 code 降级展示（功能不受影响）"
      :description="`${metaError}｜/bidding/meta/* 需电子证照域任一页面权限（后端 hasAnyPermissions：certificate:query / qualification:query / personnel:profile:query / certificate:lifecycle:query / qualification:permit:query）；补齐后点右上「重载字典/配置」即可恢复中文名与阈值。`"
    />

    <div class="ct-kpis">
      <div class="ct-kpi k-blue">
        <div class="k-label">台账总数</div>
        <div class="k-num">{{ total }}</div>
        <div class="k-note">按当前筛选条件</div>
      </div>
      <div class="ct-kpi k-green">
        <div class="k-label">本页正常</div>
        <div class="k-num">{{ kpi.ok }}</div>
        <div class="k-note">状态字典 tagType=success</div>
      </div>
      <div class="ct-kpi k-orange">
        <div class="k-label">本页需关注</div>
        <div class="k-num">{{ kpi.warn }}</div>
        <div class="k-note">临期等，由后端按 {{ expiringDays }} 天窗口判定</div>
      </div>
      <div class="ct-kpi k-red">
        <div class="k-label">本页异常</div>
        <div class="k-num">{{ kpi.bad }}</div>
        <div class="k-note">过期/吊销/注销等（字典 tagType=error）</div>
      </div>
      <div class="ct-kpi">
        <div class="k-label">本页长期有效</div>
        <div class="k-num">{{ kpi.longTerm }}</div>
        <div class="k-note">无有效期至，不入到期扫描</div>
      </div>
      <div class="ct-kpi">
        <div class="k-label">本页未验真</div>
        <div class="k-num">{{ kpi.unverified }}</div>
        <div class="k-note">无最近验真时间</div>
      </div>
    </div>

    <div class="ct-panel">
      <!-- 筛选：**前台样式**（左侧属性名 + 右侧文字选项）；枚举项点一下即筛，文本框回车或点查询 -->
      <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
        <AttrFilter
          v-model="scope"
          label="证照范围"
          :allow-all="false"
          :options="[
            { label: '企业证照', value: 'ORG' },
            { label: '员工证书', value: 'PERSON' },
            { label: '全部（企业+个人）', value: 'ALL' },
          ]"
          @change="onScopeChange"
        />
        <AttrFilter label="证照名称">
          <a-input v-model:value="query.certName" placeholder="模糊匹配，回车查询" style="width: 240px" allow-clear @press-enter="applyFilter" />
        </AttrFilter>
        <AttrFilter label="证书编号">
          <a-input v-model:value="query.certNo" placeholder="精确匹配，回车查询" style="width: 240px" allow-clear @press-enter="applyFilter" />
        </AttrFilter>
        <AttrFilter v-if="typeFilters.length > 0" v-model="query.certType" label="证照大类" :options="typeFilters" @change="applyFilter" />
        <AttrFilter v-else label="证照大类">
          <a-input v-model:value="query.certType" placeholder="大类 code（字典未下发时手填）" style="width: 240px" allow-clear @press-enter="applyFilter" />
        </AttrFilter>
        <AttrFilter v-model="query.certStatus" label="台账状态" :options="statusFilters" @change="applyFilter" />
        <AttrFilter label="持证主体">
          <a-input v-model:value="query.holderName" placeholder="模糊匹配，回车查询" style="width: 240px" allow-clear @press-enter="applyFilter" />
        </AttrFilter>
        <AttrFilter v-model="query.verificationStatus" label="验真状态" :options="verifyFilters" @change="applyFilter" />
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <a-button type="primary" @click="applyFilter">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
          <span class="ml-auto text-xs text-muted-foreground">编号掩码 <a-switch v-model:checked="maskNo" size="small" /></span>
        </div>
      </div>

      <div class="ct-panel-b">
        <!-- 非「企业证照」范围：统一只读表（企业+员工证书；行操作仍在各自专属页） -->
        <template v-if="scope !== 'ORG'">
          <div class="mb-2 text-xs text-muted-foreground">
            <template v-if="scope === 'PERSON'">员工证书（按员工档案维度；状态/类型用 person_cert_* 字典）</template>
            <template v-else>合并视图：企业证照 + 员工证书，各取前 {{ MERGE_CAP }} 条后按有效期排序（本地分页，非全量）</template>
          </div>
          <a-table
            class="ct-table"
            :columns="unifiedColumns"
            :data-source="unifiedRows"
            :loading="loading"
            :pagination="{
              current: query.pageNo,
              pageSize: query.pageSize,
              showSizeChanger: true,
              total,
              onChange: (p: number, s: number) => { query.pageNo = p; query.pageSize = s; load(); },
            }"
            row-key="key"
            size="small"
          >
            <template #emptyText><div class="ct-empty">该范围内暂无证照：可调整筛选条件，或点右上「证照建档」录入</div></template>
          </a-table>
        </template>

        <a-table
          v-else
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
          :scroll="{ x: 1600 }"
          @change="
            (p: any) => {
              query.pageNo = p.current;
              query.pageSize = p.pageSize;
              load();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'certName'">
              <div style="font-weight: 700">{{ record.certName }}</div>
              <div class="ct-id">
                {{ maskCertNo(record.certNo) }} · #{{ record.id }}
              </div>
            </template>
            <template v-else-if="column.key === 'certType'">
              {{ dictLabel('cert_type', record.certType) }}
              <div v-if="record.certLevel" class="ct-panel-meta">{{ record.certLevel }}</div>
            </template>
            <template v-else-if="column.key === 'holderName'">
              {{ record.holderName || '—' }}
              <div class="ct-panel-meta">{{ record.holderType ? dictLabel('holder_type', record.holderType) : '' }}</div>
            </template>
            <template v-else-if="column.key === 'validity'">
              <div>{{ validityText(record) }}</div>
              <a-tag v-if="isLongTerm(record)" color="green">长期</a-tag>
              <a-tag v-else :color="dictTag('cert_status', record.certStatus).color">
                {{ dictTag('cert_status', record.certStatus).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'certStatus'">
              <a-tag :color="dictTag('cert_status', record.certStatus).color">
                {{ dictTag('cert_status', record.certStatus).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'verificationStatus'">
              <a-tag :color="dictTag('verification_status', record.verificationStatus).color">
                {{ dictTag('verification_status', record.verificationStatus).label }}
              </a-tag>
              <div class="ct-panel-meta">
                {{ record.lastVerifiedAt ? fmtDateTime(record.lastVerifiedAt) : '未验真' }}
              </div>
            </template>
            <template v-else-if="column.key === 'updateTime'">
              {{ fmtDateTime(record.updateTime) }}
            </template>
            <template v-else-if="column.key === 'sourceChannel'">
              {{ record.sourceChannel ? dictLabel('source_channel', record.sourceChannel) : '—' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="openDetail(record)">详情</a>
                <a v-if="can('bidding:certificate:update')" @click="onEdit(record)">编辑</a>
                <a v-if="can('bidding:certificate:update')" @click="onStatus(record)">状态</a>
                <a v-if="can('bidding:certificate:lifecycle:apply')" @click="onApply(record)">流程</a>
                <a v-if="can('bidding:certificate:query')" @click="openHistory(record)">验真历史</a>
              </a-space>
            </template>
          </template>
          <template #emptyText>
            <div class="ct-empty">
              暂无证照数据<span v-if="!metaLoaded">（字典/配置尚未加载成功，可点右上角重载）</span>
            </div>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 详情抽屉：基本信息 / 文件版本 / 验真 / 变更历史 -->
    <a-drawer
      v-model:open="drawerOpen"
      class="bid-ind"
      :width="940"
      :title="detail ? `证照详情 · ${detail.certName}` : '证照详情'"
      destroy-on-close
    >
      <a-spin :spinning="detailLoading">
        <a-tabs v-model:active-key="tab">
          <a-tab-pane key="basic" tab="基本信息">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="证照 ID">
                <span class="ct-id">#{{ detail?.id }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="乐观锁版本">v{{ detail?.version ?? '—' }}</a-descriptions-item>
              <a-descriptions-item label="证照名称">{{ detail?.certName || '—' }}</a-descriptions-item>
              <a-descriptions-item label="证书编号">
                {{ maskNo ? maskCertNo(detail?.certNo) : detail?.certNo || '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="证照大类">{{ dictLabel('cert_type', detail?.certType) }}</a-descriptions-item>
              <a-descriptions-item label="类型代码">{{ detail?.certCategoryCode || '—' }}</a-descriptions-item>
              <a-descriptions-item label="等级">{{ detail?.certLevel || '—' }}</a-descriptions-item>
              <a-descriptions-item label="台账状态">
                <a-tag :color="dictTag('cert_status', detail?.certStatus).color">
                  {{ dictTag('cert_status', detail?.certStatus).label }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="持证主体">{{ detail?.holderName || '—' }}</a-descriptions-item>
              <a-descriptions-item label="主体类别">
                {{ detail?.holderType ? dictLabel('holder_type', detail.holderType) : '—' }}
                <span v-if="detail?.holderId" class="ct-id"> · 档案 #{{ detail.holderId }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="颁发机构">{{ detail?.issuingAuthority || '—' }}</a-descriptions-item>
              <a-descriptions-item label="机构代码">{{ detail?.issuingAuthorityCode || '—' }}</a-descriptions-item>
              <a-descriptions-item label="颁发日期">{{ detail?.issueDate || '—' }}</a-descriptions-item>
              <a-descriptions-item label="有效期">
                {{ validityText(detail ?? ({} as CertificateApi.Certificate)) }}
                <a-tag v-if="detail" :color="dictTag('cert_status', detail.certStatus).color">
                  {{ dictTag('cert_status', detail.certStatus).label }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="电子证照标识">{{ detail?.eCertIdent || '—' }}</a-descriptions-item>
              <a-descriptions-item label="数据来源">
                {{ detail?.sourceChannel ? dictLabel('source_channel', detail.sourceChannel) : '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="许可/业务范围" :span="2">{{ detail?.certScope || '—' }}</a-descriptions-item>
              <a-descriptions-item label="备注" :span="2">{{ detail?.remark || '—' }}</a-descriptions-item>
              <a-descriptions-item label="建档时间">{{ fmtDateTime(detail?.createTime) }}</a-descriptions-item>
              <a-descriptions-item label="更新时间">{{ fmtDateTime(detail?.updateTime) }}</a-descriptions-item>
            </a-descriptions>
            <div class="mt-3 flex flex-wrap gap-2">
              <a-button
                v-if="can('bidding:certificate:update')"
                size="small"
                @click="detail && onEdit(detail)"
              >
                编辑证照
              </a-button>
              <a-button
                v-if="can('bidding:certificate:update')"
                size="small"
                @click="detail && onStatus(detail)"
              >
                状态流转
              </a-button>
              <a-button
                v-if="can('bidding:certificate:lifecycle:apply')"
                size="small"
                type="primary"
                @click="detail && onApply(detail)"
              >
                发起变更/续期/注销/年检
              </a-button>
            </div>
          </a-tab-pane>
          <a-tab-pane key="files" tab="文件版本">
            <CertFiles :cert-id="detail?.id ?? null" :can-write="can('bidding:certificate:file')" />
          </a-tab-pane>
          <a-tab-pane key="verify" tab="验真">
            <CertVerify :cert="detail" :can-verify="can('bidding:certificate:verify')" @changed="refreshDetail" />
          </a-tab-pane>
          <a-tab-pane key="timeline" tab="变更历史">
            <CertTimeline biz-domain="CERT" :biz-id="detail?.id ?? null" />
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-drawer>

    <!-- 验真历史（读出口）：谁在何时验了哪张证、渠道与结论是什么。
         后端明确 L3 报文原文不下发（含证照编号等可识别信息、列表是高频读取面），故只给 snapshotAvailable，
         界面提示"可申请调阅"——既不泄露，也不丢可追溯性。 -->
    <a-drawer
      v-model:open="historyOpen"
      class="bid-ind"
      :width="920"
      :title="historyCert ? `验真历史 · ${historyCert.certName}` : '验真历史 · 本租户全部留痕'"
    >
      <a-space class="mb-3" wrap>
        <AttrFilter
          v-model="historyQuery.verifyChannel"
          label="验真渠道"
          :options="historyChannelOptions"
          @change="reloadHistory"
        />
        <AttrFilter
          v-model="historyQuery.verifyResult"
          label="验真结论"
          :options="historyResultOptions"
          @change="reloadHistory"
        />
        <a-button @click="loadHistory">刷新</a-button>
        <span class="ct-panel-meta">共 {{ historyTotal }} 条留痕</span>
      </a-space>
      <a-spin :spinning="historyLoading">
        <a-table
          class="ct-table"
          :columns="[
            { title: '证照', dataIndex: 'certName', key: 'certName' },
            { title: '渠道', dataIndex: 'verifyChannel', key: 'verifyChannel', width: 130 },
            { title: '结论', dataIndex: 'verifyResult', key: 'verifyResult', width: 140 },
            { title: '发起人', dataIndex: 'verifyBy', key: 'verifyBy', width: 100 },
            { title: '完成时间', dataIndex: 'verifiedAt', key: 'verifiedAt', width: 170 },
            { title: '报文留痕', dataIndex: 'snapshotAvailable', key: 'snapshotAvailable', width: 130 },
          ]"
          :data-source="historyRows"
          :pagination="{
            current: historyQuery.pageNo,
            pageSize: historyQuery.pageSize,
            total: historyTotal,
            showSizeChanger: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          row-key="id"
          size="small"
          @change="
            (p: any) => {
              historyQuery.pageNo = p.current;
              historyQuery.pageSize = p.pageSize;
              loadHistory();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'verifyChannel'">
              {{ dictLabel('verify_channel', record.verifyChannel) }}
            </template>
            <template v-else-if="column.key === 'verifyResult'">
              <a-tag :color="dictTag('verify_result', record.verifyResult).color">
                {{ dictTag('verify_result', record.verifyResult).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'verifiedAt'">{{ fmtDateTime(record.verifiedAt) }}</template>
            <template v-else-if="column.key === 'snapshotAvailable'">
              <a-tooltip
                :title="
                  record.snapshotAvailable
                    ? 'L3 报文原文（含证照编号等可识别信息）不下发；需举证/审计时按验真历史主键查库调阅'
                    : '本次未留报文原文'
                "
              >
                <a-tag :color="record.snapshotAvailable ? 'blue' : 'default'">
                  {{ record.snapshotAvailable ? '可申请调阅' : '无原文' }}
                </a-tag>
              </a-tooltip>
            </template>
          </template>
          <template #emptyText>
            <div class="ct-empty">暂无验真留痕（发起验真后这里会逐条记录渠道与结论）</div>
          </template>
        </a-table>
      </a-spin>
    </a-drawer>

    <CertForm v-model:open="formOpen" :record="formRecord" @saved="onSaved" />
    <StatusModal v-model:open="statusOpen" :cert="statusRecord" @done="onSaved(statusRecord?.id)" />
    <LifecycleApply v-model:open="applyOpen" :cert="applyRecord" @applied="onSaved" />
  </div>
</template>
