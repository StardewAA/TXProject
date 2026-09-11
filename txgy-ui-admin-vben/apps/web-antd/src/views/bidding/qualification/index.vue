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
 * 字典口径（契约 §0.5，禁止自造中文枚举）：qual_status / qual_serial / apply_type / apply_status /
 *   review_result(资质达标语义) **尚未登记字典** → 标签一律 dictLabel/dictTag 优先、缺字典回退原始 code；
 *   material_result / material_status / material_category 已注册，走字典标签与颜色。
 * 时间口径：LocalDate（issueDate/validTo/submitDate/dueDate/newValidTo）为 'yyyy-MM-dd' 字符串；
 *   仅 latestReview.reviewedAt 是 LocalDateTime（epoch 毫秒）→ 过 fmtDateTime。 */
import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { type CertificateApi, getMaterialCompleteness } from '#/api/bidding/certificate';
import {
  approveQualificationRenew,
  createQualification,
  createQualificationAsset,
  createQualificationPerformance,
  createQualificationStaff,
  createSafetyPermit,
  getQualification,
  getQualificationApplyPage,
  getQualificationPage,
  QualificationApi,
  rejectQualificationRenew,
  runComplianceReview,
  runQualificationAlertScan,
  submitQualificationRenew,
} from '#/api/bidding/qualification';

import { fmtDate, fmtDateTime } from '../certificate/format';
import { useCertMeta } from '../certificate/useCertMeta';

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
  { title: '资质证书编号', dataIndex: 'certNo', key: 'certNo', width: 200 },
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
const renewFormRef = ref();
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
    void load();
    void loadApplies();
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
    const page = await getQualificationApplyPage({ ...applyQuery });
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
  { title: '申报单号', dataIndex: 'id', key: 'id', width: 110 },
  { title: '资质', dataIndex: 'qualId', key: 'qualId', width: 90 },
  { title: '申报类型', dataIndex: 'applyType', key: 'applyType', width: 120 },
  { title: '目标等级', dataIndex: 'targetGrade', key: 'targetGrade', width: 100 },
  { title: '提交日期', dataIndex: 'submitDate', key: 'submitDate', width: 120 },
  { title: '申请延续至', dataIndex: 'dueDate', key: 'dueDate', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 130 },
  { title: '结果文号', dataIndex: 'resultNo', key: 'resultNo', width: 140 },
  { title: '新证书编号', dataIndex: 'newCertNo', key: 'newCertNo', width: 170 },
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
  newValidTo: [{ required: true, message: '延续后有效期必填', trigger: 'change' }],
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
    void loadApplies();
    void load();
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
      void loadApplies();
      void load();
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
  { title: '有效期止', dataIndex: 'validTo', key: 'validTo', width: 120 },
  { title: '匹配记录', dataIndex: 'refName', key: 'refName', width: 180 },
  { title: '说明', dataIndex: 'message', key: 'message' },
];

async function loadCompleteness() {
  if (!completenessQuery.entId) {
    message.warning('请先填写企业主体 id（entId）');
    return;
  }
  completenessLoading.value = true;
  completenessError.value = '';
  completeness.value = null;
  try {
    completeness.value = await getMaterialCompleteness(completenessQuery.entId, completenessQuery.asOf);
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
  entId: [{ required: true, message: '企业主体 id 必填', trigger: 'change' }],
  qualSerial: [{ required: true, message: '资质序列必填', trigger: 'change' }],
  qualCategory: [{ required: true, message: '资质类别必填', trigger: 'blur' }],
  qualGrade: [{ required: true, message: '资质等级必填', trigger: 'blur' }],
  certNo: [{ required: true, message: '资质证书编号必填（租户内唯一）', trigger: 'blur' }],
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
    void load();
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
    message.warning('人员姓名必填');
    return;
  }
  if (elementTab.value === 'performance' && !perfForm.projectName?.trim()) {
    message.warning('项目名称必填');
    return;
  }
  if (elementTab.value === 'asset' && !assetForm.name?.trim()) {
    message.warning('资产/装备名称必填');
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
    message.warning('企业主体 id 与安全生产许可证编号必填');
    return;
  }
  permitSaving.value = true;
  try {
    const id = await createSafetyPermit({ ...permitForm });
    message.success(`安许建档成功（id=${id}）`);
    permitOpen.value = false;
  } catch {
    // 静默
  } finally {
    permitSaving.value = false;
  }
}

onMounted(() => {
  void load();
  void loadApplies();
});
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">企业资质</div>
        <div class="ct-sub">资质台账 · 延续申报审批 · 材料完备性自查 · 到期预警扫描</div>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:qualification:create')" type="primary" @click="onCreate">资质建档</a-button>
        <a-button v-if="can('bidding:qualification:permit')" @click="openPermit">安许建档</a-button>
        <a-button v-if="can('bidding:qualification:alert-run')" :loading="scanning" @click="doScan">执行预警扫描</a-button>
        <a-button @click="load(); loadApplies();">刷新</a-button>
      </div>
    </div>

    <a-alert type="warning" show-icon class="mb-3">
      <template #message>资质域码表尚未登记字典，列表中序列/状态/申报类型等显示英文 code</template>
      <template #description>
        <div>
          <code>bidding_dict_data</code> 现无 <code>qual_status / qual_serial / qual_grade / apply_type / apply_status</code>
          任何一类（<code>?types=qual_status</code> 会抛 1060108008），故本页对这些字段<strong>不做前端硬编码翻译</strong>：
          字典登记后同页面自动显示中文名与标签色（dictLabel/dictTag 优先）。
        </div>
        <div style="margin-top: 6px">
          已接入：台账列表/详情（含绑定证照与最近核查）、延续申报、审批通过/退回、要素录入（人员·业绩·资产装备）、
          达标核查执行、安许建档、材料完备性自查、到期预警扫描。
          仍缺接口：资质主档<strong>编辑</strong>（无 update，create 只走 insert）、
          资质<strong>状态流转</strong>（Service 有 changeStatus 但无 Controller 暴露）、
          <strong>安许状态流转</strong>（需 permitId+version，待安许列表接口）、
          <strong>要素/核查台账查看</strong>（无 element/review 列表，核查结论仅能从详情 latestReview 回读一条）。
        </div>
      </template>
    </a-alert>

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
            <a-input-number v-model:value="query.entId" :min="1" placeholder="企业主体 id" style="width: 170px" />
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
            <a-input-number v-model:value="applyQuery.entId" :min="1" placeholder="企业主体 id" style="width: 170px" />
            <a-input-number v-model:value="applyQuery.qualId" :min="1" placeholder="资质 id" style="width: 150px" />
            <a-select
              v-model:value="applyQuery.applyType"
              placeholder="申报类型"
              style="width: 150px"
              allow-clear
              :options="[
                { label: 'NEW', value: 'NEW' },
                { label: 'UPGRADE', value: 'UPGRADE' },
                { label: 'RENEW', value: 'RENEW' },
                { label: 'ADDITION', value: 'ADDITION' },
                { label: 'CHANGE', value: 'CHANGE' },
                { label: 'CANCEL', value: 'CANCEL' },
              ]"
              @change="loadApplies"
            />
            <a-input v-model:value="applyQuery.status" placeholder="状态 code（如 SUBMITTED）" style="width: 200px" allow-clear @press-enter="loadApplies" />
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
                <template v-if="column.key === 'applyType'">{{ textOf('apply_type', record.applyType) }}</template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="tagOf('apply_status', record.status).color">
                    {{ tagOf('apply_status', record.status).label }}
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

      <a-tab-pane key="material" tab="③ 材料完备性自查">
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
                <a-form-item label="姓名（必填）"><a-input v-model:value="staffForm.personName" /></a-form-item>
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
