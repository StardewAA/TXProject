<script setup lang="ts">
/* 企业资质（菜单终态 60005200 / component bidding/qualification/index / componentName QualificationIndex）
 *  权限：页面 bidding:qualification:query；动作 create / renew / approve / reject / alert-run / material:query
 *
 * 三页签：
 *   ① 资质台账      GET /bidding/qualification/page（筛选：entId/序列/状态/关键字/临近到期天数）
 *                  + 详情抽屉 GET /get（含绑定证照摘要与最近一次达标核查）
 *                  + 延续申报 POST /renew（乐观锁回传列表 version）
 *   ② 延续申报台账  GET /bidding/qualification/apply/page + 审批通过 POST /approve / 退回 POST /reject
 *   ③ 材料完备性    GET /bidding/enterprise-material/completeness（字段逐字对齐 RespVO）
 *
 * 字典口径（契约 §0.5，禁止自造中文枚举）：一律 dictLabel/dictTag 优先、缺字典回落原始 code。
 *   已注册：qual_status、qual_apply_type、qual_apply_status、qual_serial（2026-09-20 新增）、
 *   material_category / material_result / material_status、safety_permit_status、safety_risk_flag、risk_level。
 *   非字典：qual_grade —— 其值域由**现行标准库数据**决定（后端 /bidding/qualification/standard/options
 *   口径为"取自现行标准库，不固化字典"），页面原样展示；登记成字典反而制造第二套编码。
 *   注意：字典类型名以 bidding_dict_data 为准 —— 申报类型/状态为 qual_apply_type / qual_apply_status（曾误写 apply_*）。
 * 时间口径：LocalDate（issueDate/validTo/submitDate/dueDate/newValidTo）为 'yyyy-MM-dd' 字符串；
 *   仅 latestReview.reviewedAt 是 LocalDateTime（epoch 毫秒）→ 过 fmtDateTime。 */
import { computed, h, onMounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { type CertificateApi, getMaterialCompleteness } from '#/api/bidding/certificate';
import { getPersonnelProfilePage } from '#/api/bidding/personnel';
import {
  approveQualificationRenew,
  changePermitStatus,
  createQualification,
  createQualificationAsset,
  createQualificationPerformance,
  createQualificationStaff,
  createQualificationStandard,
  createSafetyPermit,
  getQualification,
  getQualificationAlertPage,
  getQualificationApplyPage,
  getQualificationAssetPage,
  getQualificationPage,
  getQualificationPerformancePage,
  getQualificationPermitPage,
  getQualificationReviewPage,
  getQualificationStaffPage,
  getStandardOptions,
  getStandardPage,
  handleQualificationAlert,
  QualificationApi,
  rejectQualificationRenew,
  runComplianceReview,
  runQualificationAlertScan,
  submitQualificationRenew,
} from '#/api/bidding/qualification';
import { APPLY_STATUS_OPTIONS, APPLY_TYPE_OPTIONS } from '#/api/bidding/qualification';

import { fmtDate, fmtDateTime } from '../certificate/format';
import { useCertMeta } from '../certificate/useCertMeta';
import CopyText from '../components/CopyText.vue';

import '../certificate/cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictLabel, dictOptions, dictTag } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

/** 字典优先、缺字典回退 code（不发明中文枚举） */
function tagOf(dictType: string, code?: null | string) {
  if (!code) return { color: 'default', label: '—' };
  if (dictOptions(dictType).length > 0) return dictTag(dictType, code);
  return { color: 'default', label: code };
}
function textOf(dictType: string, code?: null | string) {
  if (!code) return '—';
  if (dictOptions(dictType).length > 0) return dictLabel(dictType, code);
  return code;
}

const activeKey = ref('ledger');

/* ================= ① 资质台账 ================= */
const query = reactive<QualificationApi.PageReq>({
  pageNo: 1,
  pageSize: 10,
  entId: undefined,
  qualSerial: undefined,
  qualStatus: undefined,
  keyword: undefined,
  expiringInDays: undefined,
});
const rows = ref<QualificationApi.Qualification[]>([]);
const total = ref(0);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const page = await getQualificationPage({ ...query });
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
  query.entId = undefined;
  query.qualSerial = undefined;
  query.qualStatus = undefined;
  query.keyword = undefined;
  query.expiringInDays = undefined;
  query.pageNo = 1;
  void load();
}

const kpi = computed(() => {
  const list = rows.value;
  return {
    pendingApply: list.filter((r) => r.hasPendingApply === true).length,
    soon: list.filter((r) => r.daysToExpire !== null && r.daysToExpire !== undefined && r.daysToExpire >= 0 && r.daysToExpire <= 30).length,
    valid: list.filter((r) => r.qualStatus === 'VALID').length,
  };
});

const ledgerColumns = [
  { title: '资质证书编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '资质证书编号' }),  dataIndex: 'certNo', key: 'certNo', width: 200 },
  { title: '序列', dataIndex: 'qualSerial', key: 'qualSerial', width: 130 },
  { title: '类别', dataIndex: 'qualCategory', key: 'qualCategory', width: 220 },
  { title: '等级', dataIndex: 'qualGrade', key: 'qualGrade', width: 100 },
  { title: '状态', dataIndex: 'qualStatus', key: 'qualStatus', width: 130 },
  { title: '有效期至', dataIndex: 'validTo', key: 'validTo', width: 120 },
  { title: '剩余天数', dataIndex: 'daysToExpire', key: 'daysToExpire', width: 110 },
  { title: '在途申报', dataIndex: 'hasPendingApply', key: 'hasPendingApply', width: 100 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '操作', key: 'action', width: 280, fixed: 'right' as const },
];

function daysColor(d?: null | number) {
  if (d === null || d === undefined) return 'default';
  if (d < 0) return 'red';
  if (d <= 30) return 'orange';
  return 'green';
}

/* ===== 详情抽屉 ===== */
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<null | QualificationApi.Detail>(null);

async function openDetail(row: QualificationApi.Qualification) {
  detailOpen.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    detail.value = await getQualification(row.id);
  } catch {
    // 详情失败保留空态（列表行仍可见）
  } finally {
    detailLoading.value = false;
  }
}

/* ===== 延续申报 ===== */
const renewOpen = ref(false);
const renewSaving = ref(false);
const renewForm = reactive<{ dueDate?: string; qualificationId: number; remark?: string; version: number }>({
  qualificationId: 0,
  version: 0,
});
const renewRow = ref<null | QualificationApi.Qualification>(null);

function openRenew(row: QualificationApi.Qualification) {
  if (row.version === undefined) {
    message.warning('该行缺少 version（乐观锁），请刷新后重试');
    return;
  }
  if (row.hasPendingApply) {
    message.warning('该资质已有在途延续申报，请先在「延续申报台账」处理');
    return;
  }
  renewRow.value = row;
  Object.assign(renewForm, { qualificationId: row.id, version: row.version, dueDate: undefined, remark: undefined });
  renewOpen.value = true;
}

async function submitRenew() {
  renewSaving.value = true;
  try {
    await submitQualificationRenew({ ...renewForm });
    message.success('延续申报已提交');
    renewOpen.value = false;
    refreshAfterWrite();
  } catch {
    // §0.4 静默：失败详情由请求层呈现
  } finally {
    renewSaving.value = false;
  }
}

/* ================= ② 延续申报台账 ================= */
const applyQuery = reactive<QualificationApi.ApplyPageReq>({
  pageNo: 1,
  pageSize: 10,
  entId: undefined,
  qualId: undefined,
  applyType: undefined,
  status: undefined,
});
const applyRows = ref<QualificationApi.ApplyRecord[]>([]);
const applyTotal = ref(0);
const applyLoading = ref(false);

async function loadApplies() {
  applyLoading.value = true;
  try {
    const page = await getQualificationApplyPage({ ...applyQuery, entId: query.entId });
    applyRows.value = page.list ?? [];
    applyTotal.value = page.total ?? 0;
  } catch {
    applyRows.value = [];
    applyTotal.value = 0;
  } finally {
    applyLoading.value = false;
  }
}

const applyColumns = [
  { title: '申报单号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '申报单号' }),  dataIndex: 'id', key: 'id', width: 110 },
  { title: '资质', dataIndex: 'qualId', key: 'qualId', width: 90 },
  { title: '申报类型', dataIndex: 'applyType', key: 'applyType', width: 120 },
  { title: '目标等级', dataIndex: 'targetGrade', key: 'targetGrade', width: 100 },
  { title: '提交日期', dataIndex: 'submitDate', key: 'submitDate', width: 120 },
  { title: '申请延续至', dataIndex: 'dueDate', key: 'dueDate', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 130 },
  { title: '结果文号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '结果文号' }),  dataIndex: 'resultNo', key: 'resultNo', width: 140 },
  { title: '新证书编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '新证书编号' }),  dataIndex: 'newCertNo', key: 'newCertNo', width: 170 },
  { title: '新有效期至', dataIndex: 'newValidTo', key: 'newValidTo', width: 120 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '操作', key: 'action', width: 150, fixed: 'right' as const },
];

/** 可审批的状态（终态 ISSUED/REJECTED 不再受理；与 QualificationApplyStatusEnum 一致） */
const AUDITABLE = new Set(['ACCEPTED', 'APPROVED', 'REVIEWING', 'SUBMITTED']);
function auditable(r: QualificationApi.ApplyRecord) {
  return AUDITABLE.has(r.status);
}

const approveOpen = ref(false);
const approveSaving = ref(false);
const approveFormRef = ref();
const approveForm = reactive<QualificationApi.ApproveReq>({ applyId: 0, version: 0, newValidTo: '' });
const approveRules = {
  newValidTo: [{ required: true, message: '延续后有效期不能为空', trigger: 'change' }],
};

function openApprove(r: QualificationApi.ApplyRecord) {
  if (r.version === undefined) {
    message.warning('该申报单缺少 version（乐观锁），请刷新后重试');
    return;
  }
  Object.assign(approveForm, {
    applyId: r.id,
    version: r.version,
    resultNo: r.resultNo,
    newCertNo: r.newCertNo,
    newValidTo: r.newValidTo ?? r.dueDate ?? '',
  });
  approveOpen.value = true;
}

async function submitApprove() {
  try {
    await approveFormRef.value?.validate();
  } catch {
    return;
  }
  approveSaving.value = true;
  try {
    await approveQualificationRenew({ ...approveForm });
    message.success('审批通过，主档已回写 VALID + 新有效期');
    approveOpen.value = false;
    refreshAfterWrite();
  } catch {
    // 静默
  } finally {
    approveSaving.value = false;
  }
}

function confirmReject(r: QualificationApi.ApplyRecord) {
  if (r.version === undefined) {
    message.warning('该申报单缺少 version（乐观锁），请刷新后重试');
    return;
  }
  let remark = '';
  Modal.confirm({
    title: `退回延续申报 #${r.id}`,
    okText: '退回',
    content: () =>
      // 退回理由必填：后端 remark 可空，但业务上必须留痕
      h('textarea', {
        class: 'ant-input',
        placeholder: '退回理由（必填，将写入 remark）',
        rows: 3,
        style: 'width:100%',
        onInput: (e: any) => {
          remark = e.target.value;
        },
      }),
    okType: 'danger',
    async onOk() {
      if (!remark.trim()) {
        message.warning('请填写退回理由');
        throw new Error('remark required');
      }
      await rejectQualificationRenew({ applyId: r.id, version: r.version as number, remark: remark.trim() });
      message.success('已退回，主档还原 VALID');
      refreshAfterWrite();
    },
  });
}

/* ================= ③ 材料完备性 ================= */
const completenessQuery = reactive<{ asOf?: string; entId?: number }>({ entId: undefined, asOf: undefined });
const completeness = ref<CertificateApi.MaterialCompleteness | null>(null);
const completenessLoading = ref(false);
const completenessError = ref('');

const RESULT_FALLBACK: Record<string, { color: string; label: string }> = {
  PASS: { color: 'green', label: '齐备' },
  RISK: { color: 'orange', label: '部分缺失或临近到期' },
  FAIL: { color: 'red', label: '必备材料缺失' },
};
const resultTag = computed(() => {
  const r = completeness.value?.result;
  if (!r) return { color: 'default', label: '—' };
  if (dictOptions('material_result').length > 0) return dictTag('material_result', r);
  return RESULT_FALLBACK[r] ?? { color: 'default', label: r };
});

const itemColumns = [
  { title: '材料名称', dataIndex: 'materialLabel', key: 'materialLabel', width: 220 },
  { title: '类型 code', dataIndex: 'materialType', key: 'materialType', width: 170 },
  { title: '大类', dataIndex: 'category', key: 'category', width: 110 },
  { title: '必备', dataIndex: 'mandatory', key: 'mandatory', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '有效期至', dataIndex: 'validTo', key: 'validTo', width: 120 },
  { title: '匹配记录', dataIndex: 'refName', key: 'refName', width: 180 },
  { title: '说明', dataIndex: 'message', key: 'message' },
];

async function loadCompleteness(silent = false) {
  const entId = completenessQuery.entId ?? query.entId;
  if (!entId) {
    if (!silent) message.warning('请先在顶部填写企业主体 id（entId）');
    return;
  }
  completenessLoading.value = true;
  completenessError.value = '';
  completeness.value = null;
  try {
    completeness.value = await getMaterialCompleteness(entId, completenessQuery.asOf);
  } catch (error: any) {
    // 1060107013「企业材料不完备」是业务结论：优先用 data 渲染，无 data 才当错误
    const data = error?.data ?? error?.response?.data?.data;
    if (data?.result) {
      completeness.value = data;
    } else {
      completenessError.value = error?.msg ?? error?.message ?? '完备性自查失败';
    }
  } finally {
    completenessLoading.value = false;
  }
}

/* ================= 建档 / 预警扫描 ================= */
const formOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const createdId = ref<null | number>(null);
const form = reactive<QualificationApi.SaveReq>({
  entId: undefined as unknown as number,
  qualSerial: 'CONSTRUCTION',
  qualCategory: '',
  qualGrade: '',
  certNo: '',
});
const rules = {
  entId: [{ required: true, message: '企业主体不能为空', trigger: 'change' }],
  qualSerial: [{ required: true, message: '资质序列不能为空', trigger: 'change' }],
  qualCategory: [{ required: true, message: '资质类别不能为空', trigger: 'blur' }],
  qualGrade: [{ required: true, message: '资质等级不能为空', trigger: 'blur' }],
  certNo: [{ required: true, message: '资质证书编号不能为空（租户内唯一）', trigger: 'blur' }],
};

function onCreate() {
  Object.assign(form, {
    id: undefined,
    version: undefined,
    entId: query.entId ?? (undefined as unknown as number),
    qualSerial: 'CONSTRUCTION',
    qualCategory: '',
    qualGrade: '',
    certNo: '',
    issuingAuthority: undefined,
    issueDate: undefined,
    validTo: undefined,
    approvalScope: undefined,
    bindedCertId: undefined,
    remark: undefined,
  });
  createdId.value = null;
  formOpen.value = true;
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    createdId.value = await createQualification({ ...form });
    message.success(`资质建档成功（id=${createdId.value}）`);
    formOpen.value = false;
    refreshAfterWrite();
  } catch {
    // 静默
  } finally {
    saving.value = false;
  }
}

const scanning = ref(false);
const scanHits = ref<null | number>(null);
const scanAt = ref<null | number>(null);

async function doScan() {
  scanning.value = true;
  try {
    scanHits.value = await runQualificationAlertScan();
    scanAt.value = Date.now();
    message.success('预警扫描已执行');
  } catch {
    // 静默
  } finally {
    scanning.value = false;
  }
}

/* ===== 要素录入（人员 / 业绩 / 资产装备，权限 bidding:qualification:element） ===== */
const elementOpen = ref(false);
const elementSaving = ref(false);
const elementTab = ref('staff');
const elementIds = reactive<{ entId: number; qualId: number }>({ entId: 0, qualId: 0 });
const staffForm = reactive<QualificationApi.StaffSaveReq>({ entId: 0, personName: '', qualId: 0 });
const perfForm = reactive<QualificationApi.PerformanceSaveReq>({ entId: 0, projectName: '', qualId: 0 });
const assetForm = reactive<QualificationApi.AssetSaveReq>({ entId: 0, name: '' });
/* 资质人员姓名：**优先从员工档案选**（本模块已有员工档案，手打易与档案对不上），
 * 但保留手填能力——资质申报里的"拟聘人员"可能尚未建档，故用 AutoComplete 而非 Select。
 * 注：后端 StaffSaveReqVO 只有 personName（无 personId），所以这里只回填姓名，不伪造关联 ID。 */
const personNameOptions = ref<Array<{ label: string; value: string }>>([]);
let personNameLoaded = false;
async function loadPersonNameOptions(): Promise<void> {
  if (personNameLoaded) return;
  personNameLoaded = true;
  try {
    const res = await getPersonnelProfilePage({ pageNo: 1, pageSize: 200 });
    personNameOptions.value = (res?.list ?? [])
      .map((p) => p.personName)
      .filter((n): n is string => !!n)
      .map((n) => ({ label: n, value: n }));
  } catch {
    personNameOptions.value = [];
    personNameLoaded = false; // 失败允许下次重试
  }
}

function openElement(row: QualificationApi.Qualification) {
  Object.assign(elementIds, { entId: row.entId, qualId: row.id });
  Object.assign(staffForm, {
    bindedCertId: undefined,
    entId: row.entId,
    isFullTime: undefined,
    major: undefined,
    personName: '',
    qualId: row.id,
    regNo: undefined,
    regValidTo: undefined,
    staffRole: undefined,
    titleLevel: undefined,
  });
  Object.assign(perfForm, {
    completionDate: undefined,
    contractAmount: undefined,
    entId: row.entId,
    evidenceFileIds: undefined,
    isPlatformVerified: undefined,
    ownerName: undefined,
    projectName: '',
    projectType: undefined,
    qualId: row.id,
    qualityResult: undefined,
    role: undefined,
  });
  Object.assign(assetForm, {
    assetType: undefined,
    entId: row.entId,
    evidenceFileIds: undefined,
    name: '',
    quantity: undefined,
    spec: undefined,
    statYear: undefined,
    valueAmount: undefined,
  });
  elementTab.value = 'staff';
  elementOpen.value = true;
}

/** 逗号/空格分隔 id → JSON 数组字符串（后端 evidenceFileIds 为 String 列，存 JSON） */
function toEvidenceJson(text?: string) {
  const ids = (text ?? '')
    .split(/[,\s，]+/)
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => Number(t))
    .filter((n) => Number.isFinite(n));
  return ids.length > 0 ? JSON.stringify(ids) : undefined;
}

async function submitElement() {
  if (elementTab.value === 'staff' && !staffForm.personName?.trim()) {
    message.warning('人员姓名不能为空');
    return;
  }
  if (elementTab.value === 'performance' && !perfForm.projectName?.trim()) {
    message.warning('项目名称不能为空');
    return;
  }
  if (elementTab.value === 'asset' && !assetForm.name?.trim()) {
    message.warning('资产/装备名称不能为空');
    return;
  }
  elementSaving.value = true;
  try {
    if (elementTab.value === 'staff') {
      await createQualificationStaff({ ...staffForm });
    } else if (elementTab.value === 'performance') {
      await createQualificationPerformance({ ...perfForm, evidenceFileIds: toEvidenceJson(perfForm.evidenceFileIds) });
    } else {
      await createQualificationAsset({ ...assetForm, evidenceFileIds: toEvidenceJson(assetForm.evidenceFileIds) });
    }
    message.success('要素已录入');
    elementOpen.value = false;
    refreshAfterWrite();
  } catch {
    // 静默：§0.4 错误结构由请求层呈现
  } finally {
    elementSaving.value = false;
  }
}

/* ===== 达标核查执行（权限 bidding:qualification:review） ===== */
const reviewType = ref('SELF');

function confirmReview(row: QualificationApi.Qualification) {
  Modal.confirm({
    title: `执行达标核查：资质 #${row.id}`,
    okText: '开始核查',
    content: `按当前生效标准复算要素差额，核查类型 ${reviewType.value}；核查只返回主键，结论请在「详情 → 最近一次达标核查」回读。`,
    async onOk() {
      const id = await runComplianceReview({ qualId: row.id, reviewType: reviewType.value });
      message.success(`核查已执行（主键 ${id}）`);
      if (detail.value?.id === row.id) void openDetail(row);
    },
  });
}

/* ===== 安许建档（权限 bidding:qualification:permit） ===== */
const permitOpen = ref(false);
const permitSaving = ref(false);
const permitForm = reactive<QualificationApi.PermitCreateReq>({ entId: 0, permitNo: '' });

function openPermit() {
  Object.assign(permitForm, {
    bindedCertId: undefined,
    entId: query.entId ?? 0,
    issueDate: undefined,
    issuingAuthority: undefined,
    legalPerson: undefined,
    permitNo: '',
    remark: undefined,
    validTo: undefined,
  });
  permitOpen.value = true;
}

async function submitPermit() {
  if (!permitForm.entId || !permitForm.permitNo?.trim()) {
    message.warning('企业主体与安全生产许可证编号不能为空');
    return;
  }
  permitSaving.value = true;
  try {
    const id = await createSafetyPermit({ ...permitForm });
    message.success(`安许建档成功（id=${id}）`);
    permitOpen.value = false;
    refreshAfterWrite();
  } catch {
    // 静默
  } finally {
    permitSaving.value = false;
  }
}

/* ================= 契约 §2：页签 ③ 标准与要素 / ④ 安许证与预警 ================= */

/** 写操作成功后：当前页签与顶部完备性卡片必须同时失效重拉（契约 §2 ①），避免列表已更新而卡片仍是旧结论 */
function refreshAfterWrite() {
  void load();
  void loadApplies();
  void loadCompleteness(true);
  if (activeKey.value === 'standard') {
    void loadStd();
    void loadElements();
    void loadReviews();
  }
  if (activeKey.value === 'safety') {
    void loadPermits();
    void loadAlerts();
  }
}

/* ===== ③ 资质标准 ===== */
const stdQuery = reactive<QualificationApi.StandardPageReq>({ onlyEffective: true, pageNo: 1, pageSize: 10 });
const stdRows = ref<QualificationApi.Standard[]>([]);
const stdTotal = ref(0);
const stdLoading = ref(false);
const stdLoaded = ref(false);
const stdOptions = ref<null | QualificationApi.StandardOptions>(null);

/** 序列 / 类别 / 等级 / 版本候选一律来自 /standard/options，前端不硬编码枚举（契约 §0.5） */
const stdSerialOptions = computed(() =>
  (stdOptions.value?.serials ?? []).map((s) => ({ label: s.qualSerial ?? '', value: s.qualSerial ?? '' })),
);
const stdCategoryOptions = computed(() => {
  const hit = stdOptions.value?.serials?.find((s) => s.qualSerial === stdQuery.qualSerial);
  return (hit?.categories ?? []).map((c) => ({ label: c.qualCategory ?? '', value: c.qualCategory ?? '' }));
});
const stdGradeOptions = computed(() => {
  const hit = stdOptions.value?.serials?.find((s) => s.qualSerial === stdQuery.qualSerial);
  const cat = hit?.categories?.find((c) => c.qualCategory === stdQuery.qualCategory);
  return (cat?.grades ?? []).map((g) => ({ label: g, value: g }));
});
const stdVersionOptions = computed(() =>
  (stdOptions.value?.stdVersions ?? []).map((v) => ({ label: v, value: v })),
);

async function loadStdOptions() {
  if (stdOptions.value) return;
  try {
    stdOptions.value = await getStandardOptions();
  } catch {
    // 静默：选项拉不到时下拉为空，不影响列表
  }
}

async function loadStd() {
  stdLoading.value = true;
  try {
    const page = await getStandardPage({ ...stdQuery });
    stdRows.value = page.list ?? [];
    stdTotal.value = page.total ?? 0;
    stdLoaded.value = true;
  } catch {
    // 静默
  } finally {
    stdLoading.value = false;
  }
}
function onStdPage(p: number) {
  stdQuery.pageNo = p;
  void loadStd();
}

const stdColumns = [
  { title: '标准版本', dataIndex: 'stdVersion', key: 'stdVersion', width: 130 },
  { title: '序列', dataIndex: 'qualSerial', key: 'qualSerial', width: 150 },
  { title: '类别', dataIndex: 'qualCategory', key: 'qualCategory', width: 130 },
  { title: '等级', dataIndex: 'qualGrade', key: 'qualGrade', width: 110 },
  { title: '人员要求', dataIndex: 'reqStaff', key: 'reqStaff', ellipsis: true },
  { title: '业绩要求', dataIndex: 'reqPerformance', key: 'reqPerformance', ellipsis: true },
  { title: '资产要求', dataIndex: 'reqAssets', key: 'reqAssets', ellipsis: true, width: 160 },
  { title: '生效起', dataIndex: 'effectiveFrom', key: 'effectiveFrom', width: 115 },
  { title: '生效止', dataIndex: 'effectiveTo', key: 'effectiveTo', width: 115 },
  { title: '生效中', key: 'isEffective', width: 90 },
  { title: '来源', dataIndex: 'sourceRef', key: 'sourceRef', width: 140, ellipsis: true },
];

const stdFormOpen = ref(false);
const stdSaving = ref(false);
const stdForm = reactive<QualificationApi.StandardCreateReq>({
  qualCategory: '',
  qualGrade: '',
  qualSerial: '',
  stdVersion: '',
});

function openStandard() {
  Object.assign(stdForm, {
    effectiveFrom: undefined,
    effectiveTo: undefined,
    qualCategory: '',
    qualGrade: '',
    qualSerial: '',
    reqAssets: undefined,
    reqEquipment: undefined,
    reqPerformance: undefined,
    reqStaff: undefined,
    sourceRef: undefined,
    stdVersion: '',
  });
  void loadStdOptions();
  stdFormOpen.value = true;
}

async function submitStandard() {
  if (!stdForm.qualSerial || !stdForm.qualCategory || !stdForm.qualGrade || !stdForm.stdVersion) {
    message.warning('序列 / 类别 / 等级 / 标准版本不能为空');
    return;
  }
  stdSaving.value = true;
  try {
    const id = await createQualificationStandard({ ...stdForm });
    message.success(`标准已登记（id=${id}）`);
    stdFormOpen.value = false;
    stdOptions.value = null; // 新序列/等级可能新增候选，下次进页签重拉
    refreshAfterWrite();
  } catch {
    // 静默
  } finally {
    stdSaving.value = false;
  }
}

/* ===== ③ 要素台账（人员 / 业绩 / 资产装备） ===== */
const elemQualId = ref<number | undefined>(undefined);
const elemTabKey = ref('staff');
const elemLoading = ref(false);
const elemTotal = ref(0);
const elemPage = reactive({ pageNo: 1, pageSize: 10 });
const elemRows = ref<Array<QualificationApi.Asset | QualificationApi.Performance | QualificationApi.Staff>>([]);

const STAFF_COLUMNS = [
  { title: '姓名', dataIndex: 'personName', key: 'personName', width: 110 },
  { title: '岗位角色', dataIndex: 'staffRole', key: 'staffRole', width: 170 },
  { title: '专业', dataIndex: 'major', key: 'major', width: 130 },
  { title: '职称等级', dataIndex: 'titleLevel', key: 'titleLevel', width: 110 },
  { title: '注册号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '注册号' }),  dataIndex: 'regNo', key: 'regNo', width: 140 },
  { title: '全职', key: 'isFullTime', width: 80 },
  { title: '社保状态', dataIndex: 'socialSecurityStatus', key: 'socialSecurityStatus', width: 110 },
  { title: '继续教育学时', key: 'eduHours', width: 130 },
  { title: '注册有效期至', dataIndex: 'regValidTo', key: 'regValidTo', width: 130 },
  { title: '绑定证照', key: 'bindedCertId', width: 100 },
];
const PERFORMANCE_COLUMNS = [
  { title: '项目名称', dataIndex: 'projectName', key: 'projectName', width: 220 },
  { title: '项目类型', dataIndex: 'projectType', key: 'projectType', width: 130 },
  { title: '角色', dataIndex: 'role', key: 'role', width: 100 },
  { title: '合同金额', dataIndex: 'contractAmount', key: 'contractAmount', width: 130 },
  { title: '竣工日期', dataIndex: 'completionDate', key: 'completionDate', width: 120 },
  { title: '质量结论', dataIndex: 'qualityResult', key: 'qualityResult', width: 120 },
  { title: '业主', dataIndex: 'ownerName', key: 'ownerName', width: 160 },
  { title: '平台可核验', key: 'isPlatformVerified', width: 110 },
  { title: '规模指标', dataIndex: 'scaleMetrics', key: 'scaleMetrics', ellipsis: true },
];
const ASSET_COLUMNS = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 200 },
  { title: '资产类型', dataIndex: 'assetType', key: 'assetType', width: 150 },
  { title: '规格型号', dataIndex: 'spec', key: 'spec', width: 160 },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100 },
  { title: '价值金额', dataIndex: 'valueAmount', key: 'valueAmount', width: 130 },
  { title: '统计年份', dataIndex: 'statYear', key: 'statYear', width: 100 },
  { title: '证据文件', dataIndex: 'evidenceFileIds', key: 'evidenceFileIds', ellipsis: true },
];
const elemColumns = computed(() => {
  if (elemTabKey.value === 'performance') return PERFORMANCE_COLUMNS;
  if (elemTabKey.value === 'asset') return ASSET_COLUMNS;
  return STAFF_COLUMNS;
});

async function loadElements() {
  elemLoading.value = true;
  try {
    const params: QualificationApi.ElementPageReq = { ...elemPage, entId: query.entId, qualId: elemQualId.value };
    if (elemTabKey.value === 'performance') {
      const page = await getQualificationPerformancePage(params);
      elemRows.value = page.list ?? [];
      elemTotal.value = page.total ?? 0;
    } else if (elemTabKey.value === 'asset') {
      const page = await getQualificationAssetPage(params);
      elemRows.value = page.list ?? [];
      elemTotal.value = page.total ?? 0;
    } else {
      const page = await getQualificationStaffPage(params);
      elemRows.value = page.list ?? [];
      elemTotal.value = page.total ?? 0;
    }
  } catch {
    // 静默
  } finally {
    elemLoading.value = false;
  }
}
function onElemPage(p: number) {
  elemPage.pageNo = p;
  void loadElements();
}
function switchElemTab() {
  elemPage.pageNo = 1;
  void loadElements();
}

/* ===== ③ 达标核查历史 ===== */
const reviewQuery = reactive<QualificationApi.ReviewPageReq>({ pageNo: 1, pageSize: 10 });
const reviewRows = ref<QualificationApi.Review[]>([]);
const reviewTotal = ref(0);
const reviewLoading = ref(false);
/** 核查结论建议值（QualificationReviewResultEnum 未登记字典，命中字典时优先用字典） */
const REVIEW_RESULT_FALLBACK: Record<string, { color: string; label: string }> = {
  FAIL: { color: 'red', label: '不达标' },
  PARTIAL: { color: 'orange', label: '部分达标' },
  PASS: { color: 'green', label: '达标' },
};
/** 核查结论 → 标签（字典 risk_level/material_result 不覆盖核查结论，故用建议值兜底） */
function reviewResultTag(v?: null | string) {
  return REVIEW_RESULT_FALLBACK[v ?? ''] ?? { color: 'default', label: v ?? '—' };
}

const reviewResultOptions = computed(() =>
  Object.keys(REVIEW_RESULT_FALLBACK).map((k) => ({ label: REVIEW_RESULT_FALLBACK[k]!.label, value: k })),
);
const reviewColumns = [
  { title: '核查时间', key: 'reviewedAt', width: 165 },
  { title: '资质', dataIndex: 'qualId', key: 'qualId', width: 100 },
  { title: '标准版本', dataIndex: 'stdVersion', key: 'stdVersion', width: 130 },
  { title: '核查类型', dataIndex: 'reviewType', key: 'reviewType', width: 120 },
  { title: '结论', key: 'reviewResult', width: 110 },
  { title: '核查人', dataIndex: 'reviewer', key: 'reviewer', width: 100 },
  { title: '关联申报单', dataIndex: 'linkedApplyId', key: 'linkedApplyId', width: 110 },
  { title: '差额明细', dataIndex: 'diffItems', key: 'diffItems', ellipsis: true },
];

async function loadReviews() {
  reviewLoading.value = true;
  try {
    const page = await getQualificationReviewPage({ ...reviewQuery, entId: query.entId });
    reviewRows.value = page.list ?? [];
    reviewTotal.value = page.total ?? 0;
  } catch {
    // 静默
  } finally {
    reviewLoading.value = false;
  }
}
function onReviewPage(p: number) {
  reviewQuery.pageNo = p;
  void loadReviews();
}

/* ===== ④ 安许证台账 ===== */
const permitQuery = reactive<QualificationApi.PermitPageReq>({ pageNo: 1, pageSize: 10 });
const permitRows = ref<QualificationApi.Permit[]>([]);
const permitTotal = ref(0);
const permitLoading = ref(false);
const permitLoaded = ref(false);
const permitColumns = [
  { title: '许可证编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '许可证编号' }),  dataIndex: 'permitNo', key: 'permitNo', width: 200 },
  { title: '发证机关', dataIndex: 'issuingAuthority', key: 'issuingAuthority', width: 160 },
  { title: '发证日期', dataIndex: 'issueDate', key: 'issueDate', width: 115 },
  { title: '有效期至', dataIndex: 'validTo', key: 'validTo', width: 115 },
  { title: '剩余天数', key: 'daysToExpire', width: 100 },
  { title: '状态', key: 'permitStatus', width: 120 },
  { title: '风险', key: 'riskFlag', width: 120 },
  { title: '宽限至', key: 'graceExpireAt', width: 165 },
  { title: '法定代表人', dataIndex: 'legalPerson', key: 'legalPerson', width: 120 },
  { title: '操作', key: 'action', width: 110, fixed: 'right' as const },
];

async function loadPermits() {
  permitLoading.value = true;
  try {
    const page = await getQualificationPermitPage({ ...permitQuery, entId: query.entId });
    permitRows.value = page.list ?? [];
    permitTotal.value = page.total ?? 0;
    permitLoaded.value = true;
  } catch {
    // 静默
  } finally {
    permitLoading.value = false;
  }
}
function onPermitPage(p: number) {
  permitQuery.pageNo = p;
  void loadPermits();
}

/** 状态流转候选：逐条对应 SafetyPermitStatusEnum.TRANSITIONS，避免提交必然被拒的目标 */
const PERMIT_TRANSITIONS: Record<string, string[]> = {
  EXPIRED: ['SUSPENDED', 'REVOKED'],
  EXPIRING_SOON: ['VALID', 'SUSPENDED', 'REVOKED', 'EXPIRED'],
  REVOKED: [],
  SUSPENDED: ['VALID', 'EXPIRING_SOON', 'REVOKED'],
  VALID: ['EXPIRING_SOON', 'SUSPENDED', 'REVOKED'],
};
const permitStatusOpen = ref(false);
const permitStatusSaving = ref(false);
const permitStatusRow = ref<null | QualificationApi.Permit>(null);
const permitStatusForm = reactive<QualificationApi.PermitStatusChangeReq>({
  permitId: 0,
  targetStatus: '',
  version: 0,
});
/** 安许状态码表（逐字取自 SafetyPermitStatusEnum） */
const PERMIT_STATUS_OPTIONS = ['VALID', 'EXPIRING_SOON', 'EXPIRED', 'SUSPENDED', 'REVOKED'].map((s) => ({
  label: s,
  value: s,
}));

const permitTargetOptions = computed(() =>
  (PERMIT_TRANSITIONS[permitStatusRow.value?.permitStatus ?? ''] ?? []).map((s) => ({ label: s, value: s })),
);

function openPermitStatus(row: QualificationApi.Permit) {
  if (row.version === undefined) {
    message.warning('该行缺少 version（乐观锁），请刷新后重试');
    return;
  }
  permitStatusRow.value = row;
  Object.assign(permitStatusForm, { permitId: row.id, reason: undefined, targetStatus: '', version: row.version });
  permitStatusOpen.value = true;
}

async function submitPermitStatus() {
  if (!permitStatusForm.targetStatus) {
    message.warning('请选择目标状态');
    return;
  }
  if (permitStatusForm.targetStatus === 'REVOKED' && !permitStatusForm.reason?.trim()) {
    message.warning('吊销必须填写原因');
    return;
  }
  permitStatusSaving.value = true;
  try {
    await changePermitStatus({ ...permitStatusForm });
    message.success('安许状态已流转');
    permitStatusOpen.value = false;
    refreshAfterWrite();
  } catch {
    // 静默：非法迁移由后端返回 §0.4 错误
  } finally {
    permitStatusSaving.value = false;
  }
}

/* ===== ④ 预警清单 ===== */
const alertQuery = reactive<QualificationApi.AlertPageReq>({ pageNo: 1, pageSize: 10 });
const alertRows = ref<QualificationApi.Alert[]>([]);
const alertTotal = ref(0);
const alertLoading = ref(false);
const alertColumns = [
  { title: '触发时间', key: 'triggeredAt', width: 165 },
  { title: '来源', dataIndex: 'refType', key: 'refType', width: 130 },
  { title: '来源对象', dataIndex: 'refName', key: 'refName', width: 220 },
  { title: '预警类型', dataIndex: 'alertType', key: 'alertType', width: 140 },
  { title: '级别', key: 'alertLevel', width: 100 },
  { title: '内容', dataIndex: 'alertMessage', key: 'alertMessage', ellipsis: true },
  { title: '通知', key: 'notify', width: 150 },
  { title: '已处理', key: 'handled', width: 110 },
  { title: '处理说明', dataIndex: 'handleNote', key: 'handleNote', width: 160, ellipsis: true },
  { title: '操作', key: 'operation', width: 110, fixed: 'right' },
];
const handledOptions = [
  { label: '未处理', value: false },
  { label: '已处理', value: true },
];

async function loadAlerts() {
  alertLoading.value = true;
  try {
    const page = await getQualificationAlertPage({ ...alertQuery, entId: query.entId });
    alertRows.value = page.list ?? [];
    alertTotal.value = page.total ?? 0;
  } catch {
    // 静默
  } finally {
    alertLoading.value = false;
  }
}
function onAlertPage(p: number) {
  alertQuery.pageNo = p;
  void loadAlerts();
}

/* ===== 预警处理（销账）=====
 * 权限：与"执行一次扫描"同码 bidding:qualification:alert-run（后端 QualificationController#handleAlert 同码；
 *   前端未擅自新登记权限码——那属于权限模型决策，需同时改权限表与菜单行 SQL）。
 * CAS：version 取自清单行，后端不一致即拒绝，说明已被他人处理；失败后**只重拉清单**，
 *   不自动重试（重试会覆盖他人处理备注）。 */
const alertHandleOpen = ref(false);
const alertHandleRow = ref<null | QualificationApi.Alert>(null);
const alertHandleNote = ref('');
const alertHandleSaving = ref(false);

function openAlertHandle(row: QualificationApi.Alert) {
  alertHandleRow.value = row;
  alertHandleNote.value = '';
  alertHandleOpen.value = true;
}

async function submitAlertHandle() {
  const row = alertHandleRow.value;
  if (!row) return;
  // 版本号是 CAS 的前置条件：拿不到就**明确拒绝**，绝不发一个假版本（要么恒冲突、要么绕过并发控制）
  const version = row.version;
  if (version === undefined || version === null) {
    message.warning('该预警未下发版本号，无法处理：请刷新清单，仍无版本号请联系后端确认 version 是否下发');
    return;
  }
  alertHandleSaving.value = true;
  try {
    await handleQualificationAlert({
      alertId: row.id,
      handleNote: alertHandleNote.value.trim() || undefined,
      version,
    });
    message.success('预警已标记为处理');
    alertHandleOpen.value = false;
    await loadAlerts();
    refreshAfterWrite();
  } catch {
    // 后端错误由请求层 toast（含 CAS 冲突的提示）；此处重拉清单让用户看到"被别人处理过"的真实状态
    alertHandleOpen.value = false;
    await loadAlerts();
  } finally {
    alertHandleSaving.value = false;
  }
}

/* ===== 页签懒加载 + 主体切换全页重拉（契约 §2：页签切换不重拉已加载数据；主体切换全页重拉） ===== */
const noQueryPerm = computed(() => !can('bidding:qualification:query'));

watch(activeKey, (k) => {
  if (k === 'standard') {
    void loadStdOptions();
    if (!stdLoaded.value) {
      void loadStd();
      void loadElements();
      void loadReviews();
    }
  }
  if (k === 'safety' && !permitLoaded.value) {
    void loadPermits();
    void loadAlerts();
  }
});

watch(
  () => query.entId,
  () => {
    elemQualId.value = undefined;
    stdLoaded.value = false;
    permitLoaded.value = false;
    applyQuery.entId = query.entId;
    refreshAfterWrite();
  },
);

onMounted(() => {
  void load();
  void loadApplies();
});
</script>

<template>
  <!-- 契约 §1.1 ①：页面本身无 query 权限时显示无权限提示，而不是空白页 -->
  <div v-if="noQueryPerm" class="ct-page">
    <a-result
      status="403"
      title="无权限访问"
      sub-title="当前账号缺少 bidding:qualification:query；请联系管理员在「电子资质」菜单下授权。"
    />
  </div>
  <div v-else class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">企业资质</div>
        <div class="ct-sub">资质台账 · 延续申报审批 · 标准与要素台账 · 安许证与预警 · 完备性自查</div>
      </div>
      <div class="ct-actions">
        <a-input-number v-model:value="query.entId" :min="1" placeholder="企业主体 id（entId）" style="width: 200px" />
        <a-button v-if="can('bidding:qualification:material:query')" :loading="completenessLoading" @click="loadCompleteness()">
          材料完备性自查
        </a-button>
        <a-button v-if="can('bidding:qualification:create')" type="primary" @click="onCreate">资质建档</a-button>
        <a-button v-if="can('bidding:qualification:permit')" @click="openPermit">安许建档</a-button>
        <a-button v-if="can('bidding:qualification:alert-run')" :loading="scanning" @click="doScan">执行预警扫描</a-button>
        <a-button @click="load(); loadApplies();">刷新</a-button>
      </div>
    </div>

    <a-alert type="info" show-icon class="mb-3">
      <template #message>字典口径：状态 / 申报类型 / 序列已注册字典，等级按标准库候选与原码展示</template>
      <template #description>
        <div>
          已注册（中文名 + 标签色）：<code>qual_status</code> 台账状态、<code>qual_apply_type</code> / <code>qual_apply_status</code> 申报类型与状态、
          <code>qual_serial</code> 序列、<code>material_result / material_status / material_category</code>、
          <code>safety_permit_status</code>、<code>safety_risk_flag</code>、<code>risk_level</code>。
          <code>qual_grade</code> <strong>不是字典</strong>：其值域由现行标准库数据决定（后端接口口径：
          <code>/bidding/qualification/standard/options</code> 取自标准库、不固化字典），故原样展示；
          登记成字典反而会制造第二套编码。
          本页一律字典优先、缺字典回落 code（<strong>不硬编码中文枚举</strong>）。
        </div>
        <div style="margin-top: 6px">
          已接入：台账列表/详情、延续申报与审批、资质标准登记与台账（序列/类别/等级候选取自 /standard/options）、
          要素录入与要素台账（人员·业绩·资产装备三张表）、达标核查执行与核查历史、安许建档·台账·状态流转、
          到期预警清单与手动扫描、材料完备性自查。
          仍缺接口：资质主档<strong>编辑</strong>（无 update，create 只走 insert）、
          资质<strong>主档状态流转</strong>（Service 有 changeStatus 但无 Controller 暴露）、
          要素记录的<strong>编辑/删除</strong>（仅 create + page）。
          预警<strong>处理闭环已接通</strong>（POST /bidding/qualification/alert/handle，版本 CAS；处理人/时间/说明见清单列）。
        </div>
      </template>
    </a-alert>

    <!-- 顶部常驻：材料完备性结论卡片（契约 §2：不随页签隐藏；写操作成功后与页签同时失效重拉） -->
    <div class="ct-panel mb-3">
      <div class="ct-panel-t">材料完备性结论</div>
      <div class="ct-panel-b">
        <a-space class="mb-2" wrap>
          <a-date-picker
            v-model:value="completenessQuery.asOf"
            placeholder="核验基准日（可空=今天）"
            value-format="YYYY-MM-DD"
            style="width: 200px"
          />
          <a-button
            v-if="can('bidding:qualification:material:query')"
            type="primary"
            :loading="completenessLoading"
            @click="loadCompleteness()"
          >
            自查
          </a-button>
          <span class="ct-panel-meta">可填未来日期，预演「投标截止日是否仍有效」；逐项明细见页签 ⑤</span>
        </a-space>
        <a-spin :spinning="completenessLoading">
          <a-alert v-if="completenessError" type="error" show-icon :message="completenessError" />
          <a-space v-else-if="completeness" wrap>
            <a-tag :color="resultTag.color">结论：{{ resultTag.label }}</a-tag>
            <a-tag>必备材料 {{ completeness.readyCount }} / {{ completeness.requiredCount }}</a-tag>
            <a-tag :color="(completeness.missingItems?.length ?? 0) > 0 ? 'red' : 'default'">
              缺失/过期 {{ completeness.missingItems?.length ?? 0 }} 项
            </a-tag>
            <a-tag :color="(completeness.expiringItems?.length ?? 0) > 0 ? 'orange' : 'default'">
              临近到期 {{ completeness.expiringItems?.length ?? 0 }} 项
            </a-tag>
          </a-space>
          <div v-else class="ct-empty">填入企业主体 id 后点「自查」：返回身份类 / 资质类 / 实力类三档结论</div>
        </a-spin>
      </div>
    </div>

    <a-tabs v-model:active-key="activeKey">
      <a-tab-pane key="ledger" tab="① 资质台账">
        <div class="ct-kpis">
          <div class="ct-kpi k-blue">
            <div class="k-label">资质总数</div>
            <div class="k-num">{{ total }}</div>
            <div class="k-note">按当前筛选条件</div>
          </div>
          <div class="ct-kpi k-green">
            <div class="k-label">本页有效</div>
            <div class="k-num">{{ kpi.valid }}</div>
            <div class="k-note">qualStatus=VALID</div>
          </div>
          <div class="ct-kpi">
            <div class="k-label">本页在途申报</div>
            <div class="k-num">{{ kpi.pendingApply }}</div>
            <div class="k-note">hasPendingApply</div>
          </div>
          <div class="ct-kpi k-amber">
            <div class="k-label">本页 30 天内到期</div>
            <div class="k-num">{{ kpi.soon }}</div>
            <div class="k-note">0 ≤ daysToExpire ≤ 30</div>
          </div>
        </div>

        <div class="ct-panel">
          <div class="ct-filter">
            <span class="ct-panel-meta">主体在页首选择 →</span>
            <a-select
              v-model:value="query.qualSerial"
              placeholder="资质序列"
              style="width: 170px"
              allow-clear
              :options="QualificationApi.QUAL_SERIAL_OPTIONS"
              @change="load"
            />
            <a-input v-model:value="query.qualStatus" placeholder="状态 code（如 VALID）" style="width: 180px" allow-clear @press-enter="load" />
            <a-input v-model:value="query.keyword" placeholder="关键字（证书编号/机关）" style="width: 200px" allow-clear @press-enter="load" />
            <a-input-number v-model:value="query.expiringInDays" :min="1" placeholder="N 天内到期" style="width: 140px" />
            <a-select
              v-model:value="reviewType"
              placeholder="核查类型"
              style="width: 170px"
              :options="QualificationApi.REVIEW_TYPE_OPTIONS"
            />
            <a-button type="primary" @click="load">查询</a-button>
            <a-button @click="resetQuery">重置</a-button>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="ledgerColumns"
              :data-source="rows"
              :loading="loading"
              :pagination="{
                current: query.pageNo,
                pageSize: query.pageSize,
                total,
                showSizeChanger: true,
                showTotal: (t: number) => `共 ${t} 条`,
              }"
              :scroll="{ x: 1450 }"
              row-key="id"
              size="middle"
              @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'qualSerial'">{{ textOf('qual_serial', record.qualSerial) }}</template>
                <template v-else-if="column.key === 'qualStatus'">
                  <a-tag :color="tagOf('qual_status', record.qualStatus).color">
                    {{ tagOf('qual_status', record.qualStatus).label }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'validTo'">{{ record.validTo ? fmtDate(record.validTo) : '长期' }}</template>
                <template v-else-if="column.key === 'daysToExpire'">
                  <a-tag v-if="record.daysToExpire !== null && record.daysToExpire !== undefined" :color="daysColor(record.daysToExpire)">
                    {{ record.daysToExpire }} 天
                  </a-tag>
                  <span v-else>—</span>
                </template>
                <template v-else-if="column.key === 'hasPendingApply'">
                  <a-tag v-if="record.hasPendingApply" color="processing">在途</a-tag>
                  <span v-else>—</span>
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space size="small">
                    <a @click="openDetail(record)">详情</a>
                    <a v-if="can('bidding:qualification:renew')" @click="openRenew(record)">延续申报</a>
                    <a v-if="can('bidding:qualification:element')" @click="openElement(record)">要素录入</a>
                    <a v-if="can('bidding:qualification:review')" @click="confirmReview(record)">达标核查</a>
                  </a-space>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">暂无资质记录：填企业主体 id 查询，或点右上「资质建档」</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="apply" tab="② 延续申报台账">
        <div class="ct-panel">
          <div class="ct-filter">
            <span class="ct-panel-meta">主体在页首选择 →</span>
            <a-input-number v-model:value="applyQuery.qualId" :min="1" placeholder="资质 id" style="width: 150px" />
            <a-select
              v-model:value="applyQuery.applyType"
              placeholder="申报类型"
              style="width: 150px"
              allow-clear
              :options="APPLY_TYPE_OPTIONS"
              @change="loadApplies"
            />
            <a-select v-model:value="applyQuery.status" placeholder="申报状态" style="width: 160px" allow-clear :options="APPLY_STATUS_OPTIONS" @change="loadApplies" />
            <a-button type="primary" @click="loadApplies">查询</a-button>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="applyColumns"
              :data-source="applyRows"
              :loading="applyLoading"
              :pagination="{
                current: applyQuery.pageNo,
                pageSize: applyQuery.pageSize,
                total: applyTotal,
                showSizeChanger: true,
                showTotal: (t: number) => `共 ${t} 条`,
              }"
              :scroll="{ x: 1650 }"
              row-key="id"
              size="middle"
              @change="(p: any) => { applyQuery.pageNo = p.current; applyQuery.pageSize = p.pageSize; void loadApplies(); }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'applyType'">{{ textOf('qual_apply_type', record.applyType) }}</template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="tagOf('qual_apply_status', record.status).color">
                    {{ tagOf('qual_apply_status', record.status).label }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'submitDate'">{{ record.submitDate ? fmtDate(record.submitDate) : '—' }}</template>
                <template v-else-if="column.key === 'dueDate'">{{ record.dueDate ? fmtDate(record.dueDate) : '—' }}</template>
                <template v-else-if="column.key === 'newValidTo'">
                  {{ record.newValidTo ? fmtDate(record.newValidTo) : '—' }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space v-if="auditable(record)" size="small">
                    <a v-if="can('bidding:qualification:approve')" @click="openApprove(record)">审批通过</a>
                    <a v-if="can('bidding:qualification:reject')" style="color: #c6362e" @click="confirmReject(record)">退回</a>
                  </a-space>
                  <span v-else class="ct-panel-meta">已终态</span>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">无申报单（在「资质台账」对某条资质点「延续申报」即可发起）</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="standard" tab="③ 标准与要素">
        <div class="ct-panel">
          <div class="ct-panel-t">资质标准（序列 / 类别 / 等级 → 人员·业绩·资产要求）</div>
          <div class="ct-filter">
            <a-select
              v-model:value="stdQuery.qualSerial"
              placeholder="序列"
              allow-clear
              :options="stdSerialOptions"
              style="width: 150px"
              @change="stdQuery.qualCategory = undefined; stdQuery.qualGrade = undefined"
            />
            <a-select
              v-model:value="stdQuery.qualCategory"
              placeholder="类别"
              allow-clear
              :options="stdCategoryOptions"
              style="width: 150px"
              @change="stdQuery.qualGrade = undefined"
            />
            <a-select
              v-model:value="stdQuery.qualGrade"
              placeholder="等级"
              allow-clear
              :options="stdGradeOptions"
              style="width: 130px"
            />
            <a-select
              v-model:value="stdQuery.stdVersion"
              placeholder="标准版本"
              allow-clear
              :options="stdVersionOptions"
              style="width: 150px"
            />
            <a-checkbox v-model:checked="stdQuery.onlyEffective">仅生效中</a-checkbox>
            <a-button type="primary" @click="loadStd">查询</a-button>
            <a-button v-if="can('bidding:qualification:standard')" @click="openStandard">标准登记</a-button>
            <span class="ct-panel-meta">候选来自 /standard/options，不硬编码枚举</span>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="stdColumns"
              :data-source="stdRows"
              :loading="stdLoading"
              :pagination="{
                current: stdQuery.pageNo,
                pageSize: stdQuery.pageSize,
                total: stdTotal,
                showSizeChanger: false,
                onChange: onStdPage,
              }"
              row-key="id"
              size="small"
              :scroll="{ x: 1500 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'isEffective'">
                  <a-tag :color="record.isEffective ? 'green' : 'default'">
                    {{ record.isEffective ? '生效中' : '已失效' }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'effectiveTo'">{{ record.effectiveTo || '长期' }}</template>
              </template>
              <template #emptyText><div class="ct-empty">按序列/类别/等级筛选，或先登记一条标准</div></template>
            </a-table>
          </div>
        </div>

        <div class="ct-panel mt-3">
          <div class="ct-panel-t">要素台账（人员配置 / 工程业绩 / 资产技术装备）</div>
          <div class="ct-filter">
            <a-input-number
              v-model:value="elemQualId"
              :min="1"
              placeholder="资质 id（qualId，可空=按主体查全部）"
              style="width: 290px"
            />
            <a-button type="primary" @click="loadElements">查询</a-button>
            <a-radio-group v-model:value="elemTabKey" button-style="solid" @change="switchElemTab">
              <a-radio-button value="staff">人员配置</a-radio-button>
              <a-radio-button value="performance">工程业绩</a-radio-button>
              <a-radio-button value="asset">资产/技术装备</a-radio-button>
            </a-radio-group>
            <span class="ct-panel-meta">录入入口在「① 资质台账」行内「要素录入」</span>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="elemColumns"
              :data-source="elemRows"
              :loading="elemLoading"
              :pagination="{
                current: elemPage.pageNo,
                pageSize: elemPage.pageSize,
                total: elemTotal,
                showSizeChanger: false,
                onChange: onElemPage,
              }"
              row-key="id"
              size="small"
              :scroll="{ x: 1400 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'isFullTime'">
                  <a-tag :color="record.isFullTime ? 'green' : 'default'">
                    {{ record.isFullTime ? '全职' : '非全职' }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'isPlatformVerified'">
                  <a-tag :color="record.isPlatformVerified ? 'green' : 'default'">
                    {{ record.isPlatformVerified ? '可核验' : '未核验' }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'eduHours'">
                  {{ record.eduHours ?? '—' }} / {{ record.eduHoursRequired ?? '—' }}
                </template>
                <template v-else-if="column.key === 'bindedCertId'">{{ record.bindedCertId ?? '—' }}</template>
              </template>
              <template #emptyText><div class="ct-empty">该主体/资质下暂无要素记录</div></template>
            </a-table>
          </div>
        </div>

        <div class="ct-panel mt-3">
          <div class="ct-panel-t">达标核查历史</div>
          <div class="ct-filter">
            <a-input-number
              v-model:value="reviewQuery.qualId"
              :min="1"
              placeholder="资质 id（qualId，可空）"
              style="width: 200px"
            />
            <a-select
              v-model:value="reviewQuery.reviewResult"
              placeholder="核查结论"
              allow-clear
              :options="reviewResultOptions"
              style="width: 140px"
            />
            <a-button type="primary" @click="loadReviews">查询</a-button>
            <span class="ct-panel-meta">结论在「① 台账」行内「达标核查」执行后产生</span>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="reviewColumns"
              :data-source="reviewRows"
              :loading="reviewLoading"
              :pagination="{
                current: reviewQuery.pageNo,
                pageSize: reviewQuery.pageSize,
                total: reviewTotal,
                showSizeChanger: false,
                onChange: onReviewPage,
              }"
              row-key="id"
              size="small"
              :scroll="{ x: 1300 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'reviewedAt'">{{ fmtDateTime(record.reviewedAt) }}</template>
                <template v-else-if="column.key === 'reviewResult'">
                  <a-tag :color="reviewResultTag(record.reviewResult).color">
                    {{ reviewResultTag(record.reviewResult).label }}
                  </a-tag>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">暂无核查记录</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="safety" tab="④ 安许证与预警">
        <div class="ct-panel">
          <div class="ct-panel-t">安许证台账</div>
          <div class="ct-filter">
            <a-select
              v-model:value="permitQuery.permitStatus"
              placeholder="状态"
              allow-clear
              :options="PERMIT_STATUS_OPTIONS"
              style="width: 150px"
            />
            <a-input-number
              v-model:value="permitQuery.expiringInDays"
              :min="1"
              placeholder="N 天内到期"
              style="width: 140px"
            />
            <a-input v-model:value="permitQuery.keyword" placeholder="编号 / 法人关键字" style="width: 190px" />
            <a-button type="primary" @click="loadPermits">查询</a-button>
            <a-button v-if="can('bidding:qualification:permit')" @click="openPermit">安许建档</a-button>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="permitColumns"
              :data-source="permitRows"
              :loading="permitLoading"
              :pagination="{
                current: permitQuery.pageNo,
                pageSize: permitQuery.pageSize,
                total: permitTotal,
                showSizeChanger: false,
                onChange: onPermitPage,
              }"
              row-key="id"
              size="small"
              :scroll="{ x: 1500 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'daysToExpire'">
                  <span :style="{ color: daysColor(record.daysToExpire) }">
                    {{ record.daysToExpire ?? '长期' }}
                  </span>
                </template>
                <template v-else-if="column.key === 'permitStatus'">
                  <a-tag :color="dictOptions('safety_permit_status').length > 0 ? dictTag('safety_permit_status', record.permitStatus).color : 'default'">
                    {{ dictOptions('safety_permit_status').length > 0 ? dictLabel('safety_permit_status', record.permitStatus) : record.permitStatus }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'riskFlag'">
                  <a-tag v-if="record.riskFlag" :color="dictOptions('safety_risk_flag').length > 0 ? dictTag('safety_risk_flag', record.riskFlag).color : 'default'">
                    {{ dictOptions('safety_risk_flag').length > 0 ? dictLabel('safety_risk_flag', record.riskFlag) : record.riskFlag }}
                  </a-tag>
                  <span v-else>未评估</span>
                </template>
                <template v-else-if="column.key === 'graceExpireAt'">
                  {{ record.graceExpireAt ? fmtDateTime(record.graceExpireAt) : '—' }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a
                    v-if="can('bidding:qualification:permit') && (PERMIT_TRANSITIONS[record.permitStatus] ?? []).length > 0"
                    @click="openPermitStatus(record)"
                  >
                    状态流转
                  </a>
                  <span v-else class="ct-panel-meta">—</span>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">该主体下暂无安许证，可点「安许建档」新增</div></template>
            </a-table>
          </div>
        </div>

        <div class="ct-panel mt-3">
          <div class="ct-panel-t">到期 / 风险预警清单（QUAL / SAFETY_PERMIT / STAFF 三源）</div>
          <div class="ct-filter">
            <a-select
              v-model:value="alertQuery.refType"
              placeholder="来源类型"
              allow-clear
              :options="QualificationApi.ALERT_REF_TYPE_OPTIONS"
              style="width: 180px"
            />
            <a-select
              v-model:value="alertQuery.alertLevel"
              placeholder="级别"
              allow-clear
              :options="dictOptions('risk_level')"
              style="width: 140px"
            />
            <a-select
              v-model:value="alertQuery.handled"
              placeholder="处理状态"
              allow-clear
              :options="handledOptions"
              style="width: 140px"
            />
            <a-button type="primary" @click="loadAlerts">查询</a-button>
            <a-button v-if="can('bidding:qualification:alert-run')" :loading="scanning" @click="doScan">
              执行一次扫描
            </a-button>
            <span class="ct-panel-meta">同日幂等（refType:refId:yyyyMMdd）</span>
          </div>
          <div class="ct-panel-b">
            <div v-if="scanHits !== null" class="mb-2">
              <a-tag color="processing">本轮命中 {{ scanHits }} 条</a-tag>
              <span class="ct-panel-meta" style="margin-left: 8px">执行时间 {{ fmtDateTime(scanAt) }}</span>
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
                showSizeChanger: false,
                onChange: onAlertPage,
              }"
              row-key="id"
              size="small"
              :scroll="{ x: 1400 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'triggeredAt'">{{ fmtDateTime(record.triggeredAt) }}</template>
                <template v-else-if="column.key === 'alertLevel'">
                  <a-tag :color="dictOptions('risk_level').length > 0 ? dictTag('risk_level', record.alertLevel).color : 'default'">
                    {{ dictOptions('risk_level').length > 0 ? dictLabel('risk_level', record.alertLevel) : record.alertLevel }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'notify'">
                  {{ record.notifyChannel || '—' }}
                  <span v-if="record.notifyStatus" class="ct-panel-meta">（{{ record.notifyStatus }}）</span>
                </template>
                <template v-else-if="column.key === 'handled'">
                  <a-tag :color="record.handled ? 'green' : 'orange'">
                    {{ record.handled ? '已处理' : '未处理' }}
                  </a-tag>
                  <span v-if="record.handled && (record.handledBy || record.handledAt)" class="ct-panel-meta">
                    {{ record.handledBy ? `#${record.handledBy}` : '' }}{{ record.handledAt ? ` ${fmtDateTime(record.handledAt)}` : '' }}
                  </span>
                </template>
                <template v-else-if="column.key === 'operation'">
                  <a-button
                    v-if="!record.handled && can('bidding:qualification:alert-run')"
                    size="small"
                    type="link"
                    @click="openAlertHandle(record)"
                  >
                    标记已处理
                  </a-button>
                  <span v-else>—</span>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">暂无预警记录，可点「执行一次扫描」</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="material" tab="⑤ 完备性明细与扫描">
        <div class="ct-panel">
          <div class="ct-filter">
            <a-input-number v-model:value="completenessQuery.entId" :min="1" placeholder="企业主体 id（entId）" style="width: 200px" />
            <a-date-picker
              v-model:value="completenessQuery.asOf"
              placeholder="核验基准日（可空=今天）"
              value-format="YYYY-MM-DD"
              style="width: 210px"
            />
            <a-button
              v-if="can('bidding:qualification:material:query')"
              type="primary"
              :loading="completenessLoading"
              @click="loadCompleteness"
            >
              自查
            </a-button>
            <span class="ct-panel-meta">可填未来日期，预演「投标截止日是否仍有效」</span>
          </div>
          <div class="ct-panel-b">
            <a-spin :spinning="completenessLoading">
              <a-alert v-if="completenessError" type="error" show-icon :message="completenessError" />
              <template v-else-if="completeness">
                <a-descriptions :column="3" size="small" bordered class="mb-3">
                  <a-descriptions-item label="企业主体">{{ completeness.entId }}</a-descriptions-item>
                  <a-descriptions-item label="基准日">{{ completenessQuery.asOf || '今天' }}</a-descriptions-item>
                  <a-descriptions-item label="结论">
                    <a-tag :color="resultTag.color">{{ resultTag.label }}</a-tag>
                  </a-descriptions-item>
                  <a-descriptions-item label="必备材料">
                    {{ completeness.readyCount }} / {{ completeness.requiredCount }} 已齐备
                  </a-descriptions-item>
                  <a-descriptions-item label="缺失/过期">{{ completeness.missingItems?.length ?? 0 }} 项</a-descriptions-item>
                  <a-descriptions-item label="临近到期">
                    {{ completeness.expiringItems?.length ?? 0 }} 项
                  </a-descriptions-item>
                </a-descriptions>

                <a-alert
                  v-if="completeness.missingItems?.length"
                  class="mb-2"
                  type="error"
                  show-icon
                  :message="`缺失/过期 ${completeness.missingItems.length} 项`"
                  :description="completeness.missingItems.join('、')"
                />
                <a-alert
                  v-if="completeness.expiringItems?.length"
                  class="mb-3"
                  type="warning"
                  show-icon
                  :message="`临近到期 ${completeness.expiringItems.length} 项`"
                  :description="completeness.expiringItems.join('、')"
                />

                <a-table
                  class="ct-table"
                  :columns="itemColumns"
                  :data-source="completeness.items ?? []"
                  :pagination="false"
                  row-key="materialType"
                  size="small"
                  :scroll="{ x: 1250 }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'category'">
                      {{ dictLabel('material_category', record.category) }}
                    </template>
                    <template v-else-if="column.key === 'mandatory'">
                      <a-tag :color="record.mandatory ? 'red' : 'default'">
                        {{ record.mandatory ? '必备' : '非必备' }}
                      </a-tag>
                    </template>
                    <template v-else-if="column.key === 'status'">
                      <a-tag :color="dictTag('material_status', record.status).color">
                        {{ dictTag('material_status', record.status).label }}
                      </a-tag>
                    </template>
                    <template v-else-if="column.key === 'validTo'">{{ record.validTo || '长期' }}</template>
                  </template>
                  <template #emptyText><div class="ct-empty">无逐项明细</div></template>
                </a-table>
              </template>
              <div v-else class="ct-empty">输入企业主体 id 后点「自查」，返回身份类 / 资质类 / 实力类三档结论</div>
            </a-spin>
          </div>
        </div>

        <div class="ct-panel mt-3">
          <div class="ct-panel-t">资质到期预警扫描</div>
          <div class="ct-panel-b">
            <a-space>
              <a-button v-if="can('bidding:qualification:alert-run')" :loading="scanning" @click="doScan">
                执行一次 QUAL_EXPIRY 扫描
              </a-button>
              <span class="ct-panel-meta">同日幂等（后端 @Idempotent，按 LocalDate.now() 扫三源）</span>
            </a-space>
            <div v-if="scanHits !== null" class="mt-3">
              <a-tag color="processing">命中 {{ scanHits }} 条</a-tag>
              <span class="ct-panel-meta" style="margin-left: 8px">执行时间 {{ fmtDateTime(scanAt) }}</span>
            </div>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 详情抽屉 -->
    <a-drawer v-model:open="detailOpen" class="bid-ind" :width="820" title="资质详情" destroy-on-close>
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-descriptions :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item label="资质 id">{{ detail.id }}</a-descriptions-item>
            <a-descriptions-item label="企业主体">{{ detail.entId }}</a-descriptions-item>
            <a-descriptions-item label="证书编号">{{ detail.certNo }}</a-descriptions-item>
            <a-descriptions-item label="序列">{{ textOf('qual_serial', detail.qualSerial) }}</a-descriptions-item>
            <a-descriptions-item label="类别">{{ detail.qualCategory }}</a-descriptions-item>
            <a-descriptions-item label="等级">{{ detail.qualGrade }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="tagOf('qual_status', detail.qualStatus).color">
                {{ tagOf('qual_status', detail.qualStatus).label }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="发证机关">{{ detail.issuingAuthority || '—' }}</a-descriptions-item>
            <a-descriptions-item label="发证日期">{{ detail.issueDate ? fmtDate(detail.issueDate) : '—' }}</a-descriptions-item>
            <a-descriptions-item label="有效期至">{{ detail.validTo ? fmtDate(detail.validTo) : '长期' }}</a-descriptions-item>
            <a-descriptions-item label="剩余天数">{{ detail.daysToExpire ?? '—' }}</a-descriptions-item>
            <a-descriptions-item label="版本（乐观锁）">{{ detail.version ?? '—' }}</a-descriptions-item>
            <a-descriptions-item label="来源渠道">{{ detail.sourceChannel || '—' }}</a-descriptions-item>
            <a-descriptions-item label="在途申报">{{ detail.hasPendingApply ? '有' : '无' }}</a-descriptions-item>
            <a-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</a-descriptions-item>
          </a-descriptions>

          <div class="ct-panel-t mb-2">绑定证照（bindedCertId={{ detail.bindedCertId ?? '—' }}）</div>
          <a-descriptions :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item label="证照名称">{{ detail.bindedCertName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="证照编号">{{ detail.bindedCertNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="证照状态">{{ detail.bindedCertStatus || '—' }}</a-descriptions-item>
          </a-descriptions>

          <div class="ct-panel-t mb-2">最近一次达标核查</div>
          <a-descriptions v-if="detail.latestReview" :column="2" size="small" bordered>
            <a-descriptions-item label="标准版本">{{ detail.latestReview.stdVersion || '—' }}</a-descriptions-item>
            <a-descriptions-item label="结论">
              <a-tag :color="tagOf('review_result', detail.latestReview.reviewResult).color">
                {{ tagOf('review_result', detail.latestReview.reviewResult).label }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="核查时间">{{ fmtDateTime(detail.latestReview.reviewedAt) }}</a-descriptions-item>
            <a-descriptions-item label="差异项">{{ detail.latestReview.diffItems || '—' }}</a-descriptions-item>
          </a-descriptions>
          <div v-else class="ct-tip">尚无达标核查记录（可先执行 /bidding/qualification/review/run）</div>
        </template>
        <div v-else-if="!detailLoading" class="ct-empty">未取到详情</div>
      </a-spin>
    </a-drawer>

    <!-- 预警处理（销账）-->
    <a-modal
      v-model:open="alertHandleOpen"
      :confirm-loading="alertHandleSaving"
      title="标记预警已处理"
      :width="560"
      @ok="submitAlertHandle"
    >
      <div class="mb-2">
        <div class="ct-panel-meta">预警</div>
        <div>{{ alertHandleRow?.refName || '—' }} · {{ alertHandleRow?.alertMessage || '—' }}</div>
      </div>
      <a-textarea
        v-model:value="alertHandleNote"
        :rows="3"
        maxlength="200"
        placeholder="处理说明（选填，例：已换证，新证书编号 XXX）"
        show-count
      />
      <div class="ct-tip">
        处理含版本校验：若该预警已被他人处理，本次提交会被拒绝且<strong>不会覆盖他人的处理说明</strong>，
        页面会自动刷新显示最新状态。
      </div>
    </a-modal>

    <!-- 延续申报 -->
    <a-modal
      v-model:open="renewOpen"
      :title="`延续申报：${renewRow?.certNo ?? ''}`"
      :width="620"
      :confirm-loading="renewSaving"
      ok-text="提交申报"
      cancel-text="取消"
      destroy-on-close
      @ok="submitRenew"
    >
      <a-descriptions :column="1" size="small" class="mb-3">
        <a-descriptions-item label="资质 id / version">
          {{ renewForm.qualificationId }} / {{ renewForm.version }}
        </a-descriptions-item>
        <a-descriptions-item label="当前有效期至">
          {{ renewRow?.validTo ? fmtDate(renewRow.validTo) : '长期' }}
        </a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical">
        <a-form-item label="申请延续至（可空，由审批时确定）">
          <a-date-picker v-model:value="renewForm.dueDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="申报说明">
          <a-textarea v-model:value="renewForm.remark" :rows="3" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">提交后主档置 PENDING_RENEW、生成 SUBMITTED 申报单；同一资质存在在途申报时请勿重复提交。</div>
    </a-modal>

    <!-- 审批通过 -->
    <a-modal
      v-model:open="approveOpen"
      :title="`审批通过：申报单 #${approveForm.applyId}`"
      :width="620"
      :confirm-loading="approveSaving"
      ok-text="通过并回写主档"
      cancel-text="取消"
      destroy-on-close
      @ok="submitApprove"
    >
      <a-form ref="approveFormRef" :model="approveForm" :rules="approveRules" layout="vertical">
        <a-form-item label="延续后有效期（必填）" name="newValidTo">
          <a-date-picker v-model:value="approveForm.newValidTo" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="结果文号">
          <a-input v-model:value="approveForm.resultNo" />
        </a-form-item>
        <a-form-item label="新证书编号">
          <a-input v-model:value="approveForm.newCertNo" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">审批通过后：申报单 APPROVED→ISSUED，主档回写 VALID 与 new_valid_to。</div>
    </a-modal>

    <!-- 资质建档 -->
    <a-modal
      v-model:open="formOpen"
      title="资质建档"
      :width="860"
      :confirm-loading="saving"
      ok-text="建档"
      cancel-text="取消"
      destroy-on-close
      @ok="submit"
    >
      <a-alert
        v-if="createdId"
        type="success"
        show-icon
        class="mb-3"
        :message="`已创建，资质 id=${createdId}`"
      />
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="企业主体 id（entId）" name="entId">
              <a-input-number v-model:value="form.entId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="资质序列" name="qualSerial">
              <a-select v-model:value="form.qualSerial" :options="QualificationApi.QUAL_SERIAL_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="资质证书编号" name="certNo">
              <a-input v-model:value="form.certNo" placeholder="租户内唯一（UK 判重）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="资质类别" name="qualCategory">
              <a-input v-model:value="form.qualCategory" placeholder="如：市政公用工程施工总承包" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="资质等级" name="qualGrade">
              <a-input v-model:value="form.qualGrade" placeholder="如：一级（对齐国标表110）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发证/许可机关" name="issuingAuthority">
              <a-input v-model:value="form.issuingAuthority" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="发证日期" name="issueDate">
              <a-date-picker v-model:value="form.issueDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="有效期至（空=长期）" name="validTo">
              <a-date-picker v-model:value="form.validTo" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="关联证照（certificate_info id）" name="bindedCertId">
              <a-input-number
                v-model:value="form.bindedCertId"
                :min="1"
                style="width: 100%"
                placeholder="可空；存在性由后端校验"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="批准承包/承揽范围" name="approvalScope">
              <a-input v-model:value="form.approvalScope" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <!-- 资质标准登记 -->
    <a-modal
      v-model:open="stdFormOpen"
      title="资质标准登记"
      :width="840"
      :confirm-loading="stdSaving"
      ok-text="登记"
      cancel-text="取消"
      destroy-on-close
      @ok="submitStandard"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="序列（必填）">
              <a-select
                v-model:value="stdForm.qualSerial"
                :options="stdSerialOptions"
                @change="stdForm.qualCategory = ''; stdForm.qualGrade = ''"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="类别（必填）">
              <a-select
                v-model:value="stdForm.qualCategory"
                :options="stdCategoryOptions"
                @change="stdForm.qualGrade = ''"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="等级（必填）">
              <a-select v-model:value="stdForm.qualGrade" :options="stdGradeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="标准版本（必填）"><a-input v-model:value="stdForm.stdVersion" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="生效起">
              <a-date-picker v-model:value="stdForm.effectiveFrom" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="生效止（空=长期）">
              <a-date-picker v-model:value="stdForm.effectiveTo" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="人员要求"><a-textarea v-model:value="stdForm.reqStaff" :rows="2" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="业绩要求"><a-textarea v-model:value="stdForm.reqPerformance" :rows="2" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="资产要求"><a-textarea v-model:value="stdForm.reqAssets" :rows="2" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="设备要求"><a-textarea v-model:value="stdForm.reqEquipment" :rows="2" /></a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="来源依据">
              <a-input v-model:value="stdForm.sourceRef" placeholder="如：建市〔2014〕159 号《建筑业企业资质标准》" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">
        序列 / 类别 / 等级候选来自 <code>/standard/options</code>；同一「序列+类别+等级+版本」重复登记由后端判重（§0.7）。
      </div>
    </a-modal>

    <!-- 安许状态流转（permitId + version 乐观锁；目标状态只列 SafetyPermitStatusEnum 允许的迁移） -->
    <a-modal
      v-model:open="permitStatusOpen"
      title="安许状态流转"
      :width="640"
      :confirm-loading="permitStatusSaving"
      ok-text="提交"
      cancel-text="取消"
      destroy-on-close
      @ok="submitPermitStatus"
    >
      <a-descriptions :column="2" size="small" class="mb-3">
        <a-descriptions-item label="许可证编号">{{ permitStatusRow?.permitNo ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="当前状态">{{ permitStatusRow?.permitStatus ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="乐观锁 version">{{ permitStatusForm.version }}</a-descriptions-item>
        <a-descriptions-item label="企业主体">{{ permitStatusRow?.entId ?? '—' }}</a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical">
        <a-form-item label="目标状态（只列合法迁移）">
          <a-select v-model:value="permitStatusForm.targetStatus" :options="permitTargetOptions" placeholder="请选择" />
        </a-form-item>
        <a-form-item label="原因（吊销必填）">
          <a-textarea v-model:value="permitStatusForm.reason" :rows="3" placeholder="如：安全条件复核不合格 / 企业申请注销" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">
        version 过期（并发改写）会按 §0.4 返回 <code>fieldErrors</code>，请刷新台账后重试；非法迁移由后端拒绝。
      </div>
    </a-modal>

    <!-- 要素录入：人员 / 业绩 / 资产装备（每次提交一项） -->
    <a-modal
      v-model:open="elementOpen"
      :title="`要素录入：资质 #${elementIds.qualId}`"
      :width="900"
      :confirm-loading="elementSaving"
      ok-text="提交当前页签"
      cancel-text="取消"
      destroy-on-close
      @ok="submitElement"
    >
      <a-descriptions :column="2" size="small" class="mb-3">
        <a-descriptions-item label="企业主体 id">{{ elementIds.entId }}</a-descriptions-item>
        <a-descriptions-item label="资质 id">{{ elementIds.qualId }}</a-descriptions-item>
      </a-descriptions>
      <a-tabs v-model:active-key="elementTab">
        <a-tab-pane key="staff" tab="资质人员">
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="姓名（必填）">
                  <a-auto-complete
                    v-model:value="staffForm.personName"
                    :options="personNameOptions"
                    placeholder="从员工档案选择，或直接输入（拟聘人员）"
                    allow-clear
                    @focus="loadPersonNameOptions"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="岗位角色">
                  <a-select v-model:value="staffForm.staffRole" :options="QualificationApi.STAFF_ROLE_OPTIONS" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="专业"><a-input v-model:value="staffForm.major" /></a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="注册号"><a-input v-model:value="staffForm.regNo" /></a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="职称等级"><a-input v-model:value="staffForm.titleLevel" /></a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="是否全职"><a-switch v-model:checked="staffForm.isFullTime" /></a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="注册有效期至">
                  <a-date-picker v-model:value="staffForm.regValidTo" value-format="YYYY-MM-DD" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="关联证照 id（可空）">
                  <a-input-number v-model:value="staffForm.bindedCertId" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="performance" tab="工程业绩">
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="项目名称（必填）"><a-input v-model:value="perfForm.projectName" /></a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="项目类型"><a-input v-model:value="perfForm.projectType" /></a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="角色">
                  <a-select v-model:value="perfForm.role" :options="QualificationApi.PERFORMANCE_ROLE_OPTIONS" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="合同金额">
                  <a-input-number v-model:value="perfForm.contractAmount" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="竣工日期">
                  <a-date-picker v-model:value="perfForm.completionDate" value-format="YYYY-MM-DD" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="质量结论"><a-input v-model:value="perfForm.qualityResult" /></a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="业主名称"><a-input v-model:value="perfForm.ownerName" /></a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="平台可核验"><a-switch v-model:checked="perfForm.isPlatformVerified" /></a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="证据文件 id（逗号分隔，提交时转 JSON 数组）">
                  <a-input v-model:value="perfForm.evidenceFileIds" placeholder="如：101,102" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="asset" tab="资产/技术装备">
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="名称（必填）"><a-input v-model:value="assetForm.name" /></a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="资产类型">
                  <a-select v-model:value="assetForm.assetType" :options="QualificationApi.ASSET_TYPE_OPTIONS" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="规格型号"><a-input v-model:value="assetForm.spec" /></a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="数量">
                  <a-input-number v-model:value="assetForm.quantity" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="价值金额">
                  <a-input-number v-model:value="assetForm.valueAmount" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="统计年份">
                  <a-input-number v-model:value="assetForm.statYear" :min="1990" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="16">
                <a-form-item label="证据文件 id（逗号分隔，提交时转 JSON 数组）">
                  <a-input v-model:value="assetForm.evidenceFileIds" placeholder="如：201,202" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-tab-pane>
      </a-tabs>
      <div class="ct-tip">
        每次提交当前页签的一条要素（后端逐条插入）；角色/资产类型码表取自 QualificationStaffRoleEnum /
        QualificationAssetTypeEnum / PerformanceRoleEnum，尚未登记字典故仅作建议值。录入后可用「达标核查」复算差额。
      </div>
    </a-modal>

    <!-- 安许建档 -->
    <a-modal
      v-model:open="permitOpen"
      title="安全生产许可证建档"
      :width="760"
      :confirm-loading="permitSaving"
      ok-text="建档"
      cancel-text="取消"
      destroy-on-close
      @ok="submitPermit"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="企业主体 id（必填）">
              <a-input-number v-model:value="permitForm.entId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="许可证编号（必填，UK 判重）">
              <a-input v-model:value="permitForm.permitNo" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发证机关"><a-input v-model:value="permitForm.issuingAuthority" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="发证日期">
              <a-date-picker v-model:value="permitForm.issueDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="有效期至（空=长期）">
              <a-date-picker v-model:value="permitForm.validTo" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="法定代表人"><a-input v-model:value="permitForm.legalPerson" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="关联证照 id（可空）">
              <a-input-number v-model:value="permitForm.bindedCertId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注"><a-textarea v-model:value="permitForm.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">
        建档后安许状态为 VALID；状态流转（挂起/吊销等）需 permitId+version，当前无安许列表接口，故本页不提供。
        风险评估与宽限期登记见「员工资质/安许风险」页的安许风险卡片。
      </div>
    </a-modal>
  </div>
</template>
