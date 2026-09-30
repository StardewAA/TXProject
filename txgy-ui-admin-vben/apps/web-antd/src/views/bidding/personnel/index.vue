<script setup lang="ts">
/* 员工资质（菜单终态 60005300 / component bidding/personnel/index）
 *   ① 员工档案   GET/POST/PUT /bidding/personnel-profile/{page,get,overview,create,update,change-status}
 *   ② 履历       /bidding/personnel-record/{training,social-security,labor-contract}/{create,list}
 *   ③ 员工证书   契约 §8.1 分页 / §8.2 到期清单 / §8.3 细分统计 / §8.4 法定预警下限
 *   ④ 到期看板   ⑤ 安许配备自查（依赖 certSubType 统计，阈值取 safety.abc.required）
 * 人证合一：按员工、证书类型、状态、到期窗口检索；安许 A/B/C 三类人员配备自查依赖 certSubType 统计。
 * 权限：bidding:personnel:cert:{query,create,update,delete}、bidding:personnel:profile:{query,create,update}、
 *      bidding:personnel:record:{query,create}、bidding:personnel:alert-run
 * 阈值：到期窗口默认取配置 personnel.cert.expiring.window.days；配备要求取 safety.abc.required（不硬编码天数/人数） */
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { certErrorMsg } from '#/api/bidding/certificate';
import { uploadBiddingFile } from '#/api/bidding/file';
import {
  batchRenewPersonnelCert,
  changePersonnelCertStatus,
  changePersonnelProfileStatus,
  countPersonnelCertBySubType,
  createPersonnelCert,
  createPersonnelLaborContract,
  createPersonnelProfile,
  createPersonnelSocialSecurity,
  createPersonnelTraining,
  deletePersonnelCert,
  getPersonnelAlertPage,
  getPersonnelCertByPerson,
  getPersonnelCertExpiring,
  getPersonnelCertPage,
  getPersonnelCertPageByPerson,
  getPersonnelCertStats,
  getPersonnelLaborContractList,
  getPersonnelLegalFloorDays,
  getPersonnelOverview,
  getPersonnelProfilePage,
  getPersonnelSocialSecurityList,
  getPersonnelTrainingList,
  notifyPersonnelCert,
  type PersonnelCertApi,
  type PersonnelProfileApi,
  runPersonnelAlertScan,
  updatePersonnelCert,
  updatePersonnelProfile,
  verifyPersonnelCert,
} from '#/api/bidding/personnel';
// 2026-09-21 职责重新分配：安全域操作（多岗兼任校验 / 人员风险）统一归「安许风险联动」页
//   —— 它们走的是 /bidding/safety-permit/* 前缀，且安许页页面注释的「四块能力」已明列 ②多岗兼任校验 ③人员风险。
//   本页只保留 /bidding/personnel/* 的能力（证书 / 履历 / 到期看板 / 安许配备自查）。
import { getPersonnelRisk, type SafetyPermitApi } from '#/api/bidding/safetyPermit';
import { fetchSubjectProfile } from '#/api/bidding/subject';

import { fmtDate, fmtDateTime } from '../certificate/format';
// 职责边界（2026-09-21 收敛）：本页**不再挂** SafetyRisk 风险卡片
//   —— 安许 A/B/C 风险的读取/重算/快照/宽限是「安许风险联动」页（views/bidding/safety-risk）的职责，
//   两页同时挂同一张卡片会让用户不知道该在哪操作（用户反馈：界面重复、看不出该点哪个）。
//   员工页真正需要的是下方「安许配备自查」（要求人数 vs 实际人数），它依赖 certSubType 统计与 safety.abc.required 配置，不依赖该卡片。
import { useCertMeta } from '../certificate/useCertMeta';
import AttrFilter from '../components/AttrFilter.vue';
import CopyText from '../components/CopyText.vue';
// 员工侧安许能力（多岗兼任校验 / 人员风险）：原在独立安许页，现按三台账 IA 嵌入本页安许页签
//   2026-09-30 收口：safety-risk 页按主体拆到 panels/，本页只 import 员工侧面板（不再有区块开关）
import PersonnelSafetyPanels from '../safety-risk/panels/PersonnelSafetyPanels.vue';
import PdfPreview from '../vo/PdfPreview.vue';

import '../certificate/cert.css';

const router = useRouter();
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { configNumber, configValue, dictLabel, dictOptions, dictTag, metaError } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

/* 默认落在「员工维度（可展开）」——原型页面三的主数据区即该视图（人证合一） */
const tab = ref('byPerson');

/* ===== §8.1 分页 ===== */
const query = reactive<PersonnelCertApi.PageReq>({
  pageNo: 1,
  pageSize: 10,
  personId: undefined,
  entId: undefined,
  certType: undefined,
  certNo: undefined,
  certStatus: undefined,
  major: undefined,
  expiringDays: undefined,
});
const loading = ref(false);
const rows = ref<PersonnelCertApi.Cert[]>([]);
const total = ref(0);

async function load() {
  loading.value = true;
  try {
    const page = await getPersonnelCertPage({ ...query });
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
  Object.assign(query, {
    pageNo: 1,
    personId: undefined,
    // 企业主体由「主体档案」自动带出（证书台账页签查询上下文的必填项），重置筛选项时不清空
    certType: undefined,
    certNo: undefined,
    certStatus: undefined,
    major: undefined,
    expiringDays: undefined,
  });
  void load();
}

/* ===== §8.2 到期看板 ===== */
const expiringDaysCfg = computed(() => configNumber('personnel.cert.expiring.window.days', 30));
const boardEntId = ref<number | undefined>(undefined);
const boardDays = ref<number>(expiringDaysCfg.value);
const boardLoading = ref(false);
const board = ref<PersonnelCertApi.Cert[]>([]);
const boardLoaded = ref(false);

async function loadBoard() {
  boardLoading.value = true;
  try {
    board.value = await getPersonnelCertExpiring(boardEntId.value ?? currentSubject.value?.id, boardDays.value);
    boardLoaded.value = true;
  } catch {
    board.value = [];
  } finally {
    boardLoading.value = false;
  }
}
function syncBoardDays() {
  boardDays.value = expiringDaysCfg.value;
}
/** 配置是异步加载的：到达后同步窗口值；用户已手动改过则尊重用户输入 */
const boardDaysTouched = ref(false);
watch(expiringDaysCfg, (v) => {
  if (!boardDaysTouched.value && v > 0) boardDays.value = v;
});
/** §8.1④ 复审提醒：阈值取配置 personnel.cert.review.warn.days（默认 30），不硬编码 */
const reviewWarnDays = computed(() => configNumber('personnel.cert.review.warn.days', 30));

/* ===== §8.3 安许配备自查 ===== */
const countEntId = ref<number | undefined>(undefined);
const countCertType = ref<string | undefined>(undefined);
const counting = ref(false);
const counts = ref<Record<string, number>>({});
const requiredMap = computed<Record<string, number>>(() => {
  const v = configValue<unknown>('safety.abc.required', null);
  if (v && typeof v === 'object' && !Array.isArray(v)) {
    const out: Record<string, number> = {};
    for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
      const n = Number(val);
      if (Number.isFinite(n)) out[k] = n;
    }
    return out;
  }
  return {};
});
const abcRows = computed(() => {
  const keys = new Set([...Object.keys(requiredMap.value), ...Object.keys(counts.value)]);
  return [...keys].sort().map((k) => ({
    code: k,
    required: requiredMap.value[k] ?? null,
    actual: counts.value[k] ?? 0,
    ok: requiredMap.value[k] === undefined ? null : (counts.value[k] ?? 0) >= (requiredMap.value[k] as number),
  }));
});

async function runCount() {
  const ent = countEntId.value ?? currentSubject.value?.id;
  if (!ent || !countCertType.value) {
    message.warning('请选择证书类型（企业主体由档案自动带出）');
    return;
  }
  counting.value = true;
  try {
    counts.value = await countPersonnelCertBySubType(ent, countCertType.value);
  } catch {
    counts.value = {};
  } finally {
    counting.value = false;
  }
}

/* ===== §8.4 法定预警下限 ===== */
const floors = ref<Record<string, number>>({});
const floorsLoading = ref(false);
async function loadFloors() {
  floorsLoading.value = true;
  try {
    floors.value = await getPersonnelLegalFloorDays();
  } catch {
    floors.value = {};
  } finally {
    floorsLoading.value = false;
  }
}

/* ===== 登记 / 编辑 ===== */
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<null | PersonnelCertApi.Cert>(null);
const form = reactive<PersonnelCertApi.SaveReq>({ personId: undefined, certType: undefined });

/** 证书文件：直接上传取得 fileId（用户不手填文件编号） */
const certFileUploading = ref(false);
async function onPickCertFile(uf: File): Promise<boolean> {
  certFileUploading.value = true;
  try {
    const up = await uploadBiddingFile(uf, 'bidding/personnel');
    if (!up?.fileId) {
      message.warning('证书文件上传失败（未取得文件编号），请重试');
      return false;
    }
    form.fileId = up.fileId;
    message.success('证书文件已上传');
  } catch {
    /* 失败原因由请求层 toast 后端 msg */
  } finally {
    certFileUploading.value = false;
  }
  return false;
}
function clearCertFile() {
  form.fileId = undefined;
}

function openCreate() {
  editing.value = null;
  Object.keys(form).forEach((k) => delete (form as Record<string, unknown>)[k]);
  /* 归属企业：自动带入当前页首选择的主体（员工证书归入当前主体） */
  const ent =
    query.entId ?? profileQuery.entId ?? form.entId;
  if (ent) form.entId = Number(ent);
  formOpen.value = true;
}
function openEdit(r: PersonnelCertApi.Cert) {
  editing.value = r;
  Object.assign(form, {
    id: r.id,
    version: r.version,
    entId: r.entId,
    personId: r.personId,
    certType: r.certType,
    certName: r.certName,
    certNo: r.certNo,
    certLevel: r.certLevel,
    certSubType: r.certSubType,
    nextReviewDate: r.nextReviewDate,
    major: r.major,
    eduLevel: r.eduLevel,
    issuingAuthority: r.issuingAuthority,
    issueDate: r.issueDate,
    validFrom: r.validFrom,
    validTo: r.validTo,
    registerUnit: r.registerUnit,
    registerUnitCode: r.registerUnitCode,
    registerNo: r.registerNo,
    platformVerified: r.platformVerified,
    verifyUrl: r.verifyUrl,
    fileId: r.fileId,
    remark: r.remark,
  });
  formOpen.value = true;
}

async function submitForm() {
  if (!form.personId) {
    message.warning('请选择持证员工（来自员工档案）');
    return;
  }
  if (!form.certType) {
    message.warning('请选择证书类型（字典 person_cert_type）');
    return;
  }
  saving.value = true;
  try {
    if (editing.value?.id) {
      await updatePersonnelCert({ ...form, id: editing.value.id, version: editing.value.version });
      message.success('已保存');
    } else {
      await createPersonnelCert({ ...form });
      message.success('证书已登记');
    }
    formOpen.value = false;
    void load();
  } catch (error: unknown) {
    // 1060107009「该证书类型必须登记有效期」等一律直接展示后端本地化 msg
    message.warning(certErrorMsg(error));
  } finally {
    saving.value = false;
  }
}

/* ===== 状态流转 / 删除 ===== */
const statusOpen = ref(false);
const statusRow = ref<null | PersonnelCertApi.Cert>(null);
const statusTarget = ref<string | undefined>(undefined);
const statusReason = ref('');
const statusSaving = ref(false);

function openStatus(r: PersonnelCertApi.Cert) {
  statusRow.value = r;
  statusTarget.value = undefined;
  statusReason.value = '';
  statusOpen.value = true;
}
async function submitStatus() {
  if (!statusRow.value?.id || !statusTarget.value) {
    message.warning('请选择目标状态');
    return;
  }
  statusSaving.value = true;
  try {
    await changePersonnelCertStatus({
      id: statusRow.value.id,
      targetStatus: statusTarget.value,
      version: statusRow.value.version ?? 0,
      reason: statusReason.value || undefined,
    });
    message.success('状态已流转');
    statusOpen.value = false;
    void load();
  } catch (error: unknown) {
    message.warning(certErrorMsg(error));
  } finally {
    statusSaving.value = false;
  }
}
function confirmDelete(r: PersonnelCertApi.Cert) {
  Modal.confirm({
    title: '删除员工证书',
    content: `确认删除「${r.certName || r.certNo || r.id}」？软删除，历史保留。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deletePersonnelCert(r.id);
        message.success('已删除');
        void load();
      } catch (error: unknown) {
        message.warning(certErrorMsg(error));
      }
    },
  });
}

function confirmScan() {
  Modal.confirm({
    title: '手动触发员工证件到期扫描',
    content: '覆盖证书到期、复审到期、身份证到期；同日幂等，不会重复产生预警。',
    okText: '开始扫描',
    cancelText: '取消',
    async onOk() {
      try {
        const n = await runPersonnelAlertScan();
        message.success(`扫描完成，本次生成预警 ${n} 条`);
        void loadBoard();
      } catch (error: unknown) {
        message.warning(certErrorMsg(error));
      }
    },
  });
}

/* ===== 员工预警清单（后端变更通知-给前端.md §7②：只读展示，处理入口待后端开放） ===== */
const alertOpen = ref(false);
const alertLoading = ref(false);
const alertRows = ref<PersonnelCertApi.PersonnelAlert[]>([]);
const alertTotal = ref(0);
const alertQuery = reactive<PersonnelCertApi.PersonnelAlertPageReq>({
  pageNo: 1,
  pageSize: 10,
  refType: undefined,
  alertType: undefined,
  alertLevel: undefined,
  handled: undefined,
});
/* ===== 预警筛选选项：一律取字典（契约《后端变更通知-电子证照域字典治理》§六-1：删除硬编码枚举数组）=====
 * 2026-09-20 治理前的问题：
 *   · alertType 硬编码 3 项，而字典 alert_type 有 4 项（漏了 QUAL_EXPIRY 资质到期预警）→ 筛不到该类预警；
 *   · 标签把英文码拼进中文（"EXPIRY 到期"），与字典口径（"到期预警"）不一致；
 *   · alertLevel/PROFILE_STATUS 直接用英文码当标签（"INFO"、"ACTIVE"），界面不可读。 */
const alertRefTypeOptions = computed(() => {
  // 字典未注册 alert_ref_type（当前 67 个键里没有）→ 保留本地兜底；后端注册后自动切换
  const fromDict = dictOptions('alert_ref_type');
  return fromDict.length > 0
    ? fromDict
    : [
        { label: '员工证书', value: 'PERSON_CERT' },
        { label: '复审', value: 'PERSON_CERT_REVIEW' },
        { label: '身份证', value: 'PERSON_ID_CARD' },
      ];
});
const alertTypeOptions = computed(() => dictOptions('alert_type'));
const alertLevelOptions = computed(() => dictOptions('alert_level'));
/** 档位配色：取字典 tagType（勿自造映射） */
function alertLevelColor(lvl?: string): string {
  return dictTag('alert_level', lvl).color;
}
const alertColumns = [
  { title: '预警内容', dataIndex: 'alertMessage', key: 'alertMessage', ellipsis: true },
  { title: '对象', dataIndex: 'refName', key: 'refName', width: 120 },
  { title: '类别', dataIndex: 'refType', key: 'refType', width: 110 },
  { title: '档位', dataIndex: 'alertLevel', key: 'alertLevel', width: 90 },
  { title: '触发时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 165 },
  { title: '状态', dataIndex: 'handled', key: 'handled', width: 90 },
  { title: '处理备注', dataIndex: 'handleNote', key: 'handleNote', ellipsis: true },
];
function openAlerts(): void {
  alertQuery.pageNo = 1;
  alertQuery.refType = undefined;
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
    const res = await getPersonnelAlertPage({ ...alertQuery });
    alertRows.value = res.list ?? [];
    alertTotal.value = res.total ?? 0;
  } catch {
    alertRows.value = [];
    alertTotal.value = 0;
  } finally {
    alertLoading.value = false;
  }
}
function alertRefTypeLabel(t?: string): string {
  return alertRefTypeOptions.value.find((o) => o.value === t)?.label ?? t ?? '—';
}

/* 2026-09-21 删除：多岗兼任校验（原 v1.1 §12.5 的 multiForm / runMultiCheck / multiResult / roleOptions）。
 * 删除理由：与「安许风险联动」页的实现**几乎逐字相同**（同样的三个字段、同样的两个前置校验、同一个 checkMultiPost），
 *   且两处提示文案还不一致（本页"请选择员工并选择主岗位与兼任岗位" vs 安许页"人员、主岗、兼任岗不能为空"）。
 *  该能力已归安许页（页面注释「② 多岗兼任校验」），本页不再重复提供。
 *  —— 需要在本页发起校验时，走下方「安许风险联动」跳转。 */

/* ===== v1.1 §12.6 人员风险（权限 bidding:personnel:profile:query，已登记） ===== */
/* 职责边界（2026-09-21 重新分配）：本页**保留**"按需查看单个员工的风险"（只读展示，伴随员工档案看）；
 * **删除**了原来的"本页风险体检"批量评估动作 —— 那属于安全域的评估职责，归「安许风险联动」页。
 * 依据：接口是 /bidding/safety-permit/personnel/risk（安全域前缀），安许页注释的「四块能力」已明列「③ 人员风险」。
 * 因此：评估与批量体检 → 安许页；本页只看"这个人当前的风险标识是什么"。 */
const riskOpen = ref(false);
const riskLoading = ref(false);
const riskRow = ref<null | PersonnelCertApi.Cert>(null);
const riskDetail = ref<null | SafetyPermitApi.PersonRisk>(null);

async function openPersonRisk(r: PersonnelCertApi.Cert) {
  if (!r.personId) {
    message.warning('该证书未关联员工（personId 为空）');
    return;
  }
  riskRow.value = r;
  riskOpen.value = true;
  riskLoading.value = true;
  try {
    riskDetail.value = await getPersonnelRisk(r.personId);
  } catch (error: unknown) {
    riskDetail.value = null;
    message.warning(certErrorMsg(error));
  } finally {
    riskLoading.value = false;
  }
}

/* 2026-09-21 删除：scanPageRisk()「本页风险体检」（对当前页员工并发拉取风险评估）。
 * 删除理由：它是**评估动作**，与「安许风险联动」页的「③ 人员风险」同属安全域职责；
 *   放在员工证书列表上会让"评估该找谁"变模糊（用户反馈：界面重复、看不出该点哪个）。
 *   本页改为**只读**：点行内「风险」查看该员工当前的风险标识与最近一次评估时间（openPersonRisk）。 */

/* ================= 员工档案（profile）与履历（培训 / 社保 / 劳动合同） ================= */

/** 档案状态选项：取字典 personnel_status（ACTIVE=在职 / LEAVE=离职 / SUSPENDED=停职 / RETIRED=退休）。
 *  原先硬编码枚举值数组且 label=code（界面直接显示 "ACTIVE"），按字典治理口径改为读字典。 */
const PROFILE_STATUS_OPTIONS = computed(() => dictOptions('personnel_status'));
/** 允许的状态迁移（与 PersonnelStatusEnum.TRANSITIONS 一致，避免提交必然被拒的目标） */
const PROFILE_TRANSITIONS: Record<string, string[]> = {
  ACTIVE: ['LEAVE', 'SUSPENDED', 'RETIRED'],
  LEAVE: ['ACTIVE', 'RETIRED'],
  RETIRED: [],
  SUSPENDED: ['ACTIVE', 'LEAVE', 'RETIRED'],
};

const profileQuery = reactive<PersonnelProfileApi.PageReq>({ pageNo: 1, pageSize: 10 });
const profileRows = ref<PersonnelProfileApi.Profile[]>([]);
const profileTotal = ref(0);
const profileLoading = ref(false);
const profileColumns = [
  { title: '工号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '工号' }),  dataIndex: 'personNo', key: 'personNo', width: 120 },
  { title: '姓名', dataIndex: 'personName', key: 'personName', width: 100 },
  { title: '身份证（脱敏）', dataIndex: 'idCardMasked', key: 'idCardMasked', width: 170 },
  { title: '性别', key: 'gender', width: 80 },
  { title: '部门', dataIndex: 'deptName', key: 'deptName', width: 130 },
  /* 用工形式：后端 PersonnelEmploymentTypeEnum 明写"劳务派遣/兼职人员即使证书齐全，也应在核验中判为风险项"，
   * 故这里按字典 color_type 着色（LABOR_DISPATCH/PART_TIME = danger），把挂靠信号放在列表上而非藏进详情。 */
  { title: '用工形式', key: 'employmentType', width: 110 },
  { title: '学历', key: 'eduLevel', width: 100 },
  { title: '职称', key: 'titleLevel', width: 110 },
  { title: '入职日期', dataIndex: 'joinDate', key: 'joinDate', width: 115 },
  { title: '状态', key: 'personnelStatus', width: 100 },
  { title: '操作', key: 'action', width: 210, fixed: 'right' as const },
];

async function loadProfiles() {
  profileLoading.value = true;
  try {
    const page = await getPersonnelProfilePage({ ...profileQuery });
    profileRows.value = page.list ?? [];
    profileTotal.value = page.total ?? 0;
  } catch {
    // 静默
  } finally {
    profileLoading.value = false;
  }
}
function onProfilePage(p: number) {
  profileQuery.pageNo = p;
  void loadProfiles();
}

const profileFormOpen = ref(false);
const profileSaving = ref(false);
const profileForm = reactive<PersonnelProfileApi.SaveReq>({ entId: 0, personName: '' });

function resetProfileForm() {
  Object.assign(profileForm, {
    archiveNo: undefined,
    bindedAuthUserId: undefined,
    birthDate: undefined,
    deptName: undefined,
    employmentType: undefined,
    eduLevel: undefined,
    eduMajor: undefined,
    email: undefined,
    entId: profileQuery.entId ?? 0,
    gender: undefined,
    graduateDate: undefined,
    id: undefined,
    idCard: undefined,
    idCardValidTo: undefined,
    joinDate: undefined,
    mobile: undefined,
    personName: '',
    personNo: undefined,
    positionName: undefined,
    remark: undefined,
    school: undefined,
    titleLevel: undefined,
    titleMajor: undefined,
    version: undefined,
    workStartDate: undefined,
  });
}
/** 筛选变化 → 回第 1 页重查；主体变化时联动刷新员工下拉 */
function applyProfileFilter(): void {
  profileQuery.pageNo = 1;
  void loadProfiles();
  void loadPersonOptions();
}

/* ===== 本企业：普通租户只能看自己，无需也不应"选企业" =====
   口径依据：07-主体域前端交付-业务口径说明（企业主体=法人/其他组织，uscc 是唯一键）；
   主体档案 GET /bidding/subject/profile/get 返回本租户档案（含 id/subjectName/uscc），
   故前端直接取本企业（投标人档案优先，回退招标人），筛选/新建表单统一自动带出，用户无感；
   平台跨租户查看走审查任务中心（任务条）或按企业名称搜索，不经本页。 */
const subjectLoading = ref(false);
const currentSubject = ref<null | { id: number; label: string }>(null);

/** 自动带出本企业：员工档案 / 证书台账 / 新建表单统一用同一主体 */
async function loadCurrentSubject(): Promise<void> {
  subjectLoading.value = true;
  try {
    // 契约《主体档案合并一租户一档》：投标/招标共用同一份企业档案，单次无类型调用
    const profile = await fetchSubjectProfile().catch(() => null);
    if (profile?.id !== undefined && profile?.id !== null && profile.subjectName) {
      const label = profile.uscc ? profile.subjectName + '（' + profile.uscc + '）' : profile.subjectName;
      currentSubject.value = { id: Number(profile.id), label };
      if (!profileQuery.entId) {
        profileQuery.entId = Number(profile.id); // 员工档案页签筛选
        profileForm.entId = Number(profile.id); // 新建员工档案：随主体自动带出，用户不必填
      }
      if (!query.entId) {
        query.entId = Number(profile.id); // 证书台账页签筛选
      }
    }
  } finally {
    subjectLoading.value = false;
  }
}

/* ===== 员工下拉（不让用户手填员工 id）：选项取员工档案列表 ===== */
const personOptions = ref<{ label: string; value: number }[]>([]);
const personOptionsLoading = ref(false);
async function loadPersonOptions(): Promise<void> {
  personOptionsLoading.value = true;
  try {
    const page = await getPersonnelProfilePage({ pageNo: 1, pageSize: 200, entId: query.entId ?? profileQuery.entId });
    personOptions.value = (page.list ?? []).map((p) => ({
      label: (p.personNo ? p.personName + '（' + p.personNo + '）' : p.personName) ?? '',
      value: Number(p.id),
    }));
  } catch {
    // 档案接口不可用时下拉为空（宁可不显示，也不让用户手填 id）
    personOptions.value = [];
  } finally {
    personOptionsLoading.value = false;
  }
}

function openProfileCreate() {
  resetProfileForm();
  profileFormOpen.value = true;
}
function openProfileEdit(row: PersonnelProfileApi.Profile) {
  resetProfileForm();
  Object.assign(profileForm, {
    archiveNo: row.archiveNo,
    birthDate: row.birthDate ?? undefined,
    deptName: row.deptName,
    eduLevel: row.eduLevel ?? undefined,
    eduMajor: row.eduMajor,
    email: row.email,
    employmentType: row.employmentType ?? undefined,
    entId: row.entId ?? profileForm.entId,
    gender: row.gender ?? undefined,
    graduateDate: row.graduateDate ?? undefined,
    id: row.id,
    idCard: undefined,
    idCardValidTo: row.idCardValidTo ?? undefined,
    joinDate: row.joinDate ?? undefined,
    mobile: row.mobile,
    personName: row.personName ?? '',
    personNo: row.personNo,
    school: row.school,
    titleLevel: row.titleLevel ?? undefined,
    titleMajor: row.titleMajor,
    // 乐观锁版本（PersonnelProfileSaveReqVO.version）：编辑时原样回传，防并发覆盖
    version: row.version,
    workStartDate: row.workStartDate ?? undefined,
  });
  profileFormOpen.value = true;
}

async function submitProfile() {
  if (!profileForm.entId) {
    message.warning('企业主体不能为空');
    return;
  }
  if (!profileForm.personName?.trim()) {
    message.warning('姓名不能为空');
    return;
  }
  profileSaving.value = true;
  try {
    if (profileForm.id) {
      await updatePersonnelProfile({ ...profileForm });
      message.success('档案已更新');
    } else {
      const id = await createPersonnelProfile({ ...profileForm });
      message.success(`建档成功（id=${id}）`);
    }
    profileFormOpen.value = false;
    refreshProfileTab();
  } catch {
    // 静默：§0.4 错误由请求层呈现（身份证唯一性等）
  } finally {
    profileSaving.value = false;
  }
}

/* ===== 档案状态流转 ===== */
const profileStatusOpen = ref(false);
const profileStatusSaving = ref(false);
const profileStatusRow = ref<null | PersonnelProfileApi.Profile>(null);
const profileStatusForm = reactive<PersonnelProfileApi.StatusChangeReq>({ id: 0, targetStatus: '', version: 0 });
const profileTargetOptions = computed(() =>
  (PROFILE_TRANSITIONS[profileStatusRow.value?.personnelStatus ?? ''] ?? []).map((s) => ({ label: s, value: s })),
);

function openProfileStatus(row: PersonnelProfileApi.Profile) {
  if (row.version === undefined) {
    message.warning('该行缺少 version（乐观锁），请刷新后重试');
    return;
  }
  profileStatusRow.value = row;
  Object.assign(profileStatusForm, { id: row.id, reason: undefined, targetStatus: '', version: row.version });
  profileStatusOpen.value = true;
}

async function submitProfileStatus() {
  if (!profileStatusForm.targetStatus) {
    message.warning('请选择目标状态');
    return;
  }
  profileStatusSaving.value = true;
  try {
    await changePersonnelProfileStatus({ ...profileStatusForm });
    message.success('档案状态已流转');
    profileStatusOpen.value = false;
    refreshProfileTab();
  } catch {
    // 静默
  } finally {
    profileStatusSaving.value = false;
  }
}

/* ===== 员工 / 员工证书详情（2026-09-28 整改：独立详情页） =====
 * 背景：原先「查看详情」错接 doNotify()（只发提醒不显示信息），后改为抽屉，仍不符合用户要求的
 *   「独立详情页 + 分区」；现改为跳转 /bidding/personnel/detail/:id，
 *   页内分区：基本信息 / 文件版本（上传·预览·下载）/ 验真。
 * 列表行内同时提供「查看文件 / 上传」与「详情」，文件预览走 PdfPreview（fileId → 业务受控内联出口）。 */

/* ===== 页头操作收敛（2026-09-28）：主操作留明面，低频动作走「更多」 ===== */
/** 统一刷新：证书台账 + 员工维度 + 看板一次拉齐 */
async function refreshAll(): Promise<void> {
  await Promise.all([load(), loadByPerson(), loadPStats()]);
}

/** 「更多」菜单分发：与按钮共用同一批函数 */
function onMoreAction(info: { key: number | string }): void {
  switch (info.key) {
    case 'alertList': {
      openAlerts();
      break;
    }
    case 'import': {
      // 后端批量导入接口尚未开放（调用会 404）→ 明确提示，不发起必然失败的请求
      Modal.info({
        title: '批量导入暂不可用',
        content: '员工证书批量导入接口尚未开放，请先用「员工证书录入」逐条登记；接口就绪后会自动恢复该入口。',
        okText: '知道了',
      });
      break;
    }
    case 'notify': {
      void doNotify(expandedRowKeys.value.map((k) => Number(k)));
      break;
    }
    case 'scan': {
      confirmScan();
      break;
    }
    default: {
      break;
    }
  }
}

/** 人员姓名兜底：id 缺失时不要拼出「员工 #undefined」 */
function personLabel(row: null | PersonnelCertApi.PersonGroup | undefined): string {
  if (!row) return '—';
  if (row.personName) return row.personName;
  if (row.personId) return `员工 #${row.personId}`;
  return '未关联员工';
}

/** 「查看详情」→ 独立详情页（员工档案 / 员工证书共用同一详情页，按 personId 聚合） */
function openPersonDetail(row: PersonnelCertApi.Cert | PersonnelCertApi.PersonGroup): void {
  const pid = row.personId;
  if (!pid) {
    message.warning('该记录未关联员工，无法打开详情');
    return;
  }
  void router.push(`/bidding/personnel/detail/${pid}`);
}

/** 行内「查看文件」：证书文件一律 fileId → PdfPreview（内联预览，不会变下载） */
const certPreview = reactive<{ fileId?: number | string; name: string; open: boolean }>({
  fileId: undefined,
  name: '',
  open: false,
});
function previewCertFile(c: PersonnelCertApi.Cert): void {
  if (!c.fileId) {
    message.warning('该证书尚未上传文件，请先上传');
    return;
  }
  certPreview.fileId = c.fileId;
  certPreview.name = (c.certName || dictLabel('person_cert_type', c.certType) || '证书文件') + (c.certNo ? `（${c.certNo}）` : '');
  certPreview.open = true;
}

/** 证书 → 保存入参（只回传可写字段，避免把出参只读字段当入参提交） */
function certSaveReq(c: PersonnelCertApi.Cert, fileId?: number): PersonnelCertApi.SaveReq {
  return {
    id: c.id,
    version: c.version,
    entId: c.entId,
    personId: c.personId,
    certType: c.certType,
    certName: c.certName,
    certNo: c.certNo,
    certLevel: c.certLevel,
    certSubType: c.certSubType,
    nextReviewDate: c.nextReviewDate,
    major: c.major,
    eduLevel: c.eduLevel,
    issuingAuthority: c.issuingAuthority,
    issueDate: c.issueDate,
    validFrom: c.validFrom,
    validTo: c.validTo,
    registerUnit: c.registerUnit,
    registerUnitCode: c.registerUnitCode,
    registerNo: c.registerNo,
    platformVerified: c.platformVerified,
    verifyUrl: c.verifyUrl,
    fileId: fileId ?? c.fileId,
    remark: c.remark,
  };
}

/** 行内「上传」：上传成功即把文件挂到该证书（替换现行文件） */
async function uploadCertFile(c: PersonnelCertApi.Cert, file: File): Promise<boolean> {
  try {
    const up = await uploadBiddingFile(file, 'bidding/personnel');
    if (!up?.fileId) {
      message.warning('文件上传失败（未取得文件编号），请重试');
      return false;
    }
    await updatePersonnelCert(certSaveReq(c, up.fileId));
    message.success('文件已上传并挂载到该证书');
    await load();
    await loadByPerson();
  } catch {
    /* 失败原因由请求层 toast 后端 msg */
  }
  return false;
}
/** a-upload 的 before-upload 工厂（模板里不做类型标注） */
const uploadFor = (c: PersonnelCertApi.Cert) => (file: File) => uploadCertFile(c, file);

/* ===== 履职概览（证书 / 社保 / 合同 / 学时 / 风险） ===== */
const overviewOpen = ref(false);
const overviewLoading = ref(false);
const overview = ref<null | PersonnelProfileApi.Overview>(null);
/** 该员工的**全部在册证书**（含历史旧证；见 openOverview 里的口径说明） */
const personCerts = ref<PersonnelCertApi.Cert[]>([]);
/** 证书表数据源：按人查的完整列表优先，接口失败时回退全景出参里的现行证 */
const certRows = computed<PersonnelCertApi.Cert[]>(() =>
  personCerts.value.length ? personCerts.value : ((overview.value?.certificates ?? []) as PersonnelCertApi.Cert[]),
);

async function openOverview(row: PersonnelProfileApi.Profile) {
  overviewOpen.value = true;
  overviewLoading.value = true;
  overview.value = null;
  personCerts.value = [];
  try {
    /* 证书表用 `list-by-person`（按人查**全部在册证书**）而不是全景出参里的 certificates：
     * 依据后端《后端变更通知-给前端》：分页与到期清单两条接口**只返回当前有效证**（isCurrent 恒 true），
     * 只有按人查全部证书会返回 isCurrent=false 的**历史旧证** —— 要区分现行/历史必须走这条。 */
    const [ov, certs] = await Promise.all([
      getPersonnelOverview(row.id),
      getPersonnelCertByPerson(row.id).catch(() => [] as PersonnelCertApi.Cert[]),
    ]);
    overview.value = ov;
    personCerts.value = certs ?? [];
  } catch {
    // 静默
  } finally {
    overviewLoading.value = false;
  }
}

function refreshProfileTab() {
  void loadProfiles();
  const openId = overview.value?.profile?.id;
  if (overviewOpen.value && openId) void openOverview({ id: openId });
}

/* ===== 履历：培训 / 社保 / 劳动合同 ===== */
const recordPersonId = ref<number | undefined>(undefined);
const recordType = ref('training');
const recordLoading = ref(false);
const trainingRows = ref<PersonnelProfileApi.Training[]>([]);
const socialRows = ref<PersonnelProfileApi.SocialSecurity[]>([]);
const contractRows = ref<PersonnelProfileApi.LaborContract[]>([]);
const trainingColumns = [
  { title: '培训名称', dataIndex: 'trainingName', key: 'trainingName', width: 200 },
  { title: '类型', dataIndex: 'trainingType', key: 'trainingType', width: 120 },
  { title: '主办方', dataIndex: 'organizer', key: 'organizer', width: 160 },
  { title: '起止', key: 'period', width: 200 },
  { title: '学时', dataIndex: 'eduHours', key: 'eduHours', width: 90 },
  { title: '结果', dataIndex: 'result', key: 'result', width: 100 },
  { title: '成绩', dataIndex: 'score', key: 'score', width: 90 },
  { title: '证书编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '证书编号' }),  dataIndex: 'certNo', key: 'certNo', width: 150 },
  { title: '证书有效期至', dataIndex: 'certValidTo', key: 'certValidTo', width: 130 },
];
const socialColumns = [
  { title: '缴费月份', dataIndex: 'payMonth', key: 'payMonth', width: 110 },
  { title: '缴纳单位', dataIndex: 'payerName', key: 'payerName', width: 200 },
  { title: '统一社会信用代码', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '统一社会信用代码' }),  dataIndex: 'payerCreditCode', key: 'payerCreditCode', width: 190 },
  { title: '状态', dataIndex: 'payStatus', key: 'payStatus', width: 110 },
  { title: '缴费基数', dataIndex: 'payBase', key: 'payBase', width: 120 },
  { title: '缴费金额', dataIndex: 'payAmount', key: 'payAmount', width: 120 },
  { title: '社保号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '社保号' }),  dataIndex: 'socialNo', key: 'socialNo', width: 150 },
  { title: '来源', dataIndex: 'sourceChannel', key: 'sourceChannel', width: 120 },
];
const contractColumns = [
  { title: '合同编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '合同编号' }),  dataIndex: 'contractNo', key: 'contractNo', width: 170 },
  { title: '类型', dataIndex: 'contractType', key: 'contractType', width: 120 },
  { title: '签订日期', dataIndex: 'signDate', key: 'signDate', width: 120 },
  { title: '起止', key: 'period', width: 200 },
  { title: '状态', dataIndex: 'contractStatus', key: 'contractStatus', width: 110 },
  { title: '岗位', dataIndex: 'positionName', key: 'positionName', width: 150 },
  { title: '附件', dataIndex: 'fileId', key: 'fileId', width: 100 },
];

async function loadRecords() {
  if (!recordPersonId.value) {
    message.warning('请先选择员工');
    return;
  }
  recordLoading.value = true;
  try {
    if (recordType.value === 'socialSecurity') {
      socialRows.value = await getPersonnelSocialSecurityList(recordPersonId.value);
    } else if (recordType.value === 'laborContract') {
      contractRows.value = await getPersonnelLaborContractList(recordPersonId.value);
    } else {
      trainingRows.value = await getPersonnelTrainingList(recordPersonId.value);
    }
  } catch {
    // 静默
  } finally {
    recordLoading.value = false;
  }
}
function switchRecordType() {
  void loadRecords();
}

const recFormOpen = ref(false);
const recSaving = ref(false);
const trainingForm = reactive<PersonnelProfileApi.TrainingSaveReq>({ entId: 0, personId: 0, trainingName: '' });
const socialForm = reactive<PersonnelProfileApi.SocialSecuritySaveReq>({ entId: 0, payMonth: '', personId: 0 });
const contractForm = reactive<PersonnelProfileApi.LaborContractSaveReq>({ contractNo: '', entId: 0, personId: 0 });

/** 履历附件：直接上传取得 fileId（用户不手填文件编号；培训=培训证明，社保=缴费证明，合同=合同扫描件） */
const recordFileUploading = ref(false);
const recordFileName = ref('');
async function onPickRecordFile(uf: File): Promise<boolean> {
  recordFileUploading.value = true;
  try {
    const up = await uploadBiddingFile(uf, 'bidding/personnel');
    if (!up?.fileId) {
      message.warning('附件上传失败（未取得文件编号），请重试');
      return false;
    }
    if (recordType.value === 'socialSecurity') socialForm.evidenceFileId = up.fileId;
    else if (recordType.value === 'laborContract') contractForm.fileId = up.fileId;
    else trainingForm.fileId = up.fileId;
    recordFileName.value = up.name || uf.name;
    message.success('附件已上传');
  } catch {
    /* 失败原因由请求层 toast 后端 msg */
  } finally {
    recordFileUploading.value = false;
  }
  return false;
}
function clearRecordFile() {
  if (recordType.value === 'socialSecurity') socialForm.evidenceFileId = undefined;
  else if (recordType.value === 'laborContract') contractForm.fileId = undefined;
  else trainingForm.fileId = undefined;
  recordFileName.value = '';
}
/** 当前履历表单的附件 fileId（仅用于展示是否已上传） */
const recordFileId = computed(() =>
  recordType.value === 'socialSecurity'
    ? socialForm.evidenceFileId
    : recordType.value === 'laborContract'
      ? contractForm.fileId
      : trainingForm.fileId,
);

function openRecordCreate() {
  if (!recordPersonId.value) {
    message.warning('请先选择员工');
    return;
  }
  const personId = recordPersonId.value;
  const entId = profileRows.value.find((r) => r.id === personId)?.entId ?? profileQuery.entId ?? 0;
  Object.assign(trainingForm, {
    certNo: undefined,
    certValidTo: undefined,
    eduHours: undefined,
    endDate: undefined,
    entId,
    fileId: undefined,
    organizer: undefined,
    personId,
    remark: undefined,
    result: undefined,
    score: undefined,
    startDate: undefined,
    trainingName: '',
    trainingType: undefined,
  });
  Object.assign(socialForm, {
    entId,
    evidenceFileId: undefined,
    payAmount: undefined,
    payBase: undefined,
    payMonth: '',
    payerCreditCode: undefined,
    payerName: undefined,
    payStatus: undefined,
    personId,
    remark: undefined,
    socialNo: undefined,
    sourceChannel: undefined,
  });
  Object.assign(contractForm, {
    contractNo: '',
    contractStatus: undefined,
    contractType: undefined,
    endDate: undefined,
    entId,
    fileId: undefined,
    personId,
    positionName: undefined,
    remark: undefined,
    signDate: undefined,
    startDate: undefined,
  });
  recordFileName.value = '';
  recFormOpen.value = true;
}

async function submitRecord() {
  if (recordType.value === 'training' && !trainingForm.trainingName?.trim()) {
    message.warning('培训名称不能为空');
    return;
  }
  if (recordType.value === 'socialSecurity' && !socialForm.payMonth?.trim()) {
    message.warning('缴费月份不能为空（yyyy-MM）');
    return;
  }
  if (recordType.value === 'laborContract' && !contractForm.contractNo?.trim()) {
    message.warning('合同编号不能为空');
    return;
  }
  recSaving.value = true;
  try {
    if (recordType.value === 'socialSecurity') {
      await createPersonnelSocialSecurity({ ...socialForm });
    } else if (recordType.value === 'laborContract') {
      await createPersonnelLaborContract({ ...contractForm });
    } else {
      await createPersonnelTraining({ ...trainingForm });
    }
    message.success('履历记录已录入');
    recFormOpen.value = false;
    void loadRecords();
    refreshProfileTab();
  } catch {
    // 静默
  } finally {
    recSaving.value = false;
  }
}

/** 页签懒加载：首次进入档案页签才拉档案列表；员工维度页签同理 */
watch(tab, (t) => {
  if (t === 'profile' && profileRows.value.length === 0) void loadProfiles();
  if (t === 'byPerson' && byPersonRows.value.length === 0) {
    void loadByPerson();
    void loadPStats();
  }
});

onMounted(() => {
  void load();
  void loadByPerson();
  void loadPStats();
  syncBoardDays();
  void loadFloors();
  void loadCurrentSubject();
  void loadPersonOptions();
});

const certTypeOptions = computed(() => dictOptions('person_cert_type'));
const certStatusOptions = computed(() => dictOptions('person_cert_status'));

const columns = [
  { title: '员工 / 证书', dataIndex: 'personName', key: 'person', width: 230 },
  { title: '类型 / 细分', dataIndex: 'certType', key: 'certType', width: 170 },
  { title: '有效期', dataIndex: 'validTo', key: 'validity', width: 210 },
  { title: '下次复审', dataIndex: 'nextReviewDate', key: 'nextReviewDate', width: 130 },
  { title: '注册单位', dataIndex: 'registerUnit', key: 'registerUnit', width: 170 },
  { title: '状态', dataIndex: 'certStatus', key: 'certStatus', width: 120 },
  { title: '平台核验', dataIndex: 'platformVerified', key: 'platformVerified', width: 110 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' },
];

/** 下次复审角标：用后端 reviewRemainingDays（前端不做日期减法） */
function reviewTag(r: PersonnelCertApi.Cert): null | { color: string; label: string } {
  const d = r.reviewRemainingDays;
  if (d === undefined || d === null) return null;
  if (d < 0) return { color: 'red', label: `复审逾期 ${Math.abs(d)} 天` };
  if (d <= reviewWarnDays.value) return { color: 'orange', label: `复审 ${d} 天` };
  return null;
}

function remainTag(r: PersonnelCertApi.Cert) {
  const d = r.remainingDays;
  if (d === undefined || d === null) return null;
  if (d < 0) return { color: 'red', label: `已过期 ${Math.abs(d)} 天` };
  if (d <= expiringDaysCfg.value) return { color: 'orange', label: `临期 ${d} 天` };
  return { color: 'green', label: `剩余 ${d} 天` };
}

/** 到期/预警标签悬浮说明（增强暗示）：解释剩余天数、预警窗口与配置来源 */
function expiringHint(r: PersonnelCertApi.Cert): string {
  const d = r.remainingDays;
  if (d === undefined || d === null) return '';
  const w = expiringDaysCfg.value;
  if (d < 0) return `已过期 ${Math.abs(d)} 天：证书已不在有效期内，不可用于配证 / 申报，请尽快更新`;
  if (d <= w) return `临期 ${d} 天：距到期不足预警窗口（阈值 ${w} 天，取配置 personnel.cert.expiring.window.days），请安排续期`;
  return `剩余 ${d} 天：状态正常（预警阈值 ${w} 天，取配置 personnel.cert.expiring.window.days）`;
}

/* ================= 2026-09-18 原型改造新增：员工维度树表 / 看板 / 批量续期 / 批量提醒 / 批量导入 =================
 * 契约出处：docs/电子证照前前端契约/后端接口契约-资质证书五页改造-2026-09-18.md
 * 说明：以下能力后端尚未实现，前端按契约先行接入；未就绪时请求层提示、页面保留空态（不白屏）。 */

/** 员工维度查询（原型「支持展开」树表） */
const byPersonQuery = reactive<PersonnelCertApi.ByPersonPageReq>({
  pageNo: 1,
  pageSize: 20,
  deptName: undefined,
  certType: undefined,
  certStatus: undefined,
  expiringDays: undefined,
  companyId: undefined,
});
const byPersonRows = ref<PersonnelCertApi.PersonGroup[]>([]);
const byPersonTotal = ref(0);
const byPersonLoading = ref(false);
const expandedRowKeys = ref<Array<number | string>>([]);

async function loadByPerson(): Promise<void> {
  byPersonLoading.value = true;
  try {
    const page = await getPersonnelCertPageByPerson({ ...byPersonQuery, entId: currentSubject.value?.id });
    byPersonRows.value = page.list ?? [];
    byPersonTotal.value = page.total ?? 0;
  } catch {
    byPersonRows.value = [];
    byPersonTotal.value = 0;
  } finally {
    byPersonLoading.value = false;
  }
}
function resetByPerson() {
  Object.assign(byPersonQuery, {
    deptName: undefined,
    certType: undefined,
    certStatus: undefined,
    expiringDays: undefined,
    companyId: undefined,
    pageNo: 1,
  });
  void loadByPerson();
}

/** 页面三看板统计（GET /personnel-certificate/stats） */
const pStats = ref<null | PersonnelCertApi.Stats>(null);
const pStatsLoading = ref(false);
const activePStat = ref<string | undefined>(undefined);
async function loadPStats(): Promise<void> {
  pStatsLoading.value = true;
  try {
    pStats.value = await getPersonnelCertStats({
      deptName: byPersonQuery.deptName,
      entId: currentSubject.value?.id,
    });
  } catch {
    pStats.value = null;
  } finally {
    pStatsLoading.value = false;
  }
}
function applyPStat(kind: string): void {
  activePStat.value = kind;
  byPersonQuery.pageNo = 1;
  byPersonQuery.expiringDays = undefined;
  if (kind === 'ALL') byPersonQuery.certStatus = undefined;
  else if (kind === 'EXPIRING') byPersonQuery.certStatus = 'EXPIRING_SOON';
  else if (kind === 'EXPIRED') byPersonQuery.certStatus = 'EXPIRED';
  else if (kind === 'VALID') byPersonQuery.certStatus = 'VALID';
  void loadByPerson();
}

/** 选中证书（证书层级勾选；批量续期 / 批量提醒共用）。
 *  保持原值类型（不 String()）——antd 的 rowKey 是证书 id，转换后选中态会对不上。 */
const selectedCertKeys = ref<Array<number | string>>([]);
const certRowSelection = computed(() => ({
  onChange: (keys: (number | string)[]) => {
    selectedCertKeys.value = keys;
  },
  selectedRowKeys: selectedCertKeys.value,
}));

/** 展开/折叠员工行 */
function onExpandRow(expanded: boolean, record: Record<string, any>): void {
  const pid = record.personId;
  if (pid === undefined || pid === null) return;
  expandedRowKeys.value = expanded
    ? [...expandedRowKeys.value, pid]
    : expandedRowKeys.value.filter((k) => k !== pid);
}

/** 批量续期（契约 POST /personnel-certificate/batch-renew，返回逐条结果） */
const batchRenewOpen = ref(false);
const batchRenewSaving = ref(false);
const batchRenewForm = reactive<{ newValidTo?: string; remark?: string; renewalType?: string }>({});
function openBatchRenew() {
  if (selectedCertKeys.value.length === 0) {
    message.warning('请先勾选要续期的证书（展开员工行后勾选证书层）');
    return;
  }
  batchRenewForm.newValidTo = undefined;
  batchRenewForm.renewalType = undefined;
  batchRenewForm.remark = undefined;
  batchRenewOpen.value = true;
}
async function submitBatchRenew() {
  batchRenewSaving.value = true;
  try {
    const items = selectedCertKeys.value.map((id) => ({ id, version: undefined }));
    const res = await batchRenewPersonnelCert({
      items,
      newValidTo: batchRenewForm.newValidTo,
      renewalType: batchRenewForm.renewalType,
      remark: batchRenewForm.remark,
    });
    const fail = res?.failCount ?? 0;
    if (fail > 0) {
      Modal.info({
        title: `续期完成：成功 ${res?.successCount ?? 0} 条，失败 ${fail} 条`,
        okText: '知道了',
        content: () =>
          h(
            'div',
            { style: 'max-height:320px;overflow:auto' },
            (res?.failures ?? [])
              .slice(0, 50)
              .map((f) => h('div', `${f.name || f.id}：${f.message ?? '未知原因'}`)),
          ),
      });
    } else {
      message.success(`续期完成，成功 ${res?.successCount ?? selectedCertKeys.value.length} 条`);
    }
    batchRenewOpen.value = false;
    selectedCertKeys.value = [];
    void loadByPerson();
    void loadPStats();
  } catch {
    // 错误由请求层提示
  } finally {
    batchRenewSaving.value = false;
  }
}

/** 批量到期提醒（契约 POST /personnel-certificate/alert/notify，幂等）。
 *  两种入口：行内「查看详情」= 对该员工；工具栏 = 对所有**已勾选的证书**。两者都为空时明确提示，不发空请求。 */
const notifying = ref(false);
async function doNotify(personIds?: number[]) {
  const byPerson = (personIds ?? []).filter((v) => v !== undefined && v !== null);
  const byCert = selectedCertKeys.value;
  if (byPerson.length === 0 && byCert.length === 0) {
    message.warning('请先展开员工行或勾选证书，再发起到期提醒');
    return;
  }
  notifying.value = true;
  try {
    const n = await notifyPersonnelCert(
      byPerson.length > 0 ? { personIds: byPerson } : { certIds: byCert.map((k) => String(k)) },
    );
    message.success(`已发出提醒 ${n ?? ''} 条`);
  } catch {
    // 错误由请求层提示
  } finally {
    notifying.value = false;
  }
}



/** 证书层级：验真（原 platformVerified 只是标记位，无验真动作） */
async function doVerifyCert(cert: PersonnelCertApi.Cert) {
  try {
    await verifyPersonnelCert({ certId: cert.id as number });
    message.success('已提交验真，结论以平台核验状态为准');
    void loadByPerson();
  } catch {
    // 错误由请求层提示（若后端确认员工证书不走验真，请去掉该按钮）
  }
}

/** 证书层级：单条续期（复用批量接口的单条形态，保持"逐条结果"语义一致） */
async function doRenewOne(cert: PersonnelCertApi.Cert) {
  openEdit(cert as PersonnelCertApi.Cert);
}

/** 展开/折叠：按员工维度行展开 */
const byPersonColumns = [
  { title: '员工姓名', dataIndex: 'personName', key: 'personName', width: 140 },
  { title: '工号', dataIndex: 'personNo', key: 'personNo', width: 130 },
  { title: '部门', dataIndex: 'deptName', key: 'deptName', width: 160 },
  { title: '持有证书数', dataIndex: 'certCount', key: 'certCount', width: 120 },
  { title: '最近到期时间', dataIndex: 'nearestValidTo', key: 'nearestValidTo', width: 150 },
  { title: '操作', key: 'action', width: 240, fixed: 'right' as const },
];

/** 证书层级列（展开行内） */
const certSubColumns = [
  { title: '证书名称', dataIndex: 'certName', key: 'certName', width: 220, ellipsis: true },
  { title: '证书编号', dataIndex: 'certNo', key: 'certNo', width: 190 },
  { title: '有效期', key: 'validity', width: 210 },
  { title: '状态', dataIndex: 'certStatus', key: 'certStatus', width: 120 },
  { title: '操作', key: 'subAction', width: 200 },
];
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">员工证件台账</h2>
        <!-- 副标题只留一句定位；口径细则移入右侧「口径」气泡 -->
        <p class="ct-sub">人证合一 · 员工档案 / 履历 / 证书 / 到期看板 / 安许 A·B·C 配备自查</p>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:personnel:cert:create')" type="primary" @click="openCreate">员工证书录入</a-button>
        <a-button :loading="loading || byPersonLoading" @click="refreshAll">刷新</a-button>
        <a-dropdown>
          <a-button>
            更多
            <IconifyIcon icon="lucide:chevron-down" class="ml-1 inline-block align-middle" />
          </a-button>
          <template #overlay>
            <a-menu @click="onMoreAction">
              <!-- 后端批量导入接口尚未开放（调用 404）→ 只给明确提示，不提供必然失败的入口 -->
              <a-menu-item v-if="can('bidding:personnel:cert:import')" key="import">批量导入（暂未开放）</a-menu-item>
              <a-menu-item key="notify">
                批量到期提醒{{ expandedRowKeys.length > 0 ? `（已选 ${expandedRowKeys.length} 人）` : '（按当前筛选）' }}
              </a-menu-item>
              <a-menu-item v-if="can('bidding:personnel:alert-run')" key="scan">到期扫描</a-menu-item>
              <a-menu-item v-if="can('bidding:personnel:cert:query')" key="alertList">预警清单</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-popover title="员工证件台账口径" placement="bottomRight" :overlay-style="{ maxWidth: '520px' }">
          <a-button type="text">
            <IconifyIcon icon="lucide:info" class="mr-1 inline-block align-middle" />
            口径
          </a-button>
          <template #content>
            <div class="cert-note">
              <p>
                人证合一：把「人」和「证」关联起来，支持按员工、类型、状态与到期窗口检索，
                供安许证三类人员（A/B/C）配备自查。
              </p>
              <p>
                档案的身份证<strong>只写不读</strong>（回读为脱敏值）；在职状态按状态机流转，
                <strong>离职 / 退休不计入</strong>资质人数与配证候选。到期窗口取配置
                <code>personnel.cert.expiring.window.days</code>，不在前端硬编码天数。
              </p>
            </div>
          </template>
        </a-popover>
      </div>
    </div>

    <a-alert
      v-if="metaError"
      type="warning"
      show-icon
      class="mb-3"
      message="字典/配置未加载，证书类型与状态将按 code 展示"
      :description="`${metaError}｜/bidding/meta/* 需电子证照域任一页面权限（已含 personnel:profile:query，故正常授权下不应出现该提示）。`"
    />

    <a-tabs v-model:active-key="tab">
      <a-tab-pane key="profile" tab="① 员工档案">
        <div class="ct-panel">
          <!-- 筛选：**前台样式**（左侧属性名 + 右侧文字选项） -->
          <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
            <AttrFilter label="本企业"><span class="ct-id">{{ subjectLoading ? '加载中…' : currentSubject?.label || '未取到主体档案' }}</span></AttrFilter>
            <AttrFilter label="姓名">
              <a-input v-model:value="profileQuery.personName" placeholder="模糊匹配，回车查询" style="width: 200px" allow-clear @press-enter="applyProfileFilter" />
            </AttrFilter>
            <AttrFilter label="工号">
              <a-input v-model:value="profileQuery.personNo" placeholder="模糊匹配，回车查询" style="width: 200px" allow-clear @press-enter="applyProfileFilter" />
            </AttrFilter>
            <AttrFilter label="部门">
              <a-input v-model:value="profileQuery.deptName" placeholder="模糊匹配，回车查询" style="width: 200px" allow-clear @press-enter="applyProfileFilter" />
            </AttrFilter>
            <AttrFilter v-model="profileQuery.personnelStatus" label="在职状态" :options="PROFILE_STATUS_OPTIONS" @change="applyProfileFilter" />
            <AttrFilter v-model="profileQuery.eduLevel" label="学历" :options="dictOptions('edu_level')" @change="applyProfileFilter" />
            <div class="mt-2 flex flex-wrap items-center gap-2">
              <a-button type="primary" @click="applyProfileFilter">查询</a-button>
              <a-button v-if="can('bidding:personnel:profile:create')" type="primary" @click="openProfileCreate">建档</a-button>
              <span class="ml-auto text-xs text-muted-foreground">身份证仅提交、回读脱敏；离职/退休人员不计入资质人数</span>
            </div>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="profileColumns"
              :data-source="profileRows"
              :loading="profileLoading"
              :pagination="{
                current: profileQuery.pageNo,
                pageSize: profileQuery.pageSize,
                total: profileTotal,
                showSizeChanger: false,
                onChange: onProfilePage,
              }"
              row-key="id"
              size="small"
              :scroll="{ x: 1450 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'gender'">{{ record.gender ? dictLabel('gender', record.gender) : '—' }}</template>
                <template v-else-if="column.key === 'eduLevel'">
                  {{ record.eduLevel ? dictLabel('edu_level', record.eduLevel) : '—' }}
                </template>
                <template v-else-if="column.key === 'titleLevel'">{{ record.titleLevel || '—' }}</template>
                <template v-else-if="column.key === 'employmentType'">
                  <a-tag
                    v-if="record.employmentType"
                    :color="dictOptions('employment_type').length > 0 ? dictTag('employment_type', record.employmentType).color : 'default'"
                  >
                    {{
                      dictOptions('employment_type').length > 0
                        ? dictTag('employment_type', record.employmentType).label
                        : record.employmentType
                    }}
                  </a-tag>
                  <span v-else>—</span>
                </template>
                <template v-else-if="column.key === 'personnelStatus'">
                  <a-tag
                    :color="dictOptions('personnel_status').length > 0 ? dictTag('personnel_status', record.personnelStatus).color : record.personnelStatus === 'ACTIVE' ? 'green' : 'default'"
                  >
                    {{
                      dictOptions('personnel_status').length > 0
                        ? dictTag('personnel_status', record.personnelStatus).label
                        : record.personnelStatus
                    }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space size="small">
                    <a v-if="can('bidding:personnel:profile:query')" @click="openPersonDetail(record)">查看详情</a>
                    <a v-if="can('bidding:personnel:profile:query')" @click="openOverview(record)">履职概览</a>
                    <a-dropdown>
                      <a @click.prevent>
                        更多
                        <IconifyIcon icon="lucide:chevron-down" class="inline-block align-middle" />
                      </a>
                      <template #overlay>
                        <a-menu>
                          <a-menu-item v-if="can('bidding:personnel:profile:update')" key="edit" @click="openProfileEdit(record)">编辑</a-menu-item>
                          <a-menu-item
                            v-if="can('bidding:personnel:profile:update') && (PROFILE_TRANSITIONS[record.personnelStatus] ?? []).length > 0"
                            key="status"
                            @click="openProfileStatus(record)"
                          >
                            状态流转
                          </a-menu-item>
                        </a-menu>
                      </template>
                    </a-dropdown>
                  </a-space>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">按主体/姓名/工号筛选，或先建一份员工档案</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="record" tab="② 履历（培训·社保·劳动合同）">
        <div class="ct-panel">
          <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
            <AttrFilter label="员工"><a-select v-model:value="recordPersonId" :options="personOptions" show-search option-filter-prop="label" allow-clear placeholder="选择员工（来自员工档案）" style="width: 220px" /></AttrFilter>
            <a-button type="primary" @click="loadRecords">查询</a-button>
            <a-radio-group v-model:value="recordType" button-style="solid" @change="switchRecordType">
              <a-radio-button value="training">培训</a-radio-button>
              <a-radio-button value="socialSecurity">社保</a-radio-button>
              <a-radio-button value="laborContract">劳动合同</a-radio-button>
            </a-radio-group>
            <a-button v-if="can('bidding:personnel:record:create')" @click="openRecordCreate">录入</a-button>
            <span class="ct-panel-meta">履历按员工维度查询（接口无分页，返回全部倒序）</span>
          </div>
          <div class="ct-panel-b">
            <a-table
              v-if="recordType === 'training'"
              class="ct-table"
              :columns="trainingColumns"
              :data-source="trainingRows"
              :loading="recordLoading"
              :pagination="false"
              row-key="id"
              size="small"
              :scroll="{ x: 1300 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'period'">
                  {{ record.startDate || '—' }} ~ {{ record.endDate || '—' }}
                </template>
              </template>
              <template #emptyText><div class="ct-empty">该员工暂无培训记录：创建培训记录后这里会显示</div></template>
            </a-table>

            <a-table
              v-else-if="recordType === 'socialSecurity'"
              class="ct-table"
              :columns="socialColumns"
              :data-source="socialRows"
              :loading="recordLoading"
              :pagination="false"
              row-key="id"
              size="small"
              :scroll="{ x: 1300 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'payStatus'">
                  <a-tag
                    :color="dictOptions('social_pay_status').length > 0 ? dictTag('social_pay_status', record.payStatus).color : 'default'"
                  >
                    {{
                      dictOptions('social_pay_status').length > 0
                        ? dictTag('social_pay_status', record.payStatus).label
                        : (record.payStatus ?? '—')
                    }}
                  </a-tag>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">该员工暂无社保记录（社保单位与主体不一致会进风险项）</div></template>
            </a-table>

            <a-table
              v-else
              class="ct-table"
              :columns="contractColumns"
              :data-source="contractRows"
              :loading="recordLoading"
              :pagination="false"
              row-key="id"
              size="small"
              :scroll="{ x: 1200 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'period'">
                  {{ record.startDate || '—' }} ~ {{ record.endDate || '（无固定期限）' }}
                </template>
              </template>
              <template #emptyText><div class="ct-empty">该员工暂无劳动合同记录：登记劳动合同后这里会显示</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="byPerson" tab="③ 员工维度（可展开）">
        <!-- 数据看板：全库统计（GET /personnel-certificate/stats），点卡片联动下方树表 -->
        <div class="ct-kpis" :class="{ 'is-loading': pStatsLoading }">
          <div class="ct-kpi k-blue">
            <div class="k-label">持证员工数</div>
            <div class="k-num">{{ pStats?.holderCount ?? '—' }}</div>
            <div class="k-note">去重人数</div>
          </div>
          <div class="ct-kpi" :class="{ 'is-active': activePStat === 'ALL' }" @click="applyPStat('ALL')">
            <div class="k-label">证书总数</div>
            <div class="k-num">{{ pStats?.certTotal ?? '—' }}</div>
            <div class="k-note">全库员工证书</div>
          </div>
          <div class="ct-kpi k-orange" :class="{ 'is-active': activePStat === 'EXPIRING' }" @click="applyPStat('EXPIRING')">
            <div class="k-label">临期预警</div>
            <div class="k-num">{{ pStats?.expiring ?? '—' }}</div>
            <div class="k-note">窗口取配置 {{ expiringDaysCfg }} 天</div>
          </div>
          <div class="ct-kpi k-red" :class="{ 'is-active': activePStat === 'EXPIRED' }" @click="applyPStat('EXPIRED')">
            <div class="k-label">异常 / 过期</div>
            <div class="k-num">{{ pStats?.expired ?? '—' }}</div>
            <div class="k-note">已过期 / 吊销</div>
          </div>
          <div class="ct-kpi">
            <div class="k-label">未验真</div>
            <div class="k-num">{{ pStats?.unverified ?? '—' }}</div>
            <div class="k-note">平台未核验</div>
          </div>
        </div>

        <div class="ct-panel">
          <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
            <AttrFilter label="本企业">
              <span class="ct-id">{{ subjectLoading ? '加载中…' : currentSubject?.label || '未取到主体档案' }}</span>
            </AttrFilter>
            <AttrFilter label="部门">
              <a-input
                v-model:value="byPersonQuery.deptName"
                placeholder="模糊匹配，回车查询"
                style="width: 180px"
                allow-clear
                @press-enter="byPersonQuery.pageNo = 1; loadByPerson()"
              />
            </AttrFilter>
            <AttrFilter v-model="byPersonQuery.certType" label="证书类型" :options="certTypeOptions" />
            <AttrFilter v-model="byPersonQuery.certStatus" label="证书状态" :options="certStatusOptions" />
            <AttrFilter label="到期窗口(天)">
              <a-input-number v-model:value="byPersonQuery.expiringDays" :min="1" placeholder="到期窗口" style="width: 140px" />
            </AttrFilter>
            <div class="mt-2 flex flex-wrap items-center gap-2">
              <a-button type="primary" @click="byPersonQuery.pageNo = 1; loadByPerson()">查询</a-button>
              <a-button @click="resetByPerson">重置</a-button>
              <a-button
                v-if="can('bidding:personnel:cert:update')"
                :disabled="selectedCertKeys.length === 0"
                @click="openBatchRenew"
              >
                批量续期{{ selectedCertKeys.length > 0 ? `（已选 ${selectedCertKeys.length}）` : '' }}
              </a-button>
              <span class="ml-auto text-xs text-muted-foreground">展开员工行后可勾选证书，逐条编辑 / 续期 / 验真</span>
            </div>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="byPersonColumns"
              :data-source="byPersonRows"
              :loading="byPersonLoading"
              :expanded-row-keys="expandedRowKeys"
              :pagination="{
                current: byPersonQuery.pageNo,
                pageSize: byPersonQuery.pageSize,
                total: byPersonTotal,
                showSizeChanger: true,
                showTotal: (t: number) => `共 ${t} 人`,
              }"
              row-key="personId"
              size="small"
              :scroll="{ x: 1100 }"
              @expand="onExpandRow"
              @change="(p: any) => { byPersonQuery.pageNo = p.current; byPersonQuery.pageSize = p.pageSize; void loadByPerson(); }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'certCount'">
                  <a-tag :color="(record.certCount ?? 0) > 0 ? 'green' : 'default'">{{ record.certCount ?? 0 }} 本</a-tag>
                </template>
                <template v-else-if="column.key === 'nearestValidTo'">
                  {{ record.nearestValidTo || '—' }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space size="small">
                    <a @click="onExpandRow(!expandedRowKeys.includes(record.personId), record)">
                      {{ expandedRowKeys.includes(record.personId) ? '折叠' : '展开' }}
                    </a>
                    <a
                      v-if="can('bidding:personnel:cert:update')"
                      @click="openBatchRenew"
                    >
                      批量续期
                    </a>
                    <!-- 「查看详情」→ 独立详情页（基本信息 / 文件版本 / 验真） -->
                    <a v-if="can('bidding:personnel:cert:query')" @click="openPersonDetail(record)">查看详情</a>
                  </a-space>
                </template>
              </template>
              <!-- 展开：证书层级（缩进展示证书名称 / 编号 / 有效期 / 状态 + 编辑 / 续期 / 验真） -->
              <template #expandedRowRender="{ record }">
                <a-table
                  class="ct-table ct-subtable"
                  :columns="certSubColumns"
                  :data-source="record.certs ?? []"
                  :pagination="false"
                  :row-selection="certRowSelection"
                  row-key="id"
                  size="small"
                >
                  <template #bodyCell="{ column: sub, record: cert }">
                    <template v-if="sub.key === 'certName'">
                      <div style="font-weight: 700">{{ cert.certName || dictLabel('person_cert_type', cert.certType) }}</div>
                      <div class="ct-panel-meta">{{ cert.certSubType || cert.certLevel || '' }}</div>
                    </template>
                    <template v-else-if="sub.key === 'validity'">
                      <div>{{ cert.validFrom || '—' }} ~ {{ cert.validTo || '长期' }}</div>
                      <a-tag v-if="remainTag(cert)" :color="remainTag(cert)!.color">{{ remainTag(cert)!.label }}</a-tag>
                    </template>
                    <template v-else-if="sub.key === 'certStatus'">
                      <a-tag :color="dictTag('person_cert_status', cert.certStatus).color">
                        {{ dictTag('person_cert_status', cert.certStatus).label }}
                      </a-tag>
                    </template>
                    <template v-else-if="sub.key === 'subAction'">
                      <a-space size="small">
                        <a v-if="cert.fileId" @click="previewCertFile(cert)">查看文件</a>
                        <a v-else-if="can('bidding:personnel:cert:update')" @click="openEdit(cert)">上传</a>
                        <a v-if="can('bidding:personnel:cert:query')" @click="openPersonDetail(cert)">详情</a>
                        <a-dropdown>
                          <a @click.prevent>
                            更多
                            <IconifyIcon icon="lucide:chevron-down" class="inline-block align-middle" />
                          </a>
                          <template #overlay>
                            <a-menu>
                              <a-menu-item v-if="can('bidding:personnel:cert:update')" key="edit" @click="doRenewOne(cert)">编辑</a-menu-item>
                              <a-menu-item v-if="can('bidding:personnel:cert:update')" key="renew" @click="openBatchRenew">续期</a-menu-item>
                              <a-menu-item v-if="can('bidding:personnel:cert:query')" key="verify" @click="doVerifyCert(cert)">验真</a-menu-item>
                            </a-menu>
                          </template>
                        </a-dropdown>
                      </a-space>
                    </template>
                  </template>
                  <template #emptyText><div class="ct-empty">该员工在当前筛选下没有证书</div></template>
                </a-table>
              </template>
              <template #emptyText>
                <div class="ct-empty">
                  暂无员工维度数据：该接口（page-by-person）后端待实现；也可先看「③ 员工证书」平铺视图
                </div>
              </template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="list" tab="④ 员工证书">
        <div class="ct-panel">
          <div class="ct-tip">员工证照由租户企业自行录入与审查：录入 → 自审（通过 / 退回）→ 生效；平台审查只针对企业资质，员工证照无平台入口。</div>
          <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
            <AttrFilter label="员工"><a-select v-model:value="query.personId" :options="personOptions" show-search option-filter-prop="label" allow-clear placeholder="选择员工（来自员工档案）" style="width: 200px" /></AttrFilter>
            <AttrFilter label="本企业"><span class="ct-id">{{ subjectLoading ? '加载中…' : currentSubject?.label || '未取到主体档案' }}</span></AttrFilter>
            <AttrFilter v-model="query.certType" label="证书类型" :options="certTypeOptions" />
            <AttrFilter v-model="query.certStatus" label="状态" :options="certStatusOptions" />
            <AttrFilter label="证书编号（精确）"><a-input v-model:value="query.certNo" placeholder="证书编号（精确）" style="width: 170px" allow-clear /></AttrFilter>
            <AttrFilter label="专业（模糊）"><a-input v-model:value="query.major" placeholder="专业（模糊）" style="width: 140px" allow-clear /></AttrFilter>
            <AttrFilter label="到期窗口(天)"><a-input-number v-model:value="query.expiringDays" :min="1" placeholder="到期窗口(天)" style="width: 140px" /></AttrFilter>
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
              :scroll="{ x: 1500 }"
              @change="
                (p: any) => {
                  query.pageNo = p.current;
                  query.pageSize = p.pageSize;
                  load();
                }
              "
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'person'">
                  <div style="font-weight: 700">
                    {{ personLabel(record) }}
                  </div>
                  <div class="ct-id">{{ record.certName || '—' }} · {{ record.certNo || '—' }}</div>
                </template>
                <template v-else-if="column.key === 'certType'">
                  {{ record.certType ? dictLabel('person_cert_type', record.certType) : '—' }}
                  <div class="ct-panel-meta">{{ record.certSubType || record.certLevel || '' }}</div>
                </template>
                <template v-else-if="column.key === 'validity'">
                  <div>{{ record.validFrom || '—' }} ~ {{ record.validTo || '长期' }}</div>
                  <a-tooltip v-if="remainTag(record)" :title="expiringHint(record)">
                    <a-tag :color="remainTag(record)!.color">{{ remainTag(record)!.label }}</a-tag>
                  </a-tooltip>
                  <a-tag v-if="reviewTag(record)" :color="reviewTag(record)!.color">{{ reviewTag(record)!.label }}</a-tag>
                </template>
                <template v-else-if="column.key === 'certStatus'">
                  <a-tag :color="dictTag('person_cert_status', record.certStatus).color">
                    {{ dictTag('person_cert_status', record.certStatus).label }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'platformVerified'">
                  <a-tag :color="record.platformVerified ? 'green' : 'default'">
                    {{ record.platformVerified ? '已核验' : '未核验' }}
                  </a-tag>
                  <div v-if="record.verifiedAt" class="ct-panel-meta">{{ fmtDateTime(record.verifiedAt) }}</div>
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space size="small">
                    <!-- 文件入口显眼：有文件直接「查看文件」，无文件直接「上传」 -->
                    <a v-if="record.fileId" @click="previewCertFile(record)">查看文件</a>
                    <a-upload
                      v-else-if="can('bidding:personnel:cert:update')"
                      :before-upload="uploadFor(record)"
                      :show-upload-list="false"
                    >
                      <a>上传</a>
                    </a-upload>
                    <a v-if="can('bidding:personnel:cert:query')" @click="openPersonDetail(record)">详情</a>
                    <a-dropdown>
                      <a @click.prevent>
                        更多
                        <IconifyIcon icon="lucide:chevron-down" class="inline-block align-middle" />
                      </a>
                      <template #overlay>
                        <a-menu>
                          <a-menu-item v-if="can('bidding:personnel:cert:update')" key="edit" @click="openEdit(record)">编辑</a-menu-item>
                          <a-menu-item v-if="can('bidding:personnel:cert:update')" key="status" @click="openStatus(record)">状态流转</a-menu-item>
                          <a-menu-item v-if="can('bidding:personnel:cert:query')" key="risk" @click="openPersonRisk(record)">人员风险</a-menu-item>
                          <a-menu-item v-if="can('bidding:personnel:cert:delete')" key="delete" danger @click="confirmDelete(record)">删除</a-menu-item>
                        </a-menu>
                      </template>
                    </a-dropdown>
                  </a-space>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">暂无员工证书数据：按筛选条件查询，或先为员工建档证书</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="board" tab="④ 到期看板">
        <div class="ct-panel">
          <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
            <AttrFilter label="本企业"><span class="ct-id">{{ subjectLoading ? '加载中…' : currentSubject?.label || '未取到主体档案' }}</span></AttrFilter>
            <AttrFilter label="到期窗口天数">
<a-input-number
              v-model:value="boardDays"
              :min="1"
              :max="730"
              placeholder="到期窗口天数"
              style="width: 160px"
              @change="boardDaysTouched = true"
/>
</AttrFilter>
            <a-button type="primary" :loading="boardLoading" @click="loadBoard">刷新看板</a-button>
            <span class="ct-ml">
              窗口默认取配置 personnel.cert.expiring.window.days = {{ expiringDaysCfg }} 天
            </span>
          </div>
          <div class="ct-panel-b">
            <div v-if="board.length" class="ct-kpis" style="margin-top: 0">
              <div class="ct-kpi k-orange">
                <div class="k-label">窗口内待处理</div>
                <div class="k-num">{{ board.length }}</div>
                <div class="k-note">{{ boardDays }} 天内到期（按到期日升序）</div>
              </div>
              <div class="ct-kpi k-red">
                <div class="k-label">其中已过期</div>
                <div class="k-num">{{ board.filter((b) => (b.remainingDays ?? 1) < 0).length }}</div>
                <div class="k-note">需立即办理</div>
              </div>
              <div class="ct-kpi">
                <div class="k-label">涉及员工</div>
                <div class="k-num">{{ new Set(board.map((b) => b.personId)).size }}</div>
                <div class="k-note">去重人数</div>
              </div>
            </div>
            <a-spin :spinning="boardLoading">
              <ul v-if="board.length" class="ct-tl">
                <li v-for="b in board" :key="b.id">
                  <div class="tl-t">
                    <a-tooltip v-if="remainTag(b)" :title="expiringHint(b)">
                      <a-tag :color="remainTag(b)!.color">{{ remainTag(b)!.label }}</a-tag>
                    </a-tooltip>
                    <b>{{ b.personName || `员工 #${b.personId}` }}</b>
                    <span>{{ b.certName || dictLabel('person_cert_type', b.certType) }}</span>
                    <span class="ct-id">{{ b.certNo || '' }}</span>
                  </div>
                  <div class="tl-m">
                    <span class="tl-k">到期日：</span>{{ b.validTo || '长期' }}
                    <template v-if="b.nextReviewDate">
                      ；<span class="tl-k">下次复审：</span>{{ fmtDate(b.nextReviewDate) }}
                      <a-tag v-if="reviewTag(b)" :color="reviewTag(b)!.color">{{ reviewTag(b)!.label }}</a-tag>
                    </template>
                    <template v-if="b.registerUnit">；<span class="tl-k">注册单位：</span>{{ b.registerUnit }}</template>
                    <template v-if="b.certSubType">；<span class="tl-k">细分：</span>{{ b.certSubType }}</template>
                  </div>
                </li>
              </ul>
              <div v-else class="ct-empty">
                {{ boardLoaded ? '窗口内没有需要处理的证书' : '点「刷新看板」加载到期清单（不分页，按到期日升序）' }}
              </div>
            </a-spin>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="abc" tab="⑤ 安许配备自查">
        <div class="ct-panel">
          <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
            <AttrFilter label="本企业"><span class="ct-id">{{ subjectLoading ? '加载中…' : currentSubject?.label || '未取到主体档案' }}</span></AttrFilter>
            <AttrFilter v-model="countCertType" label="证书类型（如安许考核合格证）" :options="certTypeOptions" />
            <a-button type="primary" :loading="counting" @click="runCount">统计可用持证人数</a-button>
            <span class="ct-ml">配备要求取自配置 safety.abc.required</span>
          </div>
          <div class="ct-panel-b">
            <!-- 2026-09-21 去重：原此处内嵌 SafetyRisk 风险卡片（读取/重算/快照/宽限），与「安许风险联动」页功能重合，已移除。
                 本页只保留"配备自查"（要求人数 vs 实际人数）；风险本身请到专属页处理。 -->
            <a-alert class="mb-3" type="info" show-icon>
              <template #message>
                安许 A/B/C 风险的读取、重算、快照与宽限登记，统一在
                <a @click="router.push('/bidding/safety-risk')">「安许风险联动」</a>
                页处理；本页只做「安许配备自查」（要求人数 vs 实际人数）。
              </template>
            </a-alert>
            <a-table
              class="ct-table"
              :columns="[
                { title: '细分（A/B/C 等）', dataIndex: 'code', key: 'code', width: 200 },
                { title: '要求人数', dataIndex: 'required', key: 'required', width: 140 },
                { title: '可用持证人数', dataIndex: 'actual', key: 'actual', width: 160 },
                { title: '结论', dataIndex: 'ok', key: 'ok', width: 160 },
              ]"
              :data-source="abcRows"
              :pagination="false"
              row-key="code"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'required'">
                  {{ record.required === null ? '未配置' : record.required }}
                </template>
                <template v-else-if="column.key === 'ok'">
                  <a-tag v-if="record.ok === null" color="default">仅统计</a-tag>
                  <a-tag v-else :color="record.ok ? 'green' : 'red'">{{ record.ok ? '达标' : '缺员' }}</a-tag>
                </template>
              </template>
              <template #emptyText>
                <div class="ct-empty">选择企业与证书类型后统计（仅统计"可用状态"持证人数）</div>
              </template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <!-- 2026-09-21 去重：「多岗兼任校验」页签已移除 —— 该能力归「安许风险联动」页（避免两页各一套实现） -->
      <a-tab-pane key="multi" tab="多岗兼任校验">
        <div class="ct-panel">
          <div class="ct-panel-b">
            <a-alert type="info" show-icon>
              <template #message>
                「多岗兼任校验」已统一到
                <a @click="router.push('/bidding/safety-risk')">「安许风险联动」</a>
                页（与安许 A/B/C 风险、人员风险、规则配置同属安全域），本页不再重复提供入口。
              </template>
            </a-alert>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="floor" tab="法定预警下限">
        <div class="ct-panel">
          <div class="ct-panel-h">
            <span class="ct-panel-t">法定办理/复审提前期</span>
            <span class="ct-panel-meta">配置预警天数不得低于该值，否则会错过法定窗口</span>
          </div>
          <div class="ct-panel-b">
            <a-spin :spinning="floorsLoading">
              <a-table
                class="ct-table"
                :columns="[
                  { title: '证照类别', dataIndex: 'code', key: 'code' },
                  { title: '法定下限（天）', dataIndex: 'days', key: 'days', width: 180 },
                ]"
                :data-source="Object.entries(floors).map(([code, days]) => ({ code, days }))"
                :pagination="false"
                row-key="code"
                size="small"
              >
                <template #emptyText><div class="ct-empty">暂无下限配置：需先在配置中心设置法定办理/复审提前期下限后重新加载</div></template>
              </a-table>
            </a-spin>
          </div>
        </div>

        <!-- 2026-09-28 三台账重组：多岗兼任校验与人员风险回到本台账。
             归属依据（判据一 主实体原则）：两者的主实体都是**员工**（回答"这名人员能不能上岗"），
             与「安许配备自查」同属员工侧，故合并进同一个安许页签。
             【为何推翻 2026-09-21 的拆分】当时按"接口前缀归属"把它们判给安许页
             （理由是走 /bidding/safety-permit/* 前缀）——那是**实现细节**，不是业务归属；
             本次按主实体重判，接口前缀不参与归属判断。
             实现方式：直接挂员工侧面板 PersonnelSafetyPanels（safety-risk 已按主体拆到 panels/，
             组件内只含员工侧区块：②多岗兼任校验 + ③人员风险），实现只有一份。 -->
        <PersonnelSafetyPanels class="mt-3" />
      </a-tab-pane>
    </a-tabs>


    <!-- v1.1 §12.6 人员风险抽屉（不自动拒绝，仅提示留痕） -->
    <a-drawer v-model:open="riskOpen" class="bid-ind" :width="620" title="人员风险" destroy-on-close>
      <a-spin :spinning="riskLoading">
        <div class="ct-lb">员工</div>
        <div class="mb-3">
          {{ riskRow?.personName || (riskRow?.personId ? `员工 #${riskRow.personId}` : '未关联员工') }}
          <span class="ct-id">{{ riskRow?.certName || '' }}</span>
        </div>
        <template v-if="riskDetail">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="人员风险标识">
              <a-tag :color="dictTag('person_risk_flag', riskDetail.personRiskFlag).color">
                {{ riskDetail.personRiskFlag ? dictTag('person_risk_flag', riskDetail.personRiskFlag).label : '未评估' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="12 个月岗位变更次数">
              {{ riskDetail.jobChangeCount12m ?? 0 }} 次（窗口起 {{ riskDetail.jobChangeWindowStart || '—' }}）
            </a-descriptions-item>
            <a-descriptions-item label="是否异常">
              <a-tag :color="riskDetail.abnormal ? 'red' : 'green'">{{ riskDetail.abnormal ? '异常' : '正常' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="评估时间">{{ riskDetail.personRiskEvaluatedAt || '未评估' }}</a-descriptions-item>
          </a-descriptions>
          <div class="ct-tip">人员风险仅用于提示与留痕，不自动拒绝业务操作（契约 v1.1 §12.6）。</div>
        </template>
        <div v-else class="ct-empty">未取到人员风险数据</div>
      </a-spin>
    </a-drawer>

    <!-- 员工预警清单（后端变更通知-给前端.md §7②：只读展示，处理入口待后端开放） -->
    <a-drawer v-model:open="alertOpen" class="bid-ind" :width="900" title="员工预警清单" destroy-on-close>
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <a-select v-model:value="alertQuery.refType" :options="alertRefTypeOptions" placeholder="预警类别" allow-clear style="width: 130px" @change="reloadAlerts" />
        <a-select v-model:value="alertQuery.alertType" :options="alertTypeOptions" placeholder="预警类型" allow-clear style="width: 150px" @change="reloadAlerts" />
        <a-select v-model:value="alertQuery.alertLevel" :options="alertLevelOptions" placeholder="档位" allow-clear style="width: 130px" @change="reloadAlerts" />
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
        :scroll="{ x: 820 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'refType'">{{ alertRefTypeLabel(record.refType) }}</template>
          <template v-else-if="column.key === 'alertLevel'">
            <a-tag :color="alertLevelColor(record.alertLevel)">{{ dictLabel('alert_level', record.alertLevel) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'triggeredAt'">{{ fmtDateTime(record.triggeredAt) }}</template>
          <template v-else-if="column.key === 'handled'">
            <a-tag :color="record.handled ? 'green' : 'orange'">{{ record.handled ? '已处理' : '待处理' }}</a-tag>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">暂无预警：可先点「到期扫描」补跑生成</div></template>
      </a-table>
    </a-drawer>

    <!-- 员工档案建档 / 编辑 -->
    <a-modal
      v-model:open="profileFormOpen"
      :title="profileForm.id ? `编辑员工档案 #${profileForm.id}` : '员工档案建档'"
      :width="900"
      :confirm-loading="profileSaving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submitProfile"
    >
      <a-form layout="horizontal">
        <!-- ① 归属与身份（编号类 + 个人基本信息） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">归属与身份<span class="ct-sg-note">企业自动带入页首主体；姓名 / 工号 / 证件</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="归属企业（必填）">
                <a-input :value="currentSubject?.label || '加载中…'" disabled />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="姓名（必填）"><a-input v-model:value="profileForm.personName" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="工号"><a-input v-model:value="profileForm.personNo" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item :label="profileForm.id ? '身份证号（留空=不修改）' : '身份证号'">
                <a-input v-model:value="profileForm.idCard" placeholder="18 位，后端唯一性校验" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="身份证有效期至">
                <a-date-picker v-model:value="profileForm.idCardValidTo" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="性别">
                <a-select v-model:value="profileForm.gender" allow-clear :options="dictOptions('gender')" />
              </a-form-item>
            </div>
          </div>
        </div>

        <!-- ② 联系方式与教育 -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">联系方式与教育</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="出生日期">
                <a-date-picker v-model:value="profileForm.birthDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="手机号"><a-input v-model:value="profileForm.mobile" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="邮箱"><a-input v-model:value="profileForm.email" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="学历">
                <a-select v-model:value="profileForm.eduLevel" allow-clear :options="dictOptions('edu_level')" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="所学专业"><a-input v-model:value="profileForm.eduMajor" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="毕业院校"><a-input v-model:value="profileForm.school" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="毕业日期">
                <a-date-picker v-model:value="profileForm.graduateDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="职称等级"><a-input v-model:value="profileForm.titleLevel" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="职称专业"><a-input v-model:value="profileForm.titleMajor" /></a-form-item>
            </div>
          </div>
        </div>

        <!-- ③ 任职信息 -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">任职信息<span class="ct-sg-note">部门 / 岗位 / 用工</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="参加工作日期">
                <a-date-picker v-model:value="profileForm.workStartDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="入职日期">
                <a-date-picker v-model:value="profileForm.joinDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="部门"><a-input v-model:value="profileForm.deptName" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <!-- 岗位名称 / 人事档案号：Profile 出参本来就有，SaveReq 此前缺声明导致"看得见存不下"；
                   2026-09-14 由"类型对等"门禁（PersonnelProfileSaveReqVO ← SaveReq）发现后补齐。 -->
              <a-form-item label="岗位名称"><a-input v-model:value="profileForm.positionName" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="人事档案号"><a-input v-model:value="profileForm.archiveNo" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <!-- 用工形式：字典 employment_type（PersonnelEmploymentTypeEnum）。留空 = 后端按列默认 FULL_TIME 落库，
                   故不设为必填，避免把"没填"变成提交失败。 -->
              <a-form-item label="用工形式">
                <a-select
                  v-model:value="profileForm.employmentType"
                  :options="dictOptions('employment_type')"
                  placeholder="留空 = 全职（后端默认）"
                  allow-clear
                />
              </a-form-item>
            </div>
          </div>
        </div>

        <!-- ④ 备注（自由文本最后） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">备注</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="备注"><a-textarea v-model:value="profileForm.remark" :rows="2" /></a-form-item>
            </div>
          </div>
        </div>
      </a-form>
      <div class="ct-tip">
        编辑时不回填身份证号（详情只回传脱敏值），如需换证请重新填写；在职状态请用行内「状态流转」。
      </div>
    </a-modal>

    <!-- 档案状态流转 -->
    <a-modal
      v-model:open="profileStatusOpen"
      title="员工档案状态流转"
      :width="620"
      :confirm-loading="profileStatusSaving"
      ok-text="提交"
      cancel-text="取消"
      destroy-on-close
      @ok="submitProfileStatus"
    >
      <a-descriptions :column="2" size="small" class="mb-3">
        <a-descriptions-item label="姓名">{{ profileStatusRow?.personName ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="当前状态">{{ profileStatusRow?.personnelStatus ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="乐观锁 version">{{ profileStatusForm.version }}</a-descriptions-item>
        <a-descriptions-item label="部门">{{ profileStatusRow?.deptName ?? '—' }}</a-descriptions-item>
        <!-- 状态流转前先给"上次何时变的"：避免刚改过又改（后端 statusChangeDate 一直在回传，此前未展示） -->
        <a-descriptions-item label="最近状态变更">{{ profileStatusRow?.statusChangeDate || '—' }}</a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical">
        <a-form-item label="目标状态（只列合法迁移）">
          <a-select v-model:value="profileStatusForm.targetStatus" :options="profileTargetOptions" placeholder="请选择" />
        </a-form-item>
        <a-form-item label="原因">
          <a-textarea v-model:value="profileStatusForm.reason" :rows="3" placeholder="如：离职交接完成 / 停职待岗" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">离职/退休后该员工不再计入资质人数与投标配证候选；非法迁移由后端按状态机拒绝。</div>
    </a-modal>

    <!-- 履历录入（按当前页签切换表单） -->
    <a-modal
      v-model:open="recFormOpen"
      :title="recordType === 'training' ? '培训记录录入' : recordType === 'socialSecurity' ? '社保记录录入' : '劳动合同录入'"
      :width="820"
      :confirm-loading="recSaving"
      ok-text="录入"
      cancel-text="取消"
      destroy-on-close
      @ok="submitRecord"
    >
      <a-form v-if="recordType === 'training'" layout="horizontal">
        <!-- ① 培训信息（元数据） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">培训信息<span class="ct-sg-note">名称 / 类型 / 主办方</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="培训名称（必填）"><a-input v-model:value="trainingForm.trainingName" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="培训类型"><a-input v-model:value="trainingForm.trainingType" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="主办方"><a-input v-model:value="trainingForm.organizer" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="结果"><a-input v-model:value="trainingForm.result" placeholder="如：合格" /></a-form-item>
            </div>
          </div>
        </div>
        <!-- ② 时间与学时 -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">时间与学时</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="开始日期">
                <a-date-picker v-model:value="trainingForm.startDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="结束日期">
                <a-date-picker v-model:value="trainingForm.endDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="学时">
                <a-input-number v-model:value="trainingForm.eduHours" :min="0" style="width: 100%" />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="成绩">
                <a-input-number v-model:value="trainingForm.score" :min="0" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="证书编号"><a-input v-model:value="trainingForm.certNo" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="证书有效期至">
                <a-date-picker v-model:value="trainingForm.certValidTo" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
          </div>
        </div>
        <!-- ③ 附件与备注（最后） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">附件与备注</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="培训证明">
                <div class="flex items-center gap-2">
                  <a-upload :before-upload="onPickRecordFile" :show-upload-list="false" :disabled="recordFileUploading">
                    <a-button :loading="recordFileUploading">{{ recordFileUploading ? '上传中…' : '选择文件上传' }}</a-button>
                  </a-upload>
                  <a v-if="recordFileId" class="text-xs text-red-500" @click="clearRecordFile">移除</a>
                  <span v-if="recordFileId" class="text-xs text-green-600">{{ recordFileName || (`#${ recordFileId}`) }}</span>
                </div>
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="备注"><a-textarea v-model:value="trainingForm.remark" :rows="2" /></a-form-item>
            </div>
          </div>
        </div>
      </a-form>

      <a-form v-else-if="recordType === 'socialSecurity'" layout="horizontal">
        <!-- ① 缴费信息 -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">缴费信息<span class="ct-sg-note">月份 / 单位 / 状态</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="缴费月份（必填）">
                <a-input v-model:value="socialForm.payMonth" placeholder="如 2026-09" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="缴纳单位"><a-input v-model:value="socialForm.payerName" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="缴纳单位代码">
                <a-input v-model:value="socialForm.payerCreditCode" placeholder="统一社会信用代码" />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="缴费状态"><a-input v-model:value="socialForm.payStatus" placeholder="如：正常" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="缴费基数">
                <a-input-number v-model:value="socialForm.payBase" :min="0" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="缴费金额">
                <a-input-number v-model:value="socialForm.payAmount" :min="0" style="width: 100%" />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="社保号"><a-input v-model:value="socialForm.socialNo" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="来源渠道">
                <a-input v-model:value="socialForm.sourceChannel" placeholder="如：社保局导出" />
              </a-form-item>
            </div>
          </div>
        </div>
        <!-- ② 附件与备注（最后） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">附件与备注</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="缴费证明">
                <div class="flex items-center gap-2">
                  <a-upload :before-upload="onPickRecordFile" :show-upload-list="false" :disabled="recordFileUploading">
                    <a-button :loading="recordFileUploading">{{ recordFileUploading ? '上传中…' : '选择文件上传' }}</a-button>
                  </a-upload>
                  <a v-if="recordFileId" class="text-xs text-red-500" @click="clearRecordFile">移除</a>
                  <span v-if="recordFileId" class="text-xs text-green-600">{{ recordFileName || (`#${ recordFileId}`) }}</span>
                </div>
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="备注"><a-textarea v-model:value="socialForm.remark" :rows="2" /></a-form-item>
            </div>
          </div>
        </div>
      </a-form>

      <a-form v-else layout="horizontal">
        <!-- ① 合同信息（编号类 + 元数据） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">合同信息<span class="ct-sg-note">编号 / 类型 / 岗位 / 状态</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="合同编号（必填）"><a-input v-model:value="contractForm.contractNo" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="合同类型">
                <a-input v-model:value="contractForm.contractType" placeholder="如：固定期限" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="岗位"><a-input v-model:value="contractForm.positionName" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="合同状态">
                <a-input v-model:value="contractForm.contractStatus" placeholder="如：履行中" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="签订日期">
                <a-date-picker v-model:value="contractForm.signDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="开始日期">
                <a-date-picker v-model:value="contractForm.startDate" value-format="YYYY-MM-DD" style="width: 100%" />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="结束日期">
                <a-date-picker v-model:value="contractForm.endDate" value-format="YYYY-MM-DD" style="width: 100%" placeholder="空=无固定期限" />
              </a-form-item>
            </div>
          </div>
        </div>
        <!-- ② 附件与备注（最后） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">附件与备注</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="合同扫描件">
                <div class="flex items-center gap-2">
                  <a-upload :before-upload="onPickRecordFile" :show-upload-list="false" :disabled="recordFileUploading">
                    <a-button :loading="recordFileUploading">{{ recordFileUploading ? '上传中…' : '选择文件上传' }}</a-button>
                  </a-upload>
                  <a v-if="recordFileId" class="text-xs text-red-500" @click="clearRecordFile">移除</a>
                  <span v-if="recordFileId" class="text-xs text-green-600">{{ recordFileName || (`#${ recordFileId}`) }}</span>
                </div>
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="备注"><a-textarea v-model:value="contractForm.remark" :rows="2" /></a-form-item>
            </div>
          </div>
        </div>
      </a-form>
      <div class="ct-tip">
        履历记录只增不删（审计需要）；录入后「履职概览」的社保一致性、合同有效性与学时汇总会同步变化。
      </div>
    </a-modal>

    <!-- 履职概览 -->
    <a-drawer v-model:open="overviewOpen" class="bid-ind" :width="720" title="履职概览" destroy-on-close>
      <a-spin :spinning="overviewLoading">
        <template v-if="overview">
          <a-descriptions :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item label="姓名">{{ overview.profile?.personName ?? '—' }}</a-descriptions-item>
            <a-descriptions-item label="工号">{{ overview.profile?.personNo ?? '—' }}</a-descriptions-item>
            <a-descriptions-item label="在职状态">
              <a-tag :color="overview.profile?.personnelStatus === 'ACTIVE' ? 'green' : 'default'">
                {{ overview.profile?.personnelStatus ?? '—' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="身份证有效">
              <a-tag :color="overview.idCardValid ? 'green' : 'red'">
                {{ overview.idCardValid ? '有效' : '过期/缺失' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="可用证书">
              {{ overview.usableCertCount ?? 0 }} / {{ overview.totalCertCount ?? 0 }}
            </a-descriptions-item>
            <a-descriptions-item label="累计学时">{{ overview.totalEduHours ?? '—' }}</a-descriptions-item>
            <a-descriptions-item label="最新社保">
              {{ overview.latestSocialSecurityMonth || '—' }}（{{ overview.latestSocialSecurityStatus || '—' }}）
            </a-descriptions-item>
            <a-descriptions-item label="社保缴纳单位">{{ overview.latestSocialSecurityPayer || '—' }}</a-descriptions-item>
            <a-descriptions-item label="社保一致性">
              <a-tag :color="overview.socialSecurityConsistent ? 'green' : 'red'">
                {{ overview.socialSecurityConsistent ? '与主体一致' : '不一致' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="劳动合同">
              <a-tag :color="overview.laborContractValid ? 'green' : 'red'">
                {{ overview.laborContractValid ? '有效' : '缺失/过期' }}
              </a-tag>
              <a-tag v-if="overview.laborContractConsistent === false" color="orange">主体不一致</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="可否参与投标">
              <a-tag :color="overview.usableForTender ? 'green' : 'red'">
                {{ overview.usableForTender ? '可参与' : '不可参与' }}
              </a-tag>
            </a-descriptions-item>
          </a-descriptions>

          <a-alert
            v-if="(overview.risks?.length ?? 0) > 0"
            class="mb-3"
            type="warning"
            show-icon
            :message="`风险项 ${overview.risks?.length} 条`"
            :description="(overview.risks ?? []).join('；')"
          />
          <a-alert v-else class="mb-3" type="success" show-icon message="无风险项" />

          <a-table
            class="ct-table"
            :columns="[
              { title: '证书名称', dataIndex: 'certName', key: 'certName' },
              { title: '类型', dataIndex: 'certType', key: 'certType', width: 140 },
              { title: '状态', dataIndex: 'certStatus', key: 'certStatus', width: 110 },
              { title: '有效期至', dataIndex: 'validTo', key: 'validTo', width: 120 },
              { title: '证态', dataIndex: 'isCurrent', key: 'certState', width: 90 },
            ]"
            :data-source="certRows"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'certState'">
                <a-tag :color="record.isCurrent ? 'green' : 'default'">{{ record.isCurrent ? '现行' : '历史' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'certType'">
                {{ record.certType ? dictLabel('person_cert_type', record.certType) : '—' }}
              </template>
              <template v-else-if="column.key === 'certStatus'">
                <a-tag :color="dictTag('person_cert_status', record.certStatus).color">
                  {{ dictTag('person_cert_status', record.certStatus).label }}
                </a-tag>
              </template>
            </template>
            <template #emptyText><div class="ct-empty">该员工暂无证书：可先为该员工建档证书</div></template>
          </a-table>
        </template>
        <div v-else class="ct-empty">正在读取概览…</div>
      </a-spin>
    </a-drawer>

    <!-- 登记 / 编辑 -->
    <a-modal
      v-model:open="formOpen"
      :title="editing ? `编辑员工证书 #${editing.id}` : '登记员工证书'"
      :width="880"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submitForm"
    >
      <a-form layout="horizontal">
        <!-- ① 归属与标识（编号类归组） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">归属与标识<span class="ct-sg-note">员工 / 企业自动带入 / 证书类型</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="持证员工" required>
                <a-select
                  v-model:value="form.personId"
                  :options="personOptions"
                  :loading="personOptionsLoading"
                  show-search
                  option-filter-prop="label"
                  placeholder="选择员工（来自员工档案）"
                  allow-clear
                  style="width: 100%"
                />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="归属企业">
                <a-input :value="currentSubject?.label || '加载中…'" disabled />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="证书类型" required>
                <a-select
                  v-model:value="form.certType"
                  :options="certTypeOptions"
                  :disabled="certTypeOptions.length === 0"
                  :placeholder="certTypeOptions.length ? '取自字典 person_cert_type' : '字典为空，请先配置'"
                  show-search
                  option-filter-prop="label"
                  allow-clear
                  style="width: 100%"
                />
              </a-form-item>
            </div>
          </div>
        </div>

        <!-- ② 证照信息（元数据） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">证照信息<span class="ct-sg-note">名称 / 编号 / 细分 / 等级</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="证书名称"><a-input v-model:value="form.certName" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="证书编号"><a-input v-model:value="form.certNo" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="证书细分"><a-input v-model:value="form.certSubType" placeholder="安许 A/B/C 等" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="等级"><a-input v-model:value="form.certLevel" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="专业"><a-input v-model:value="form.major" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="学历"><a-input v-model:value="form.eduLevel" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="颁发机构"><a-input v-model:value="form.issuingAuthority" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="颁发日期">
                <a-date-picker v-model:value="form.issueDate" style="width: 100%" value-format="YYYY-MM-DD" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="平台核验"><a-switch v-model:checked="form.platformVerified" /></a-form-item>
            </div>
          </div>
        </div>

        <!-- ③ 有效期与注册（时间 + 注册编号归组） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">有效期与注册<span class="ct-sg-note">起止 / 复审 / 注册单位</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="有效期起">
                <a-date-picker v-model:value="form.validFrom" style="width: 100%" value-format="YYYY-MM-DD" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="有效期至">
                <a-date-picker v-model:value="form.validTo" style="width: 100%" value-format="YYYY-MM-DD" allow-clear />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="下次复审日">
                <a-date-picker v-model:value="form.nextReviewDate" style="width: 100%" value-format="YYYY-MM-DD" />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="注册单位"><a-input v-model:value="form.registerUnit" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="注册单位代码"><a-input v-model:value="form.registerUnitCode" /></a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="注册编号"><a-input v-model:value="form.registerNo" /></a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="核验链接"><a-input v-model:value="form.verifyUrl" /></a-form-item>
            </div>
          </div>
        </div>

        <!-- ④ 附件与备注（最后） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">附件与备注</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="证书文件">
                <div class="flex items-center gap-2">
                  <a-upload
                    :before-upload="onPickCertFile"
                    :show-upload-list="false"
                    :disabled="certFileUploading"
                  >
                    <a-button :loading="certFileUploading">{{ certFileUploading ? '上传中…' : '选择文件上传' }}</a-button>
                  </a-upload>
                  <a v-if="form.fileId" class="text-xs text-red-500" @click="clearCertFile">移除已上传文件</a>
                  <span v-if="form.fileId" class="text-xs text-green-600">已上传，文件编号 #{{ form.fileId }}</span>
                </div>
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
            </div>
          </div>
        </div>
      </a-form>
    </a-modal>

    <!-- 状态流转 -->
    <a-modal
      v-model:open="statusOpen"
      title="员工证书状态流转"
      :width="520"
      :confirm-loading="statusSaving"
      ok-text="确认"
      cancel-text="取消"
      destroy-on-close
      @ok="submitStatus"
    >
      <div class="ct-lb">证书</div>
      <div class="mb-3">
        {{ statusRow?.certName || '—' }} <span class="ct-id">{{ statusRow?.certNo || '' }}</span>
      </div>
      <div class="ct-lb"><span class="ct-ast">*</span>目标状态</div>
      <a-select
        v-model:value="statusTarget"
        class="w-full mb-3"
        :options="certStatusOptions"
        :disabled="certStatusOptions.length === 0"
        placeholder="取自字典 person_cert_status"
      />
      <div class="ct-lb">事由</div>
      <a-textarea v-model:value="statusReason" :rows="2" placeholder="吊销/注销建议填写" />
      <div class="ct-tip">目标状态由后端状态机校验：非法流转（如待办直接生效）会被拒绝并自动刷新；吊销 / 注销 / 退回请务必填写事由留痕。</div>
    </a-modal>

    <!-- 批量续期（契约 POST /personnel-certificate/batch-renew；返回逐条结果，单个失败不影响其余） -->
    <a-modal
      v-model:open="batchRenewOpen"
      :title="`批量续期（已选 ${selectedCertKeys.length} 本证书）`"
      :width="620"
      :confirm-loading="batchRenewSaving"
      ok-text="提交续期"
      cancel-text="取消"
      destroy-on-close
      @ok="submitBatchRenew"
    >
      <a-alert type="info" show-icon class="mb-3" message="逐条处理，单个失败不影响其余" />
      <a-form layout="vertical">
        <a-form-item label="新有效期至">
          <a-date-picker v-model:value="batchRenewForm.newValidTo" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="续期方式">
          <a-select
            v-model:value="batchRenewForm.renewalType"
            :options="dictOptions('renewal_type')"
            allow-clear
            placeholder="取自字典 renewal_type（可空）"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="batchRenewForm.remark" :rows="2" placeholder="如：年度换证，统一延续至新周期" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">
        续期门槛与状态机由后端裁决；每条各自带回乐观锁版本，冲突项会在结果明细里列出，不会静默覆盖。
      </div>
    </a-modal>

    <!-- 证书文件预览：一律 fileId → 业务受控内联出口，不用签名下载地址（否则点预览会变下载） -->
    <PdfPreview
      v-model:open="certPreview.open"
      :file-id="certPreview.fileId"
      :file-name="certPreview.name"
      :title="certPreview.name || '证书文件预览'"
    />
  </div>
</template>

<style scoped>
/* 员工维度看板（与证照台账同款交互） */
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

/* 展开行内的证书子表：整体缩进，形成"员工 → 证书"的层级感 */
.ct-subtable {
  margin-left: 24px;
}
</style>
