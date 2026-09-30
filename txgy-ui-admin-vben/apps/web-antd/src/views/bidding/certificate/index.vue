<script setup lang="ts">
/* 证照台账（契约 §2 全部 + §4 验真 + §5 预警补跑 + §3 文件版本 + §6.6 轨迹入口）
 * 权限：bidding:certificate:{query,create,update,verify,file,alert-run}、bidding:certificate:lifecycle:apply
 * 口径：状态/类型/来源等标签一律取字典；"即将到期"天数取配置 cert.expiring.window.days（不硬编码阈值） */
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore, useUserStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import {
  batchVerifyCertificates,
  certErrorMsg,
  type CertificateApi,

  getCertAlertPage,
  getCertificatePage,
  getCertVerifyHistoryPage,
  importCertificates,
  runCertAlertDispatch,
  runCertAlertScan,
} from '#/api/bidding/certificate';
import { uploadBiddingFile } from '#/api/bidding/file';
import { getPersonnelCertPage, type PersonnelCertApi } from '#/api/bidding/personnel';

import AttrFilter from '../components/AttrFilter.vue';
import { dotColor, isLongTerm, maskCertNo, validityText } from './certDisplay';
import { fmtDate, fmtDateTime } from './format';
// 生命周期（原独立页 60005400）：按三台账 IA 并入本页「流程」页签，实现只有一份
import LifecyclePanel from './lifecycle/index.vue';
import CertForm from './modules/cert-form.vue';
import LifecycleApply from './modules/lifecycle-apply.vue';
import StatusModal from './modules/status-modal.vue';
import { useCertMeta } from './useCertMeta';

import './cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
/** 当前登录人（页头「管理员」位）。注：**超管判定不要用权限码 `*:*:*`**（本部署恒 false），
 *  需要平台视角时用 `userStore.userRoles.includes('super_admin')`。此处仅作身份展示。 */
const currentUserName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员',
);
const { configNumber, dictItems, dictLabel, dictOptions, dictTag, metaError, metaLoaded, reloadMeta } =
  useCertMeta();

/** 页签（2026-09-28）：把原独立页「证件全生命周期」收进本页「流程」页签；
 *  默认停在台账页签——用户进这一页首先是要看证照列表，而不是流程看板。 */
const activeTab = ref('ledger');

/* ===== 表格勾选（批量操作前置） =====
 * ⚠️ 选中态**必须按表格 rowKey 的原始类型保存**（本页 rowKey=id，数字）。若在这里 String()，
 * 回填给 antd 时字符串与数字不相等 → 勾选框不显示。转字符串只在**提交给接口**时做。 */
const selectedRowKeys = ref<Array<number | string>>([]);
const rowSelection = computed(() => ({
  onChange: (keys: (number | string)[]) => {
    selectedRowKeys.value = keys;
  },
  selectedRowKeys: selectedRowKeys.value,
}));

/* ===== 批量验真（契约：POST /bidding/certificate-verify/verify-batch） =====
 * **不做**"前端循环调单条"：会真实触发 N 次验真、无事务、无进度、失败难回滚，且可能撞上游验真渠道频控。
 * 接口未就绪时由请求层提示错误，**勾选不丢**，后端上线后可直接重试。 */
const batchVerifying = ref(false);
async function doBatchVerify(): Promise<void> {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先勾选要验真的证照');
    return;
  }
  batchVerifying.value = true;
  try {
    // 提交时才转字符串（雪花 ID 精度），选中态本身保持表格原始类型
    const res = await batchVerifyCertificates({ certIds: selectedRowKeys.value.map((k) => String(k)) });
    const items = res?.items ?? [];
    const failed = items.filter((i) => i.accepted === false);
    if (failed.length > 0) {
      const names = failed
        .slice(0, 3)
        .map((f) => f.certName || f.certId || '')
        .filter(Boolean)
        .join('、');
      message.warning(
        `已受理 ${items.length - failed.length} 条，失败 ${failed.length} 条${names ? `（${names}${failed.length > 3 ? ' 等' : ''}）` : ''}`,
      );
    } else {
      message.success(`已提交 ${items.length || selectedRowKeys.value.length} 条验真，结论见「验真历史」`);
    }
    selectedRowKeys.value = [];
    void load();
    void loadStats();
  } catch {
    // 失败（含后端未实现）：错误由请求层提示，勾选保留
  } finally {
    batchVerifying.value = false;
  }
}

/* ===== 批量导入（契约：先 /bidding/file/upload 取 fileId，再 POST /bidding/certificate/import） ===== */
const importing = ref(false);
async function onPickImportFile(file: File): Promise<boolean> {
  importing.value = true;
  try {
    const up = await uploadBiddingFile(file, 'bidding/certificate');
    if (!up?.fileId) {
      message.warning('文件上传失败（未取得文件编号），请重试');
      return false;
    }
    const res = await importCertificates({ fileId: up.fileId });
    const ok = res?.successCount ?? 0;
    const fail = res?.failCount ?? 0;
    if (fail > 0) {
      Modal.info({
        title: `导入完成：成功 ${ok} 行，失败 ${fail} 行`,
        content: () =>
          h(
            'div',
            { style: 'max-height:320px;overflow:auto' },
            (res?.failures ?? [])
              .slice(0, 50)
              .map((f) => h('div', `第 ${f.rowNum ?? '?'} 行：${f.message ?? '未知原因'}`)),
          ),
        okText: '知道了',
      });
    } else {
      message.success(`导入完成，成功 ${ok} 行`);
    }
    void load();
    void loadStats();
  } catch {
    // 模板不符 / 后端未实现等，错误由请求层提示
  } finally {
    importing.value = false;
  }
  return false;
}

/** 高级筛选折叠：把非高频条件（所属企业 / 验真状态 / 有效期区间）收起来，主行只留名称·编号·主体 + 类型 + 状态 */
const advancedOpen = ref(false);

/** 字典由「系统字典」模块维护，本模块只读消费；此按钮仅做跳转，不改字典 */
const DICT_MANAGE_PATH = '/system/dict';
function goDict(): void {
  void router.push(DICT_MANAGE_PATH);
}

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决），避免界面被锁死 */
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<CertificateApi.PageReq>({
  pageNo: 1,
  pageSize: 20,
  certName: undefined,
  certNo: undefined,
  certType: undefined,
  certStatus: undefined,
  holderName: undefined,
  verificationStatus: undefined,
});

// 筛选/分页来自 URL Query（设计约束：进入详情/建档/编辑返回时保留筛选、分页状态）
Object.assign(query, {
  certName: (route.query.certName as string) || undefined,
  certNo: (route.query.certNo as string) || undefined,
  certType: (route.query.certType as string) || undefined,
  certStatus: (route.query.certStatus as string) || undefined,
  holderName: (route.query.holderName as string) || undefined,
  verificationStatus: (route.query.verificationStatus as string) || undefined,
  pageNo: Number(route.query.pageNo) || 1,
  pageSize: Number(route.query.pageSize) || 20,
});
/** 仅保留有值的筛选写入 URL（undefined 由 vue-router 丢弃，返回经 query 恢复） */
function toQuery(q: CertificateApi.PageReq): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(q)) {
    if (v !== undefined && v !== null && v !== '') out[k] = String(v);
  }
  return out;
}
watch(query, () => void router.replace({ query: toQuery(query) }), { deep: true });

const loading = ref(false);
const rows = ref<CertificateApi.Certificate[]>([]);
const total = ref(0);
const maskNo = ref(true);

/* ===== 数据看板（全库口径，不是"本页统计"） =====
 * 原型要求卡片显示全库计数并**点击联动表格筛选**。后端 page 接口支持按 certStatus / verificationStatus
 * 过滤并返回 total，故看板用「pageSize=1 的计数查询」并发取全库真实计数，无需后端新增统计接口。
 * 硬约束：状态码一律从字典取（契约 §0.5），卡片与其筛选**一一对应**——保证"卡片上的数字 = 点击后列表条数"。
 * 三类状态各取该 tagType 下 sort 最小的一项作代表码（正常=success / 临期=warning / 异常=error），
 * 故暂扣(SUSPENDED)、吊销(REVOKED)、注销(CANCELLED)不计入卡片，仍可通过下方「台账状态」筛选查看。 */
type StatKey = 'ALL' | 'BAD' | 'OK' | 'UNVERIFIED' | 'WARN';
const stats = ref<Record<StatKey, null | number>>({ ALL: null, BAD: null, OK: null, UNVERIFIED: null, WARN: null });
const statsLoading = ref(false);
const activeStat = ref<StatKey | undefined>(undefined);

function statusCodeOfTag(tagType: string): string | undefined {
  return dictItems('cert_status').find((i) => i.tagType === tagType)?.value;
}
/** 未验真码：verification_status 中 tagType=default 的一项；字典未下发时回落契约码（与 CONTRACT_LABELS 同源） */
const unverifiedCode = computed(
  () => dictItems('verification_status').find((i) => i.tagType === 'default')?.value ?? 'UNVERIFIED',
);

async function countCert(params: Partial<CertificateApi.PageReq>): Promise<number> {
  try {
    const page = await getCertificatePage({ pageNo: 1, pageSize: 1, ...params } as CertificateApi.PageReq);
    return page.total ?? 0;
  } catch {
    return 0;
  }
}

/* ===== 页头操作收敛（2026-09-28）：主操作留明面，低频动作走「更多」 ===== */
/** 统一刷新：台账 + 看板 + 统计一次拉齐，避免用户逐个按钮点 */
async function refreshAll(): Promise<void> {
  await load();
  await Promise.all([loadStats()]);
}

/** 行内「更多」分发：与详情抽屉内的按钮共用同一批函数 */
function onRowAction(key: number | string, record: CertificateApi.Certificate): void {
  switch (key) {
    case 'apply': {
      onApply(record);
      break;
    }
    case 'edit': {
      onEdit(record);
      break;
    }
    case 'history': {
      openHistory(record);
      break;
    }
    case 'status': {
      onStatus(record);
      break;
    }
    default: {
      break;
    }
  }
}

/** 「更多」菜单分发：与按钮共用同一批函数，不复制逻辑 */
function onMoreAction(info: { key: number | string }): void {
  switch (info.key) {
    case 'alertList': {
      openAlerts();
      break;
    }
    case 'alertScan': {
      confirmScan();
      break;
    }
    case 'batchVerify': {
      void doBatchVerify();
      break;
    }
    case 'dict': {
      goDict();
      break;
    }
    case 'dispatch': {
      confirmDispatch();
      break;
    }
    case 'reloadMeta': {
      void onReloadMeta();
      break;
    }
    case 'verifyHistory': {
      openHistory();
      break;
    }
    default: {
      break;
    }
  }
}

async function loadStats(): Promise<void> {
  if (!metaLoaded.value) return; // 字典未就绪无法按 tagType 分桶：看板显示「—」而非猜测
  statsLoading.value = true;
  const okCode = statusCodeOfTag('success');
  const warnCode = statusCodeOfTag('warning');
  const badCode = statusCodeOfTag('error');
  const [all, ok, warn, bad, unverified] = await Promise.all([
    countCert({}),
    okCode ? countCert({ certStatus: okCode }) : Promise.resolve(null),
    warnCode ? countCert({ certStatus: warnCode }) : Promise.resolve(null),
    badCode ? countCert({ certStatus: badCode }) : Promise.resolve(null),
    countCert({ verificationStatus: unverifiedCode.value }),
  ]);
  stats.value = { ALL: all, BAD: bad, OK: ok, UNVERIFIED: unverified, WARN: warn };
  statsLoading.value = false;
}

const formOpen = ref(false);
const formRecord = ref<CertificateApi.Certificate | null>(null);
const statusOpen = ref(false);
const statusRecord = ref<CertificateApi.Certificate | null>(null);
const applyOpen = ref(false);
const applyRecord = ref<CertificateApi.Certificate | null>(null);
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


/* ===== 证照到期预警清单（后端变更通知-给前端.md §7①：只读展示，处理入口待后端开放） ===== */
const alertOpen = ref(false);
const alertLoading = ref(false);
const alertRows = ref<CertificateApi.CertAlert[]>([]);
const alertTotal = ref(0);
const alertQuery = reactive<CertificateApi.CertAlertPageReq>({
  pageNo: 1,
  pageSize: 10,
  alertType: undefined,
  alertLevel: undefined,
  handled: undefined,
});
/* 字典优先 + 静态兜底（字典 403 缺口按契约 12 降级，回显原 code） */
const alertTypeOptions = computed(() => dictOptions('alert_type'));
const alertLevelOptions = computed(() => dictOptions('alert_level'));
function alertLevelColor(lvl?: string): string {
  return lvl === 'CRITICAL' ? 'red' : lvl === 'WARNING' ? 'orange' : 'blue';
}
const alertColumns = [
  { title: '预警内容', dataIndex: 'alertMessage', key: 'alertMessage', ellipsis: true },
  { title: '类型', dataIndex: 'alertType', key: 'alertType', width: 100 },
  { title: '档位', dataIndex: 'alertLevel', key: 'alertLevel', width: 90 },
  { title: '触发时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 165 },
  { title: '状态', dataIndex: 'handled', key: 'handled', width: 90 },
  { title: '处理人', dataIndex: 'handledBy', key: 'handledBy', width: 100 },
  { title: '处理备注', dataIndex: 'handleNote', key: 'handleNote', ellipsis: true },
];
function openAlerts(): void {
  alertQuery.pageNo = 1;
  alertQuery.alertType = undefined;
  alertQuery.alertLevel = undefined;
  alertQuery.handled = undefined;
  alertOpen.value = true;
  void loadAlerts();
}
function reloadAlerts(): void {
  alertQuery.pageNo = 1;
  void loadAlerts();
}
async function loadAlerts(): Promise<void> {
  alertLoading.value = true;
  try {
    const res = await getCertAlertPage({ ...alertQuery });
    alertRows.value = res.list ?? [];
    alertTotal.value = res.total ?? 0;
  } catch {
    alertRows.value = [];
    alertTotal.value = 0;
  } finally {
    alertLoading.value = false;
  }
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
    { dataIndex: 'name', key: 'name', title: '证照 / 证书', width: 260, ellipsis: true },
    { dataIndex: 'typeText', key: 'type', title: '类型', width: 140 },
    { dataIndex: 'holder', key: 'holder', title: '持证主体 / 员工', width: 180, ellipsis: true },
    { dataIndex: 'validTo', key: 'validTo', title: '有效期至', width: 130 },
    { dataIndex: 'statusText', key: 'status', title: '状态', width: 130 },
  ];
  return scope.value === 'ALL'
    ? [...base, { dataIndex: 'source', key: 'source', title: '来源', width: 110 }]
    : base;
});
/** 点卡片 → 写入对应筛选条件后重查（卡片与列表口径一致） */
function applyStat(k: StatKey): void {
  activeStat.value = k;
  // 卡片计数取自 certificate/page（企业证照），故联动时把范围切回企业证照，避免"点了卡片但表格没变"
  scope.value = 'ORG';
  query.pageNo = 1;
  if (k === 'ALL') {
    query.certStatus = undefined;
    query.verificationStatus = undefined;
  } else if (k === 'UNVERIFIED') {
    query.certStatus = undefined;
    query.verificationStatus = unverifiedCode.value;
  } else {
    query.verificationStatus = undefined;
    query.certStatus =
      k === 'OK' ? statusCodeOfTag('success') : k === 'WARN' ? statusCodeOfTag('warning') : statusCodeOfTag('error');
  }
  void load();
}

/** 卡片「清空」态：用户手动改筛选后不应再高亮旧卡片 */
function clearActiveStat(): void {
  activeStat.value = undefined;
}

/** 切范围：回第 1 页重查（看板卡片只对企业证照口径，切走后取消高亮） */
function onScopeChange(): void {
  clearActiveStat();
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
  clearActiveStat(); // 用户手动改条件后，看板卡片不再保持高亮（避免"卡片选中但条件已变"的错觉）
  query.pageNo = 1;
  void load();
}

function resetQuery() {
  clearActiveStat();
  query.certName = undefined;
  query.certNo = undefined;
  query.certType = undefined;
  query.certStatus = undefined;
  query.holderName = undefined;
  query.verificationStatus = undefined;
  query.companyId = undefined;
  query.validToFrom = undefined;
  query.validToTo = undefined;
  query.pageNo = 1;
  selectedRowKeys.value = [];
  void load();
}

/** 重载字典/配置：字典回来后才能按 tagType 分桶，故统计必须跟着重算 */
async function onReloadMeta(): Promise<void> {
  await reloadMeta();
  await loadStats();
}

onMounted(() => {
  void load();
  void loadStats();
});

/* 字典异步到达：到达后补算看板（首屏 loadStats 通常因字典未就绪而跳过） */
watch(metaLoaded, (ready) => {
  if (ready) void loadStats();
});

/* ===== 展示派生 =====
 * validityText / isLongTerm / maskCertNo / dotColor 已抽到 ./certDisplay —— 详情页（独立页）
 * 与列表页共用一份实现，避免"两处各写一半"的漂移（本仓既有口径：同一能力只留一处实现）。
 * 契约 §2.1④ 口径不变：前端**不得**拿 validTo 与本地时间比较派生"临期/已过期"，
 * 该结论由后端按 cert.expiring.window.days / cert.alert.legal.floor.days 计算后写入 certStatus。 */

const columns = [
  { title: '证照', dataIndex: 'certName', key: 'certName', width: 260, ellipsis: true },
  { title: '类型', dataIndex: 'certType', key: 'certType', width: 130 },
  { title: '持证主体', dataIndex: 'holderName', key: 'holderName', width: 180, ellipsis: true },
  { title: '有效期', dataIndex: 'validTo', key: 'validity', width: 240 },
  { title: '状态', dataIndex: 'certStatus', key: 'certStatus', width: 120 },
  { title: '验真', dataIndex: 'verificationStatus', key: 'verificationStatus', width: 150 },
  { title: '来源', dataIndex: 'sourceChannel', key: 'sourceChannel', width: 100 },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 165 },
  /* 时间列统一在 bodyCell 用 fmtDateTime 渲染（后端 LocalDateTime = epoch 毫秒） */
  { title: '操作', key: 'action', width: 150, fixed: 'right' },
];

/* ===== 行为：详情/编辑/建档走独立路由页（设计约束：非抽屉/非弹窗），返回经 URL Query 保留列表状态 ===== */
function onCreate() {
  void router.push('/bidding/certificate/create');
}
function onEdit(r: CertificateApi.Certificate) {
  if (r.id === undefined) return;
  void router.push(`/bidding/certificate/${r.id}/edit`);
}
function onStatus(r: CertificateApi.Certificate) {
  statusRecord.value = r;
  statusOpen.value = true;
}
function onApply(r: CertificateApi.Certificate) {
  applyRecord.value = r;
  applyOpen.value = true;
}

/** 行内「查看 / 文件」：跳转独立详情页（用户 2026-09-28 口径：查看不再是表格下方的占位块）。
 *  tab=files 时详情页直接落到「文件版本」，让"这张证有没有文件、能不能传"一步可达。 */
function goDetail(r: CertificateApi.Certificate, tab?: 'files') {
  void router.push({
    path: `/bidding/certificate/${r.id}`,
    query: tab ? { tab } : {},
  });
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
    content: '把处于「待投递 / 投递失败」状态的到期通知立即投递（已发送记录不会重复发送）。',
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

function onSaved() {
  void load();
  void loadStats(); // 写操作会改变状态分布，看板必须跟着重算
}

/* ===== 四类归口二级联动（契约《后端变更通知-电子证照域字典治理》§三 + §六-3）=====
 * 口径：大类（cert_category）→ 用映射字典 cert_type_category 过滤出该大类下的类型 → 再选类型（cert_type）。
 * ⚠️ 归属关系**读字典，不硬编码**（大类归属可能被运营调整）。
 * ⚠️ 后端 `CertificatePageReqVO` **没有 certCategory 入参**（其 `certType` 的 Schema 描述误写成"证照大类 code"，
 *    但 example 是 ADMIN_LICENSE = cert_type 的值），所以**大类只做前端联动收窄，不参与服务端筛选**；
 *    真正提交给后端筛选的仍是 `query.certType`。已在契约里向后端提出（见变更通知回执）。 */
const categoryFilters = computed(() => dictOptions('cert_category'));
const typeFilters = computed(() => dictOptions('cert_type'));
const statusFilters = computed(() => dictOptions('cert_status'));
const verifyFilters = computed(() => dictOptions('verification_status'));
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">证照台账</h2>
        <!-- 副标题只留一句定位；口径细则移入右侧「口径」气泡（原三行长文占掉页头一半高度） -->
        <p class="ct-sub">企业名下证照的统一台账 · 有效期 / 状态 / 验真一屏可判，建档后接续全生命周期</p>
      </div>
      <div class="ct-actions">
        <span class="ct-id ct-user">{{ currentUserName }}</span>
        <!-- 页头分层：主操作（新建）+ 刷新 留在明面，其余低频动作进「更多」。
             原先是 11 个按钮平铺并列，没有主次，用户要在一排里找目标。 -->
        <a-button v-if="can('bidding:certificate:create')" type="primary" @click="onCreate">新建证照</a-button>
        <a-button :loading="loading" @click="refreshAll">刷新</a-button>
        <a-dropdown>
          <a-button>
            更多
            <IconifyIcon icon="lucide:chevron-down" class="ml-1 inline-block align-middle" />
          </a-button>
          <template #overlay>
            <a-menu @click="onMoreAction">
              <!-- 导入需要文件选择器：a-upload 包在菜单项外层，保留原生选文件行为 -->
              <a-upload
                v-if="can('bidding:certificate:import')"
                :before-upload="onPickImportFile"
                :disabled="importing"
                :show-upload-list="false"
              >
                <a-menu-item key="import">批量导入{{ importing ? '（上传中…）' : '' }}</a-menu-item>
              </a-upload>
              <a-menu-item
                v-if="can('bidding:certificate:verify')"
                key="batchVerify"
                :disabled="selectedRowKeys.length === 0"
              >
                批量验真{{ selectedRowKeys.length > 0 ? `（已选 ${selectedRowKeys.length}）` : '（先勾选）' }}
              </a-menu-item>
              <a-menu-item v-if="can('bidding:certificate:alert-run')" key="alertScan">预警扫描</a-menu-item>
              <a-menu-item v-if="can('bidding:certificate:query')" key="alertList">预警清单</a-menu-item>
              <a-menu-item v-if="can('bidding:certificate:query')" key="verifyHistory">验真历史</a-menu-item>
              <a-menu-item v-if="can('bidding:certificate:alert-run')" key="dispatch">派发通知</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="dict">配置字典</a-menu-item>
              <a-menu-item v-if="!metaLoaded" key="reloadMeta">重载字典 / 配置</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <!-- 说明文字收进气泡：不占主视觉区 -->
        <a-popover title="台账口径" placement="bottomRight" :overlay-style="{ maxWidth: '520px' }">
          <a-button type="text">
            <IconifyIcon icon="lucide:info" class="mr-1 inline-block align-middle" />
            口径
          </a-button>
          <template #content>
            <div class="cert-note">
              <p>
                企业名下全部证照的统一台账：是否有效、是否即将到期、是否已验真一目了然；
                建档后可继续走「文件版本 → 验真 → 变更/续期/注销年检」全生命周期。
              </p>
              <p>
                枚举与阈值均来自后台字典 / 配置（<code>cert_status</code>、<code>verification_status</code>、
                <code>cert_type</code>、<code>cert_category</code>、<code>file_category</code>、
                <code>verify_channel</code>、<code>source_channel</code>），调整后无需前端发版；
                临期窗口取配置 <code>cert.expiring.window.days</code>，不在前端硬编码天数。
              </p>
            </div>
          </template>
        </a-popover>
      </div>
    </div>

    <!-- ===== 页签（2026-09-28 三台账重组） =====
         原「证件全生命周期」(菜单 60005400) 并入本页「流程」页签。
         归属依据（判据一 主实体原则 + 判据二 就地原则）：生命周期流程的主实体就是**企业证照**；
         用户看完一张证想发起续期时，不该先切到另一页再重新选一次证照。
         说明：页签内保留原有缩进层级未做整体重排，以缩小 diff、避免大规模缩进引入的隐性错误。 -->
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="ledger" tab="证照台账">
    <a-alert
      v-if="metaError"
      type="warning"
      show-icon
      class="mb-3"
      message="字典/配置未加载，页面按 code 降级展示（功能不受影响）"
      :description="`${metaError}｜/bidding/meta/* 需电子证照域任一页面权限（后端 hasAnyPermissions：certificate:query / qualification:query / personnel:profile:query / certificate:lifecycle:query / qualification:permit:query）；补齐后点右上「重载字典/配置」即可恢复中文名与阈值。`"
    />

    <!-- 数据看板：**全库计数**（非"本页统计"），点卡片联动下方表格筛选 -->
    <div class="ct-kpis" :class="{ 'is-loading': statsLoading }">
      <div class="ct-kpi k-blue" :class="{ 'is-active': activeStat === 'ALL' }" @click="applyStat('ALL')">
        <div class="k-label">全部证照</div>
        <div class="k-num">{{ stats.ALL ?? '—' }}</div>
        <div class="k-note">全库企业证照总数</div>
      </div>
      <div class="ct-kpi k-green" :class="{ 'is-active': activeStat === 'OK' }" @click="applyStat('OK')">
        <div class="k-label">正常</div>
        <div class="k-num">{{ stats.OK ?? '—' }}</div>
        <div class="k-note">状态字典 tagType=success</div>
      </div>
      <div class="ct-kpi k-orange" :class="{ 'is-active': activeStat === 'WARN' }" @click="applyStat('WARN')">
        <div class="k-label">临期预警</div>
        <div class="k-num">{{ stats.WARN ?? '—' }}</div>
        <div class="k-note">tagType=warning，窗口取配置 {{ expiringDays }} 天</div>
      </div>
      <div class="ct-kpi k-red" :class="{ 'is-active': activeStat === 'BAD' }" @click="applyStat('BAD')">
        <div class="k-label">异常 / 过期</div>
        <div class="k-num">{{ stats.BAD ?? '—' }}</div>
        <div class="k-note">tagType=error（过期等）</div>
      </div>
      <div class="ct-kpi" :class="{ 'is-active': activeStat === 'UNVERIFIED' }" @click="applyStat('UNVERIFIED')">
        <div class="k-label">未验真</div>
        <div class="k-num">{{ stats.UNVERIFIED ?? '—' }}</div>
        <div class="k-note">verification_status 未验真</div>
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
          <a-input v-model:value="query.certType" placeholder="证照大类字典未配置，暂不支持按大类筛选" style="width: 240px" disabled />
        </AttrFilter>
        <AttrFilter v-model="query.certStatus" label="台账状态" :options="statusFilters" @change="applyFilter" />
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <a-button type="primary" @click="applyFilter">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
          <a class="ct-advanced-toggle" @click="advancedOpen = !advancedOpen">
            {{ advancedOpen ? '收起高级筛选 ▲' : '高级筛选 ▼' }}
          </a>
          <span class="ml-auto text-xs text-muted-foreground">编号掩码 <a-switch v-model:checked="maskNo" size="small" /></span>
        </div>

        <!-- 高级筛选：非高频条件收进折叠区，主行只留「名称 / 编号 / 大类 / 状态」 -->
        <div v-if="advancedOpen" class="ct-advanced">
          <AttrFilter label="持证主体">
            <a-input v-model:value="query.holderName" placeholder="模糊匹配，回车查询" style="width: 240px" allow-clear @press-enter="applyFilter" />
          </AttrFilter>
          <AttrFilter v-model="query.verificationStatus" label="验真状态" :options="verifyFilters" @change="applyFilter" />
          <AttrFilter label="有效期至（从）">
            <a-date-picker
              v-model:value="query.validToFrom"
              value-format="YYYY-MM-DD"
              placeholder="起始日"
              style="width: 160px"
              @change="applyFilter"
            />
          </AttrFilter>
          <AttrFilter label="有效期至（到）">
            <a-date-picker
              v-model:value="query.validToTo"
              value-format="YYYY-MM-DD"
              placeholder="截止日"
              style="width: 160px"
              @change="applyFilter"
            />
          </AttrFilter>
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
          :row-selection="rowSelection"
          :scroll="{ x: 1660 }"
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
                {{ maskCertNo(record.certNo, maskNo) }} · #{{ record.id }}
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
              <span
                class="ct-dot"
                :style="{ background: dotColor(dictTag('cert_status', record.certStatus).color) }"
              ></span>
              {{ dictTag('cert_status', record.certStatus).label }}
            </template>
            <template v-else-if="column.key === 'verificationStatus'">
              <span
                class="ct-dot"
                :style="{ background: dotColor(dictTag('verification_status', record.verificationStatus).color) }"
              ></span>
              {{ dictTag('verification_status', record.verificationStatus).label }}
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
              <!-- 行内分层：常看的「查看 / 文件」留明面（文件入口必须显眼——用户第 2 条），
                   其余低频动作进「更多」。「查看」跳独立详情页（不再在表格下方渲染占位块）。 -->
              <a-space size="small">
                <a @click="goDetail(record)">查看</a>
                <a @click="goDetail(record, 'files')">文件</a>
                <a-dropdown>
                  <a @click.prevent>
                    更多
                    <IconifyIcon icon="lucide:chevron-down" class="inline-block align-middle" />
                  </a>
                  <template #overlay>
                    <a-menu @click="({ key }: { key: number | string }) => onRowAction(key, record)">
                      <a-menu-item v-if="can('bidding:certificate:update')" key="edit">编辑</a-menu-item>
                      <a-menu-item v-if="can('bidding:certificate:update')" key="status">状态流转</a-menu-item>
                      <a-menu-item v-if="can('bidding:certificate:lifecycle:apply')" key="apply">
                        发起变更 / 续期 / 注销 / 年检
                      </a-menu-item>
                      <a-menu-item v-if="can('bidding:certificate:query')" key="history">验真历史</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
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
      </a-tab-pane>

      <a-tab-pane key="lifecycle" tab="流程（变更 / 续期 / 注销吊销 / 年检）">
        <!-- 嵌入生命周期页：它没有路由依赖，可直接当组件用；embedded 去掉其自带页头与内边距 -->
        <LifecyclePanel embedded />
      </a-tab-pane>
    </a-tabs>
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
        <a-select
          v-model:value="historyQuery.verifyChannel"
          :options="historyChannelOptions"
          allow-clear
          placeholder="验真渠道"
          style="width: 170px"
          @change="
            () => {
              historyQuery.pageNo = 1;
              loadHistory();
            }
          "
        />
        <a-select
          v-model:value="historyQuery.verifyResult"
          :options="historyResultOptions"
          allow-clear
          placeholder="验真结论"
          style="width: 170px"
          @change="
            () => {
              historyQuery.pageNo = 1;
              loadHistory();
            }
          "
        />
        <a-button @click="loadHistory">刷新</a-button>
        <span class="ct-panel-meta">共 {{ historyTotal }} 条留痕</span>
      </a-space>
      <a-spin :spinning="historyLoading">
        <a-table
          class="ct-table"
          :columns="[
            { title: '证照', dataIndex: 'certName', key: 'certName', width: 260, ellipsis: true },
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

    <!-- 证照到期预警清单（后端变更通知-给前端.md §7①：只读展示，处理入口待后端开放） -->
    <a-drawer v-model:open="alertOpen" class="bid-ind" :width="880" title="证照到期预警清单" destroy-on-close>
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <a-select
          v-model:value="alertQuery.alertType"
          :options="alertTypeOptions.length ? alertTypeOptions : [{ label: 'EXPIRY 到期', value: 'EXPIRY' }, { label: 'REVIEW 复审', value: 'REVIEW' }, { label: 'RENEWAL 换证', value: 'RENEWAL' }]"
          placeholder="预警类型"
          allow-clear
          style="width: 150px"
          @change="reloadAlerts"
        />
        <a-select
          v-model:value="alertQuery.alertLevel"
          :options="alertLevelOptions.length ? alertLevelOptions : [{ label: 'INFO', value: 'INFO' }, { label: 'WARNING', value: 'WARNING' }, { label: 'CRITICAL', value: 'CRITICAL' }]"
          placeholder="档位"
          allow-clear
          style="width: 130px"
          @change="reloadAlerts"
        />
        <a-select v-model:value="alertQuery.handled" placeholder="处理状态" allow-clear style="width: 130px" @change="reloadAlerts">
          <a-select-option :value="false">待处理</a-select-option>
          <a-select-option :value="true">已处理</a-select-option>
        </a-select>
        <span class="ct-sub">共 {{ alertTotal }} 条 · triggeredAt 倒序 · 处理入口待后端开放</span>
      </div>
      <a-table
        class="ct-table"
        :columns="alertColumns"
        :data-source="alertRows"
        :loading="alertLoading"
        :pagination="{
          current: alertQuery.pageNo,
          pageSize: alertQuery.pageSize,
          total: alertTotal,
          onChange: (p: number) => { alertQuery.pageNo = p; void loadAlerts(); },
          showSizeChanger: false,
        }"
        row-key="id"
        size="small"
        :scroll="{ x: 780 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'alertLevel'">
            <a-tag :color="alertLevelColor(record.alertLevel)">{{ dictLabel('alert_level', record.alertLevel) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'alertType'">
            <a-tag>{{ dictLabel('alert_type', record.alertType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'triggeredAt'">{{ fmtDateTime(record.triggeredAt) }}</template>
          <template v-else-if="column.key === 'handled'">
            <a-tag :color="record.handled ? 'green' : 'orange'">{{ record.handled ? '已处理' : '待处理' }}</a-tag>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">暂无预警：可先点「预警扫描」补跑生成</div></template>
      </a-table>
    </a-drawer>

    <CertForm v-model:open="formOpen" :record="formRecord" @saved="onSaved" />
    <StatusModal v-model:open="statusOpen" :cert="statusRecord" @done="onSaved" />
    <LifecycleApply v-model:open="applyOpen" :cert="applyRecord" @applied="onSaved" />
  </div>
</template>

<style scoped>
/* 高级筛选折叠区（证照台账）：非高频条件收起，保持主行清爽 */
.ct-advanced {
  padding-top: 8px;
  margin-top: 8px;
  border-top: 1px dashed var(--ind-line, #2e333b);
}

.ct-advanced-toggle {
  font-size: 13px;
}

/* 看板卡片可点击联动：悬停/选中态 + 状态圆点。
   圆点颜色不在此处写死语义，由 script 的 dotColor()/TAG_HEX 从字典 tagType 推导（契约 §0.5）。 */
.ct-kpis.is-loading {
  opacity: 0.6;
}

.ct-kpi {
  cursor: pointer;
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease;
}

.ct-kpi:hover {
  box-shadow: var(--ind-shadow-lg, 0 4px 12px hsl(var(--foreground) / 16%));
  transform: translateY(-1px);
}

.ct-kpi.is-active {
  outline: 2px solid var(--ind-line, #2e333b);
  outline-offset: -2px;
}

.ct-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  vertical-align: middle;
  border-radius: 50%;
}

.ct-user {
  white-space: nowrap;
}
</style>
