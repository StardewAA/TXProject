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
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { certErrorMsg } from '#/api/bidding/certificate';
import {
  changePersonnelCertStatus,
  changePersonnelProfileStatus,
  countPersonnelCertBySubType,
  createPersonnelCert,
  createPersonnelLaborContract,
  createPersonnelProfile,
  createPersonnelSocialSecurity,
  createPersonnelTraining,
  deletePersonnelCert,
  getPersonnelCertExpiring,
  getPersonnelCertPage,
  getPersonnelLaborContractList,
  getPersonnelLegalFloorDays,
  getPersonnelOverview,
  getPersonnelProfilePage,
  getPersonnelSocialSecurityList,
  getPersonnelTrainingList,
  type PersonnelCertApi,
  type PersonnelProfileApi,
  runPersonnelAlertScan,
  updatePersonnelCert,
  updatePersonnelProfile,
} from '#/api/bidding/personnel';
import { checkMultiPost, getPersonnelRisk, type SafetyPermitApi } from '#/api/bidding/safetyPermit';

import { fmtDate } from '../certificate/format';
import SafetyRisk from '../certificate/modules/safety-risk.vue';
import { useCertMeta } from '../certificate/useCertMeta';

import '../certificate/cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { configNumber, configValue, dictLabel, dictOptions, dictTag, metaError } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const tab = ref('list');

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
    entId: undefined,
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
    board.value = await getPersonnelCertExpiring(boardEntId.value, boardDays.value);
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
  if (!countEntId.value || !countCertType.value) {
    message.warning('请选择企业与证书类型');
    return;
  }
  counting.value = true;
  try {
    counts.value = await countPersonnelCertBySubType(countEntId.value, countCertType.value);
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

function openCreate() {
  editing.value = null;
  Object.keys(form).forEach((k) => delete (form as Record<string, unknown>)[k]);
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
    message.warning('请填写持证员工 ID');
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

/* ===== v1.1 §12.5 多岗兼任校验（权限 bidding:personnel:cert:query，已登记，可直接用） ===== */
const multiForm = ref<{ personId?: number; primaryPost?: string; secondaryPost?: string }>({
  personId: undefined,
  primaryPost: undefined,
  secondaryPost: undefined,
});
const multiChecking = ref(false);
const multiResult = ref<null | SafetyPermitApi.MultiPostCheck>(null);
/** 岗位 code 取配置 safety.abc.roles（A/B/C…），不硬编码 */
const roleOptions = computed(() => {
  const v = configValue<unknown>('safety.abc.roles', []);
  return Array.isArray(v) ? v.map((x) => ({ label: String(x), value: String(x) })) : [];
});

async function runMultiCheck() {
  if (!multiForm.value.personId || !multiForm.value.primaryPost || !multiForm.value.secondaryPost) {
    message.warning('请填写员工 ID 并选择主岗位与兼任岗位');
    return;
  }
  if (multiForm.value.primaryPost === multiForm.value.secondaryPost) {
    message.warning('主岗位与兼任岗位不能相同');
    return;
  }
  multiChecking.value = true;
  multiResult.value = null;
  try {
    multiResult.value = await checkMultiPost({
      personId: multiForm.value.personId,
      primaryPost: multiForm.value.primaryPost,
      secondaryPost: multiForm.value.secondaryPost,
    });
  } catch (error: unknown) {
    message.warning(certErrorMsg(error));
  } finally {
    multiChecking.value = false;
  }
}

/* ===== v1.1 §12.6 人员风险（权限 bidding:personnel:profile:query，已登记） ===== */
const riskMap = ref<Record<number, SafetyPermitApi.PersonRisk>>({});
const riskOpen = ref(false);
const riskLoading = ref(false);
const riskRow = ref<null | PersonnelCertApi.Cert>(null);
const riskDetail = ref<null | SafetyPermitApi.PersonRisk>(null);
const riskScanning = ref(false);

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

/** 本页风险体检：对当前页去重后的员工并发取风险（显式动作，非自动轮询） */
async function scanPageRisk() {
  const ids = [...new Set(rows.value.map((r) => r.personId).filter((x): x is number => !!x))];
  if (ids.length === 0) {
    message.warning('当前页没有可体检的员工');
    return;
  }
  riskScanning.value = true;
  try {
    const results = await Promise.all(ids.map((id) => getPersonnelRisk(id).catch(() => null)));
    const next = { ...riskMap.value };
    for (const item of results) {
      if (item?.personId) next[item.personId] = item;
    }
    riskMap.value = next;
    message.success(`已体检 ${ids.length} 名员工`);
  } finally {
    riskScanning.value = false;
  }
}

/* ================= 员工档案（profile）与履历（培训 / 社保 / 劳动合同） ================= */

/** 档案状态码表（逐字取自 PersonnelStatusEnum） */
const PROFILE_STATUS_OPTIONS = ['ACTIVE', 'SUSPENDED', 'LEAVE', 'RETIRED'].map((s) => ({ label: s, value: s }));
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
  { title: '工号', dataIndex: 'personNo', key: 'personNo', width: 120 },
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
    message.warning('企业主体 id（entId）必填');
    return;
  }
  if (!profileForm.personName?.trim()) {
    message.warning('姓名必填');
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

/* ===== 履职概览（证书 / 社保 / 合同 / 学时 / 风险） ===== */
const overviewOpen = ref(false);
const overviewLoading = ref(false);
const overview = ref<null | PersonnelProfileApi.Overview>(null);

async function openOverview(row: PersonnelProfileApi.Profile) {
  overviewOpen.value = true;
  overviewLoading.value = true;
  overview.value = null;
  try {
    overview.value = await getPersonnelOverview(row.id);
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
  { title: '证书编号', dataIndex: 'certNo', key: 'certNo', width: 150 },
  { title: '证书有效期至', dataIndex: 'certValidTo', key: 'certValidTo', width: 130 },
];
const socialColumns = [
  { title: '缴费月份', dataIndex: 'payMonth', key: 'payMonth', width: 110 },
  { title: '缴纳单位', dataIndex: 'payerName', key: 'payerName', width: 200 },
  { title: '统一社会信用代码', dataIndex: 'payerCreditCode', key: 'payerCreditCode', width: 190 },
  { title: '状态', dataIndex: 'payStatus', key: 'payStatus', width: 110 },
  { title: '缴费基数', dataIndex: 'payBase', key: 'payBase', width: 120 },
  { title: '缴费金额', dataIndex: 'payAmount', key: 'payAmount', width: 120 },
  { title: '社保号', dataIndex: 'socialNo', key: 'socialNo', width: 150 },
  { title: '来源', dataIndex: 'sourceChannel', key: 'sourceChannel', width: 120 },
];
const contractColumns = [
  { title: '合同编号', dataIndex: 'contractNo', key: 'contractNo', width: 170 },
  { title: '类型', dataIndex: 'contractType', key: 'contractType', width: 120 },
  { title: '签订日期', dataIndex: 'signDate', key: 'signDate', width: 120 },
  { title: '起止', key: 'period', width: 200 },
  { title: '状态', dataIndex: 'contractStatus', key: 'contractStatus', width: 110 },
  { title: '岗位', dataIndex: 'positionName', key: 'positionName', width: 150 },
  { title: '附件', dataIndex: 'fileId', key: 'fileId', width: 100 },
];

async function loadRecords() {
  if (!recordPersonId.value) {
    message.warning('请先填写员工 id（personId）');
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

function openRecordCreate() {
  if (!recordPersonId.value) {
    message.warning('请先填写并查询员工 id（personId）');
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
  recFormOpen.value = true;
}

async function submitRecord() {
  if (recordType.value === 'training' && !trainingForm.trainingName?.trim()) {
    message.warning('培训名称必填');
    return;
  }
  if (recordType.value === 'socialSecurity' && !socialForm.payMonth?.trim()) {
    message.warning('缴费月份（yyyy-MM）必填');
    return;
  }
  if (recordType.value === 'laborContract' && !contractForm.contractNo?.trim()) {
    message.warning('合同编号必填');
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

/** 页签懒加载：首次进入档案页签才拉档案列表 */
watch(tab, (t) => {
  if (t === 'profile' && profileRows.value.length === 0) void loadProfiles();
});

onMounted(() => {
  void load();
  syncBoardDays();
  void loadFloors();
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
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">员工资质</h2>
        <p class="ct-sub">
          人证合一：把「人」和「证」关联起来，支持按员工、类型、状态与到期窗口检索，供安许证三类人员（A/B/C）配备自查。
          本页覆盖员工档案、履历（培训/社保/劳动合同）与员工证书（契约 §8）；档案的身份证只写不读（回传脱敏值），
          在职状态按 PersonnelStatusEnum 状态机流转，离职/退休不计入资质人数与配证候选。
        </p>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:personnel:cert:create')" type="primary" @click="openCreate">登记证书</a-button>
        <a-button v-if="can('bidding:personnel:alert-run')" @click="confirmScan">到期扫描</a-button>
        <a-button @click="load">刷新</a-button>
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
          <div class="ct-filter">
            <a-input-number v-model:value="profileQuery.entId" :min="1" placeholder="企业主体 id" style="width: 160px" />
            <a-input v-model:value="profileQuery.personName" placeholder="姓名" style="width: 120px" />
            <a-input v-model:value="profileQuery.personNo" placeholder="工号" style="width: 120px" />
            <a-input v-model:value="profileQuery.deptName" placeholder="部门" style="width: 130px" />
            <a-select
              v-model:value="profileQuery.personnelStatus"
              placeholder="在职状态"
              allow-clear
              :options="PROFILE_STATUS_OPTIONS"
              style="width: 140px"
            />
            <a-select
              v-model:value="profileQuery.eduLevel"
              placeholder="学历"
              allow-clear
              :options="dictOptions('edu_level')"
              style="width: 120px"
            />
            <a-button type="primary" @click="profileQuery.pageNo = 1; loadProfiles()">查询</a-button>
            <a-button v-if="can('bidding:personnel:profile:create')" type="primary" @click="openProfileCreate">
              建档
            </a-button>
            <span class="ct-panel-meta">身份证仅提交、回读脱敏；离职/退休人员不计入资质人数</span>
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
                  <a v-if="can('bidding:personnel:profile:query')" @click="openOverview(record)">履职概览</a>
                  <a v-if="can('bidding:personnel:profile:update')" @click="openProfileEdit(record)">编辑</a>
                  <a
                    v-if="can('bidding:personnel:profile:update') && (PROFILE_TRANSITIONS[record.personnelStatus] ?? []).length > 0"
                    @click="openProfileStatus(record)"
                  >
                    状态流转
                  </a>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">按主体/姓名/工号筛选，或先建一份员工档案</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="record" tab="② 履历（培训·社保·劳动合同）">
        <div class="ct-panel">
          <div class="ct-filter">
            <a-input-number v-model:value="recordPersonId" :min="1" placeholder="员工 id（personId）" style="width: 190px" />
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
              <template #emptyText><div class="ct-empty">该员工暂无培训记录</div></template>
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
              <template #emptyText><div class="ct-empty">该员工暂无劳动合同记录</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="list" tab="③ 员工证书">
        <div class="ct-panel">
          <div class="ct-filter">
            <a-input-number v-model:value="query.personId" :min="1" placeholder="员工 ID" style="width: 130px" />
            <a-input-number v-model:value="query.entId" :min="1" placeholder="企业 ID" style="width: 130px" />
            <a-select
              v-model:value="query.certType"
              :options="certTypeOptions"
              placeholder="证书类型"
              style="width: 180px"
              allow-clear
              show-search
              option-filter-prop="label"
            />
            <a-select
              v-model:value="query.certStatus"
              :options="certStatusOptions"
              placeholder="状态"
              style="width: 140px"
              allow-clear
            />
            <a-input v-model:value="query.certNo" placeholder="证书编号（精确）" style="width: 170px" allow-clear />
            <a-input v-model:value="query.major" placeholder="专业（模糊）" style="width: 140px" allow-clear />
            <a-input-number v-model:value="query.expiringDays" :min="1" placeholder="到期窗口(天)" style="width: 140px" />
            <a-button type="primary" @click="load">查询</a-button>
            <a-button @click="resetQuery">重置</a-button>
            <a-button :loading="riskScanning" @click="scanPageRisk">本页风险体检</a-button>
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
                    {{ record.personName || `员工 #${record.personId}` }}
                    <a-tag v-if="riskMap[record.personId]?.abnormal" color="red">异常</a-tag>
                    <a-tag v-else-if="riskMap[record.personId]?.personRiskFlag" color="orange">
                      {{ dictLabel('person_risk_flag', riskMap[record.personId]?.personRiskFlag) }}
                    </a-tag>
                  </div>
                  <div class="ct-id">{{ record.certName || '—' }} · {{ record.certNo || '—' }}</div>
                </template>
                <template v-else-if="column.key === 'certType'">
                  {{ record.certType ? dictLabel('person_cert_type', record.certType) : '—' }}
                  <div class="ct-panel-meta">{{ record.certSubType || record.certLevel || '' }}</div>
                </template>
                <template v-else-if="column.key === 'validity'">
                  <div>{{ record.validFrom || '—' }} ~ {{ record.validTo || '长期' }}</div>
                  <a-tag v-if="remainTag(record)" :color="remainTag(record)!.color">{{ remainTag(record)!.label }}</a-tag>
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
                  <div v-if="record.verifiedAt" class="ct-panel-meta">{{ record.verifiedAt }}</div>
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space size="small">
                    <a v-if="can('bidding:personnel:cert:update')" @click="openEdit(record)">编辑</a>
                    <a v-if="can('bidding:personnel:cert:update')" @click="openStatus(record)">状态</a>
                    <a v-if="can('bidding:personnel:cert:query')" @click="openPersonRisk(record)">风险</a>
                    <a v-if="can('bidding:personnel:cert:delete')" style="color: #c6362e" @click="confirmDelete(record)">
                      删除
                    </a>
                  </a-space>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">暂无员工证书数据</div></template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="board" tab="④ 到期看板">
        <div class="ct-panel">
          <div class="ct-filter">
            <a-input-number v-model:value="boardEntId" :min="1" placeholder="企业 ID（空=全部）" style="width: 180px" />
            <a-input-number
              v-model:value="boardDays"
              :min="1"
              :max="730"
              placeholder="到期窗口天数"
              style="width: 160px"
              @change="boardDaysTouched = true"
            />
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
                    <a-tag :color="remainTag(b)?.color">{{ remainTag(b)?.label }}</a-tag>
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
          <div class="ct-filter">
            <a-input-number v-model:value="countEntId" :min="1" placeholder="企业 ID（必填）" style="width: 180px" />
            <a-select
              v-model:value="countCertType"
              :options="certTypeOptions"
              placeholder="证书类型（如安许考核合格证）"
              style="width: 260px"
              allow-clear
              show-search
              option-filter-prop="label"
            />
            <a-button type="primary" :loading="counting" @click="runCount">统计可用持证人数</a-button>
            <span class="ct-ml">配备要求取自配置 safety.abc.required</span>
          </div>
          <div class="ct-panel-b">
            <!-- v1.1 §12.1~§12.4 安许风险联动（读取/重算/快照/宽限） -->
            <SafetyRisk class="mb-3" :ent-id="countEntId ?? null" />
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

      <a-tab-pane key="multi" tab="多岗兼任校验">
        <div class="ct-panel">
          <div class="ct-filter">
            <a-input-number v-model:value="multiForm.personId" :min="1" placeholder="员工 ID（必填）" style="width: 160px" />
            <a-select
              v-model:value="multiForm.primaryPost"
              :options="roleOptions"
              placeholder="主岗位"
              style="width: 140px"
              allow-clear
              :disabled="roleOptions.length === 0"
            />
            <a-select
              v-model:value="multiForm.secondaryPost"
              :options="roleOptions"
              placeholder="兼任岗位"
              style="width: 140px"
              allow-clear
              :disabled="roleOptions.length === 0"
            />
            <a-button type="primary" :loading="multiChecking" @click="runMultiCheck">校验</a-button>
            <span class="ct-ml">岗位 code 取配置 safety.abc.roles；兼任须持对应考核合格证</span>
          </div>
          <div class="ct-panel-b">
            <template v-if="multiResult">
              <a-alert
                :type="multiResult.pass ? 'success' : 'error'"
                show-icon
                :message="multiResult.pass ? '兼任合规' : '兼任不合规'"
                :description="multiResult.pass
                  ? `匹配证书 #${multiResult.matchedCertId ?? '—'}（${multiResult.requiredCertType || '—'} / ${multiResult.requiredCertSubType || '—'}）· ${multiResult.checkedAt || ''}`
                  : (multiResult.failReason || '未匹配到对应考核合格证')"
              />
              <div class="ct-tip">校验结论会落库留痕（幂等，可重复校验）。</div>
            </template>
            <div v-else class="ct-empty">选择员工与主/兼任岗位后校验：校验结论不阻断登记，仅提示留痕</div>
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
                  { title: '证照类别 code', dataIndex: 'code', key: 'code' },
                  { title: '法定下限（天）', dataIndex: 'days', key: 'days', width: 180 },
                ]"
                :data-source="Object.entries(floors).map(([code, days]) => ({ code, days }))"
                :pagination="false"
                row-key="code"
                size="small"
              >
                <template #emptyText><div class="ct-empty">暂无下限配置</div></template>
              </a-table>
            </a-spin>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- v1.1 §12.6 人员风险抽屉（不自动拒绝，仅提示留痕） -->
    <a-drawer v-model:open="riskOpen" class="bid-ind" :width="620" title="人员风险" destroy-on-close>
      <a-spin :spinning="riskLoading">
        <div class="ct-lb">员工</div>
        <div class="mb-3">
          {{ riskRow?.personName || `员工 #${riskRow?.personId}` }}
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
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="企业主体 id（必填）">
              <a-input-number v-model:value="profileForm.entId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="姓名（必填）"><a-input v-model:value="profileForm.personName" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="工号"><a-input v-model:value="profileForm.personNo" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item :label="profileForm.id ? '身份证号（留空=不修改）' : '身份证号'">
              <a-input v-model:value="profileForm.idCard" placeholder="18 位，后端唯一性校验" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="身份证有效期至">
              <a-date-picker v-model:value="profileForm.idCardValidTo" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="性别">
              <a-select v-model:value="profileForm.gender" allow-clear :options="dictOptions('gender')" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="出生日期">
              <a-date-picker v-model:value="profileForm.birthDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="手机号"><a-input v-model:value="profileForm.mobile" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="邮箱"><a-input v-model:value="profileForm.email" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="学历">
              <a-select v-model:value="profileForm.eduLevel" allow-clear :options="dictOptions('edu_level')" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="所学专业"><a-input v-model:value="profileForm.eduMajor" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="毕业院校"><a-input v-model:value="profileForm.school" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="毕业日期">
              <a-date-picker v-model:value="profileForm.graduateDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="职称等级"><a-input v-model:value="profileForm.titleLevel" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="职称专业"><a-input v-model:value="profileForm.titleMajor" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="参加工作日期">
              <a-date-picker v-model:value="profileForm.workStartDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="入职日期">
              <a-date-picker v-model:value="profileForm.joinDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="部门"><a-input v-model:value="profileForm.deptName" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <!-- 岗位名称 / 人事档案号：Profile 出参本来就有，SaveReq 此前缺声明导致"看得见存不下"；
                 2026-09-25 由"类型对等"门禁（PersonnelProfileSaveReqVO ← SaveReq）发现后补齐。 -->
            <a-form-item label="岗位名称"><a-input v-model:value="profileForm.positionName" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="人事档案号"><a-input v-model:value="profileForm.archiveNo" /></a-form-item>
          </a-col>
          <a-col :span="8">
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
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注"><a-textarea v-model:value="profileForm.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
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
      <a-form v-if="recordType === 'training'" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="培训名称（必填）"><a-input v-model:value="trainingForm.trainingName" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="培训类型"><a-input v-model:value="trainingForm.trainingType" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="主办方"><a-input v-model:value="trainingForm.organizer" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="开始日期">
              <a-date-picker v-model:value="trainingForm.startDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="结束日期">
              <a-date-picker v-model:value="trainingForm.endDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="学时">
              <a-input-number v-model:value="trainingForm.eduHours" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="成绩">
              <a-input-number v-model:value="trainingForm.score" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="结果"><a-input v-model:value="trainingForm.result" placeholder="如：合格" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="证书编号"><a-input v-model:value="trainingForm.certNo" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="证书有效期至">
              <a-date-picker v-model:value="trainingForm.certValidTo" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="附件文件 id">
              <a-input-number v-model:value="trainingForm.fileId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注"><a-textarea v-model:value="trainingForm.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>

      <a-form v-else-if="recordType === 'socialSecurity'" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="缴费月份（必填，yyyy-MM）">
              <a-input v-model:value="socialForm.payMonth" placeholder="如 2026-09" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="缴纳单位"><a-input v-model:value="socialForm.payerName" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="单位统一社会信用代码">
              <a-input v-model:value="socialForm.payerCreditCode" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="缴费状态"><a-input v-model:value="socialForm.payStatus" placeholder="如：正常" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="缴费基数">
              <a-input-number v-model:value="socialForm.payBase" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="缴费金额">
              <a-input-number v-model:value="socialForm.payAmount" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="社保号"><a-input v-model:value="socialForm.socialNo" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="来源渠道">
              <a-input v-model:value="socialForm.sourceChannel" placeholder="如：社保局导出" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="证据文件 id">
              <a-input-number v-model:value="socialForm.evidenceFileId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注"><a-textarea v-model:value="socialForm.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>

      <a-form v-else layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="合同编号（必填）"><a-input v-model:value="contractForm.contractNo" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="合同类型">
              <a-input v-model:value="contractForm.contractType" placeholder="如：固定期限" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="岗位"><a-input v-model:value="contractForm.positionName" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="签订日期">
              <a-date-picker v-model:value="contractForm.signDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="开始日期">
              <a-date-picker v-model:value="contractForm.startDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="结束日期（空=无固定期限）">
              <a-date-picker v-model:value="contractForm.endDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="合同状态">
              <a-input v-model:value="contractForm.contractStatus" placeholder="如：履行中" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="附件文件 id">
              <a-input-number v-model:value="contractForm.fileId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注"><a-textarea v-model:value="contractForm.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
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
            ]"
            :data-source="overview.certificates ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'certType'">
                {{ record.certType ? dictLabel('person_cert_type', record.certType) : '—' }}
              </template>
              <template v-else-if="column.key === 'certStatus'">
                <a-tag :color="dictTag('person_cert_status', record.certStatus).color">
                  {{ dictTag('person_cert_status', record.certStatus).label }}
                </a-tag>
              </template>
            </template>
            <template #emptyText><div class="ct-empty">该员工暂无证书</div></template>
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
      <a-row :gutter="16">
        <a-col :span="8">
          <div class="ct-lb"><span class="ct-ast">*</span>持证员工 ID</div>
          <a-input-number v-model:value="form.personId" class="w-full mb-3" :min="1" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">归属企业 ID</div>
          <a-input-number v-model:value="form.entId" class="w-full mb-3" :min="1" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb"><span class="ct-ast">*</span>证书类型</div>
          <a-select
            v-model:value="form.certType"
            class="w-full mb-3"
            :options="certTypeOptions"
            :disabled="certTypeOptions.length === 0"
            :placeholder="certTypeOptions.length ? '取自字典 person_cert_type' : '字典为空，请先配置'"
            show-search
            option-filter-prop="label"
            allow-clear
          />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">证书名称</div>
          <a-input v-model:value="form.certName" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">证书编号</div>
          <a-input v-model:value="form.certNo" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">证书细分（安许 A/B/C 等）</div>
          <a-input v-model:value="form.certSubType" class="mb-3" placeholder="配备统计依赖该字段" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">等级</div>
          <a-input v-model:value="form.certLevel" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">专业</div>
          <a-input v-model:value="form.major" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">学历</div>
          <a-input v-model:value="form.eduLevel" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">颁发机构</div>
          <a-input v-model:value="form.issuingAuthority" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">颁发日期</div>
          <a-date-picker v-model:value="form.issueDate" class="w-full mb-3" value-format="YYYY-MM-DD" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">有效期起</div>
          <a-date-picker v-model:value="form.validFrom" class="w-full mb-3" value-format="YYYY-MM-DD" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">有效期止</div>
          <a-date-picker v-model:value="form.validTo" class="w-full mb-3" value-format="YYYY-MM-DD" allow-clear />
          <div class="ct-tip">部分证书类型必须登记有效期（到期预警依赖该字段）。</div>
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">下次复审日</div>
          <a-date-picker v-model:value="form.nextReviewDate" class="w-full mb-3" value-format="YYYY-MM-DD" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">注册单位</div>
          <a-input v-model:value="form.registerUnit" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">注册单位代码</div>
          <a-input v-model:value="form.registerUnitCode" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">注册编号</div>
          <a-input v-model:value="form.registerNo" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">平台核验</div>
          <a-switch v-model:checked="form.platformVerified" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">核验链接</div>
          <a-input v-model:value="form.verifyUrl" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">证书文件 fileId</div>
          <a-input-number v-model:value="form.fileId" class="w-full mb-3" :min="1" />
          <div class="ct-tip">先经平台文件上传接口取得 fileId 再回填。</div>
        </a-col>
        <a-col :span="24">
          <div class="ct-lb">备注</div>
          <a-textarea v-model:value="form.remark" :rows="2" />
        </a-col>
      </a-row>
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
    </a-modal>
  </div>
</template>
