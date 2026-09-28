<script setup lang="ts">
/* 企业资质 · 详情独立页（设计约束：详情用独立跳转页面，非抽屉；编辑内联到同页，不建独立编辑页）
 *
 * 路由（见 router/routes/core.ts）：
 *   · /bidding/qualification/detail/:id   只读态；?mode=edit 切编辑态（query 标识，刷新不丢）
 *   · /bidding/qualification/create       建档：初始即编辑态、字段为空、不传 entId（后端取本租户主体）
 * 同一组件承载「详情 / 建档」两种场景，靠路由名区分。
 *
 * 承接关系：原列表页「资质详情」抽屉（主档信息 / 绑定证照 / 最近一次达标核查）已删除；
 * 原「编辑企业资质」弹窗亦随详情页上线删除，其字段集**整体并入本页编辑态**——
 * 弹窗字段不迁移会造成能力回退（尤其"关联证照"bindedCertId 原弹窗可改，本页必须保留该入口）。
 *
 * 规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G2；《招投标模块开发规范》§4.5
 * （乐观锁 version 必须回传）、§5 三-7（字典 code 存储，前端按字典映射展示）。
 * 口径：LocalDate（issueDate/validTo/submitDate/dueDate/newValidTo/regValidTo）是 'yyyy-MM-dd' 字符串；
 *   仅 latestReview.reviewedAt 是 LocalDateTime（epoch 毫秒），需过 fmtDateTime。 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { certErrorCode, certErrorMsg, getCertificatePage } from '#/api/bidding/certificate';
import {
  createQualification,
  getQualification,
  getQualificationAlertPage,
  getQualificationApplyPage,
  getQualificationStaffPage,
  QUAL_ERR,
  QualificationApi,
  submitQualificationRenew,
  updateQualification,
} from '#/api/bidding/qualification';

import { fmtDate, fmtDateTime } from '../certificate/format';
import CertFiles from '../certificate/modules/cert-files.vue';
import { useCertMeta } from '../certificate/useCertMeta';

import '../certificate/cert.css';

const route = useRoute();
const router = useRouter();
const { dictLabel, dictTag, dictOptions } = useCertMeta();
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();

/** 菜单 60005200「企业资质」的落地路径：父级 60005000 path='certificate' + 本项 path='qualification'
 *  （见 sql/master-credential-sync.sql）。与静态详情路由 /bidding/qualification/* 不同构，是既有约定。 */
const LIST_PATH = '/certificate/qualification';
/** 菜单 60005500「安许风险联动」的落地路径（同上：certificate + safety-risk）。 */
const SAFETY_RISK_PATH = '/certificate/safety-risk';

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

/* ===== 字典展示（后端字典优先，缺字典回退原始 code，前端不自造中文枚举）===== */
function tagOf(dictType: string, code?: null | string): { color: string; label: string } {
  if (!code) return { color: 'default', label: '—' };
  if (dictOptions(dictType).length > 0) return dictTag(dictType, code);
  return { color: 'default', label: code };
}
function textOf(dictType: string, code?: null | string): string {
  if (!code) return '—';
  if (dictOptions(dictType).length > 0) return dictLabel(dictType, code);
  return code;
}
function daysColor(days: number): string {
  if (days < 0) return 'red';
  if (days <= 30) return 'orange';
  if (days <= 90) return 'gold';
  return 'green';
}

/* ===== 场景判定：建档 / 详情；只读 / 编辑 ===== */
const isCreate = computed(() => route.name === 'BiddingQualificationCreate');
const qualId = computed(() => String(route.params.id ?? ''));
/** 编辑态由 URL query 标识（?mode=edit），刷新不丢失；建档页恒为编辑态。 */
const editMode = computed(() => isCreate.value || route.query.mode === 'edit');

/* ===== 详情数据 ===== */
const loading = ref(false);
const error = ref('');
const detail = ref<null | QualificationApi.Detail>(null);

/* ===== 编辑表单（字段集 = 原「编辑」弹窗 ∪ approvalScope）===== */
const editForm = reactive<QualificationApi.SaveReq>({
  id: undefined,
  version: undefined,
  entId: undefined,
  qualSerial: '',
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
const saving = ref(false);
const savingAndSubmit = ref(false);

/** 字段级校验错误（建档/编辑提交时填充；取代"3 秒即逝的 warning"，见后端契约 §5） */
const formErrors = reactive<Record<'certNo' | 'qualCategory' | 'qualGrade' | 'qualSerial', string>>({
  certNo: '',
  qualCategory: '',
  qualGrade: '',
  qualSerial: '',
});

/** 编辑字段快照（JSON）：用于"未保存修改"判定 —— 与快照不同即视为 dirty。
 *  保存成功后重取快照 → 离开拦截自然失效（避免"刚存完又被问一遍"）。 */
const snapshot = ref('');
function takeSnapshot(): string {
  return JSON.stringify({
    qualSerial: editForm.qualSerial ?? null,
    qualCategory: editForm.qualCategory ?? null,
    qualGrade: editForm.qualGrade ?? null,
    certNo: editForm.certNo ?? null,
    issuingAuthority: editForm.issuingAuthority ?? null,
    issueDate: editForm.issueDate ?? null,
    validTo: editForm.validTo ?? null,
    approvalScope: editForm.approvalScope ?? null,
    bindedCertId: editForm.bindedCertId ?? null,
    remark: editForm.remark ?? null,
  });
}
const isDirty = computed(() => editMode.value && takeSnapshot() !== snapshot.value);

function fillFormFrom(d: null | QualificationApi.Detail): void {
  Object.assign(editForm, {
    id: d?.id,
    version: d?.version,
    entId: d?.entId,
    qualSerial: d?.qualSerial ?? '',
    qualCategory: d?.qualCategory ?? '',
    qualGrade: d?.qualGrade ?? '',
    certNo: d?.certNo ?? '',
    issuingAuthority: d?.issuingAuthority ?? undefined,
    issueDate: d?.issueDate ?? undefined,
    validTo: d?.validTo ?? undefined,
    approvalScope: d?.approvalScope ?? undefined,
    bindedCertId: d?.bindedCertId ?? undefined,
    remark: d?.remark ?? undefined,
  });
  snapshot.value = takeSnapshot();
}
/** 建档：字段清空 + 快照置空态（此后任何输入都算 dirty）。
 *  2026-10-01 补：建档初始**预选「施工」资质序列**（CONSTRUCTION，列表页「企业资质建档」弹窗同口径）；
 *  预选后重拍快照，用户未改动表单即不算 dirty（离开不再误拦）。 */
function resetFormForCreate(): void {
  fillFormFrom(null);
  if (isCreate.value) {
    editForm.qualSerial = 'CONSTRUCTION';
    snapshot.value = takeSnapshot();
  }
}

/* ===== 只读明细数据：关联人员 / 安许预警 / 变更历史（延续申报单）===== */
const staffRows = ref<QualificationApi.Staff[]>([]);
const staffTotal = ref(0);
const staffLoading = ref(false);
const staffAllOpen = ref(false);

const alerts = ref<QualificationApi.Alert[]>([]);
const alertTotal = ref(0);
const alertLoading = ref(false);

const applies = ref<QualificationApi.ApplyRecord[]>([]);
const applyTotal = ref(0);
const applyLoading = ref(false);
const historyLoaded = ref(false);
const historyOpen = ref(false);

async function loadStaff(): Promise<void> {
  if (!qualId.value) return;
  staffLoading.value = true;
  try {
    const page = await getQualificationStaffPage({ qualId: Number(qualId.value), pageNo: 1, pageSize: 100 });
    staffRows.value = page.list ?? [];
    staffTotal.value = page.total ?? 0;
  } catch {
    staffRows.value = [];
    staffTotal.value = 0;
  } finally {
    staffLoading.value = false;
  }
}

/** 安许风险联动：按本企业主体取"未处理"预警（后端预警分页只支持 entId 维度，无 qualId 过滤）。 */
async function loadAlerts(): Promise<void> {
  const entId = detail.value?.entId;
  if (!entId) {
    alerts.value = [];
    alertTotal.value = 0;
    return;
  }
  alertLoading.value = true;
  try {
    const page = await getQualificationAlertPage({ entId, handled: false, pageNo: 1, pageSize: 5 });
    alerts.value = page.list ?? [];
    alertTotal.value = page.total ?? 0;
  } catch {
    alerts.value = [];
    alertTotal.value = 0;
  } finally {
    alertLoading.value = false;
  }
}

/** 变更历史：后端无资质主档变更史表，按契约用「延续申报单」作为变更史（applyType/状态/经办人/备注）。 */
async function loadApplies(): Promise<void> {
  if (!qualId.value) return;
  applyLoading.value = true;
  try {
    const page = await getQualificationApplyPage({ qualId: Number(qualId.value), pageNo: 1, pageSize: 50 });
    applies.value = page.list ?? [];
    applyTotal.value = page.total ?? 0;
    historyLoaded.value = true;
  } catch {
    applies.value = [];
    applyTotal.value = 0;
  } finally {
    applyLoading.value = false;
  }
}

/* ===== 加载 ===== */
async function load(): Promise<void> {
  if (isCreate.value) {
    resetFormForCreate();
    return;
  }
  if (!qualId.value) return;
  loading.value = true;
  error.value = '';
  try {
    const d = await getQualification(qualId.value);
    detail.value = d;
    fillFormFrom(d);
    await Promise.all([loadStaff(), loadAlerts()]);
  } catch (e) {
    error.value = certErrorMsg(e) || '资质详情读取失败';
  } finally {
    loading.value = false;
  }
}
onMounted(load);
/** 仅响应路由参数变化（列表连续点两条详情时组件被复用）：不能监听 fullPath ——
 *  切换 ?mode=edit 也是 query 变化，监听它会在进出编辑态时各触发一次多余重载。 */
watch(
  () => route.params.id,
  (n, o) => {
    if (n !== o) void load();
  },
);

/* ===== 编辑态切换 ===== */
function enterEdit(): void {
  void router.replace({ query: { ...route.query, mode: 'edit' } });
}
function exitEdit(): void {
  const q = { ...route.query };
  delete q.mode;
  void router.replace({ query: q });
}
async function cancelEdit(): Promise<void> {
  if (isDirty.value) {
    const ok = await confirmBox('放弃修改？', '当前有未保存的修改，取消后本次编辑内容将全部丢弃。', '放弃修改');
    if (!ok) return;
  }
  if (isCreate.value) {
    void router.replace(LIST_PATH);
    return;
  }
  if (detail.value) fillFormFrom(detail.value);
  exitEdit();
}

function confirmBox(title: string, content: string, okText: string): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title,
      content,
      okText,
      okType: 'danger',
      cancelText: '留在本页',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

/* ===== 保存 ===== */
/** 提交按钮共用保存逻辑；submitAfter=true 表示"保存并提交审批"。 */
async function save(submitAfter = false): Promise<void> {
  formErrors.qualSerial = editForm.qualSerial ? '' : '资质序列为必填';
  formErrors.qualCategory = editForm.qualCategory ? '' : '资质类别为必填';
  formErrors.qualGrade = editForm.qualGrade ? '' : '资质等级为必填';
  formErrors.certNo = editForm.certNo ? '' : '资质证书编号为必填';
  const firstErr = (['qualSerial', 'qualCategory', 'qualGrade', 'certNo'] as const).find((k) => formErrors[k]);
  if (firstErr) {
    message.warning('请先补齐下方标红的必填字段');
    const el = document.querySelector(
      `[data-qf="${firstErr}"] input, [data-qf="${firstErr}"] .ant-select-selector`,
    ) as HTMLElement | null;
    el?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    el?.focus({ preventScroll: true });
    return;
  }
  if (submitAfter) savingAndSubmit.value = true;
  else saving.value = true;
  try {
    if (isCreate.value) {
      const newId = await createQualification({ ...editForm });
      message.success('建档成功');
      // 建档成功：清 dirty 后跳该资质详情页（详情页「返回」再回列表），避免深链进 /create 时 back() 退出应用
      snapshot.value = takeSnapshot();
      void router.replace(`/bidding/qualification/detail/${newId}`);
      return;
    }
    // 更新：id/version 必传（后端缺任一报 QUALIFICATION_PARAM_INVALID）
    await updateQualification({ ...editForm });
    message.success('已保存');
    // 先对齐快照（=清 dirty），再收起编辑态并重取：
    // ① 否则"刚保存完"会被 onBeforeRouteLeave 当成未保存修改再问一遍；
    // ② 状态可能被后端按新 valid_to 重算（EXPIRED↔VALID），重取保证本页与列表口径一致。
    snapshot.value = takeSnapshot();
    exitEdit();
    await load();
    if (submitAfter) {
      message.info('修改已保存。可在此发起延续申报，提交后进入审批流程。');
      openRenew();
    }
  } catch (e) {
    const code = certErrorCode(e);
    if (code === QUAL_ERR.QUALIFICATION_DATA_MODIFIED) {
      message.error('资质已被他人修改，正在刷新最新数据，请确认后重新保存');
      void load();
    } else if (code === QUAL_ERR.QUALIFICATION_NO_DUPLICATE) {
      message.error('同序列/类别/等级的证书编号已存在，请核对后重填');
    } else if (code === QUAL_ERR.QUALIFICATION_NOT_EXISTS) {
      message.error('资质不存在，返回列表');
      void router.replace(LIST_PATH);
    } else {
      message.error(certErrorMsg(e) || '保存失败');
    }
  } finally {
    saving.value = false;
    savingAndSubmit.value = false;
  }
}

/* ===== 发起续期（沿用既有 /renew 申报流，本次不改动其逻辑）===== */
const renewOpen = ref(false);
const renewSaving = ref(false);
const renewForm = reactive<{ dueDate?: string; qualificationId: number; remark?: string; version: number }>({
  qualificationId: 0,
  version: 0,
});
function openRenew(): void {
  const d = detail.value;
  if (!d) return;
  if (d.version === undefined) {
    message.warning('该资质缺少 version（乐观锁），请刷新后重试');
    return;
  }
  if (d.hasPendingApply) {
    message.warning('该资质已有在途延续申报，请先在列表页「延续申报台账」处理');
    return;
  }
  Object.assign(renewForm, {
    qualificationId: d.id,
    version: d.version,
    dueDate: d.validTo ?? undefined,
    remark: undefined,
  });
  renewOpen.value = true;
}
async function submitRenew(): Promise<void> {
  renewSaving.value = true;
  try {
    await submitQualificationRenew({ ...renewForm });
    message.success('延续申报已提交');
    renewOpen.value = false;
    await load();
  } catch (e) {
    message.error(certErrorMsg(e) || '延续申报提交失败');
  } finally {
    renewSaving.value = false;
  }
}

/* ===== 关联证照检索（原「编辑」弹窗能力，本页保留）===== */
const certLinkOptions = ref<Array<{ label: string; value: number }>>([]);
const certLinkLoading = ref(false);
async function loadCertLinkOptions(keyword?: string): Promise<void> {
  certLinkLoading.value = true;
  try {
    const page = await getCertificatePage({ certName: keyword || undefined, pageNo: 1, pageSize: 20 });
    certLinkOptions.value = (page.list ?? []).map((c) => ({
      label: `${c.certName ?? '证照'}${c.certNo ? ` · ${c.certNo}` : ''}`,
      value: c.id,
    }));
  } catch {
    certLinkOptions.value = [];
  } finally {
    certLinkLoading.value = false;
  }
}
/** 进入编辑态时预载一次「关联证照」候选；immediate 让建档页（挂载即编辑态）也能预载。 */
watch(
  () => editMode.value,
  (v) => {
    if (v && certLinkOptions.value.length === 0) void loadCertLinkOptions('');
  },
  { immediate: true },
);

/* ===== 变更历史展开（懒加载）===== */
function toggleHistory(): void {
  historyOpen.value = !historyOpen.value;
  if (historyOpen.value && !historyLoaded.value) void loadApplies();
}

/* ===== 离开拦截 ===== */
onBeforeRouteLeave(() => {
  if (!isDirty.value) return true;
  return confirmBox('有未保存的修改，确认离开？', '离开后本次编辑内容将全部丢失。', '离开');
});
function onBeforeUnload(e: BeforeUnloadEvent): void {
  if (!isDirty.value) return;
  e.preventDefault();
  e.returnValue = '';
}
onMounted(() => window.addEventListener('beforeunload', onBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload));

/* ===== 展示辅助 ===== */
function validityText(): string {
  const d = detail.value;
  if (!d) return '—';
  const from = d.issueDate ? fmtDate(d.issueDate, '—') : '—';
  return `${from} 至 ${d.validTo ? fmtDate(d.validTo) : '长期'}`;
}
function goBack(): void {
  router.back();
}
function goSafetyRisk(): void {
  void router.push(SAFETY_RISK_PATH);
}
const titleText = computed(() => {
  if (isCreate.value) return '企业资质建档';
  const d = detail.value;
  if (!d) return '资质详情';
  return `${textOf('qual_serial', d.qualSerial)} · ${d.qualCategory || '—'}`;
});
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">
          <span class="qf-badge">{{ editMode ? '✏️' : '📄' }}</span>
          {{ editMode && !isCreate ? '编辑资质 · ' : '' }}{{ titleText }}
          <span v-if="editMode && !isCreate" class="qf-editing">编辑中…</span>
        </h2>
        <p class="ct-sub">
          <template v-if="!isCreate && detail">
            <a-tag :color="tagOf('qual_status', detail.qualStatus).color">
              {{ tagOf('qual_status', detail.qualStatus).label }}
            </a-tag>
            <a-tag
              v-if="detail.daysToExpire !== null && detail.daysToExpire !== undefined"
              :color="daysColor(detail.daysToExpire)"
              style="margin-left: 8px"
            >
              剩余 {{ detail.daysToExpire }} 天
            </a-tag>
            <span v-else class="ct-panel-meta" style="margin-left: 8px">长期有效</span>
          </template>
          <template v-else-if="isCreate">录入新资质主档；建档后归入本企业主体。</template>
        </p>
      </div>
      <div class="ct-actions">
        <a class="mr-3 text-sm text-blue-600" @click="goBack">‹ 返回企业资质</a>

        <!-- 只读态操作 -->
        <template v-if="!editMode">
          <!-- 2026-10-01 补：bidding:qualification:update 未在 system_menu 注册（后端契约 B3），
             admin 不持有 → 按钮永不渲染。兜底判 create（列表页行「编辑」同口径），B3 落地后回归 update。 -->
          <a-button
            v-if="can('bidding:qualification:update') || can('bidding:qualification:create')"
            type="primary"
            :disabled="!detail"
            @click="enterEdit"
          >
            ✏️ 编辑资质
          </a-button>
          <a-button v-if="can('bidding:qualification:renew')" :disabled="!detail" @click="openRenew">🔄 发起续期</a-button>
          <a-button v-if="can('bidding:qualification:element')" :disabled="!detail" @click="staffAllOpen = true">
            🔗 关联人员管理
          </a-button>
          <a-button v-if="can('bidding:qualification:permit:query')" @click="goSafetyRisk">⚠️ 查看本企业安许风险</a-button>
          <a-button v-if="can('bidding:qualification:query')" @click="toggleHistory">📜 变更历史</a-button>
        </template>

        <!-- 编辑态操作 -->
        <template v-else>
          <a-button type="primary" :loading="saving" @click="save(false)">💾 保存</a-button>
          <a-button v-if="!isCreate" :loading="savingAndSubmit" @click="save(true)">💾 保存并提交审批</a-button>
          <a-button @click="cancelEdit">🚫 取消</a-button>
        </template>
      </div>
    </div>

    <a-alert v-if="error" type="error" show-icon class="mb-3" :message="error" />

    <a-spin :spinning="loading">
      <!-- ========== 建档场景 / 详情场景 ========== -->
      <div v-if="isCreate || detail" class="qf-cols">
        <!-- ① 基本信息 -->
        <div class="ct-panel">
          <div class="ct-panel-h">
            <span class="ct-panel-t">基本信息</span>
            <span class="ct-panel-meta">{{ editMode ? '可编辑' : '只读' }}</span>
          </div>
          <div class="ct-panel-b">
            <!-- 只读 -->
            <template v-if="!editMode && detail">
              <a-descriptions :column="1" size="small" bordered>
                <a-descriptions-item label="资质编号">{{ detail.certNo || '—' }}</a-descriptions-item>
                <a-descriptions-item label="资质序列">{{ textOf('qual_serial', detail.qualSerial) }}</a-descriptions-item>
                <a-descriptions-item label="资质等级">{{ detail.qualGrade || '—' }}</a-descriptions-item>
                <a-descriptions-item label="资质类别">{{ detail.qualCategory || '—' }}</a-descriptions-item>
                <a-descriptions-item label="发证机关">{{ detail.issuingAuthority || '—' }}</a-descriptions-item>
                <a-descriptions-item label="有效期">{{ validityText() }}</a-descriptions-item>
                <a-descriptions-item label="批准范围">{{ detail.approvalScope || '—' }}</a-descriptions-item>
                <a-descriptions-item label="备注">{{ detail.remark || '—' }}</a-descriptions-item>
                <a-descriptions-item label="数据来源">{{ textOf('source_channel', detail.sourceChannel) }}</a-descriptions-item>
              </a-descriptions>
              <div class="ct-tip">
                资质状态由后端按有效期推导（VALID/EXPIRING_SOON/EXPIRED）；延续中、受限、吊销等业务态走状态机，
                不在本页直接修改。
              </div>
            </template>

            <!-- 编辑 -->
            <template v-else>
              <a-form layout="horizontal">
                <div class="ct-sheet">
                  <div class="ct-sheet-group">资质标识<span class="ct-sg-note">序列 / 类别 / 等级 / 证书编号</span></div>
                  <div class="ct-sheet-row">
                    <div class="ct-sheet-cell">
                      <a-form-item
                        label="资质序列"
                        required
                        :validate-status="formErrors.qualSerial ? 'error' : ''"
                        data-qf="qualSerial"
                      >
                        <a-select
                          v-model:value="editForm.qualSerial"
                          :options="QualificationApi.QUAL_SERIAL_OPTIONS"
                          placeholder="施工 / 勘察 / 设计 / 监理 / 检测"
                          style="width: 100%"
                        />
                        <div v-if="formErrors.qualSerial" class="qf-err">{{ formErrors.qualSerial }}</div>
                      </a-form-item>
                    </div>
                    <div class="ct-sheet-cell">
                      <a-form-item
                        label="资质类别"
                        required
                        :validate-status="formErrors.qualCategory ? 'error' : ''"
                        data-qf="qualCategory"
                      >
                        <a-input v-model:value="editForm.qualCategory" placeholder="如：市政公用工程施工总承包" />
                        <div v-if="formErrors.qualCategory" class="qf-err">{{ formErrors.qualCategory }}</div>
                      </a-form-item>
                    </div>
                    <div class="ct-sheet-cell">
                      <a-form-item
                        label="资质等级"
                        required
                        :validate-status="formErrors.qualGrade ? 'error' : ''"
                        data-qf="qualGrade"
                      >
                        <a-input v-model:value="editForm.qualGrade" placeholder="如：特级 / 一级 / 二级" />
                        <div v-if="formErrors.qualGrade" class="qf-err">{{ formErrors.qualGrade }}</div>
                      </a-form-item>
                    </div>
                  </div>
                  <div class="ct-sheet-row">
                    <div class="ct-sheet-cell ct-sheet-wide">
                      <a-form-item
                        label="资质证书编号"
                        required
                        :validate-status="formErrors.certNo ? 'error' : ''"
                        data-qf="certNo"
                      >
                        <a-input v-model:value="editForm.certNo" placeholder="租户内同序列/类别/等级唯一" />
                        <div v-if="formErrors.certNo" class="qf-err">{{ formErrors.certNo }}</div>
                      </a-form-item>
                    </div>
                  </div>
                </div>

                <div class="ct-sheet" style="margin-top: 14px">
                  <div class="ct-sheet-group">发证信息<span class="ct-sg-note">机关 / 日期 / 有效期</span></div>
                  <div class="ct-sheet-row">
                    <div class="ct-sheet-cell">
                      <a-form-item label="发证机关">
                        <a-input v-model:value="editForm.issuingAuthority" />
                      </a-form-item>
                    </div>
                    <div class="ct-sheet-cell">
                      <a-form-item label="发证日期">
                        <a-date-picker v-model:value="editForm.issueDate" value-format="YYYY-MM-DD" style="width: 100%" />
                      </a-form-item>
                    </div>
                    <div class="ct-sheet-cell">
                      <a-form-item label="有效期至">
                        <a-date-picker v-model:value="editForm.validTo" value-format="YYYY-MM-DD" style="width: 100%" placeholder="留空 = 长期" />
                      </a-form-item>
                    </div>
                  </div>
                  <div class="ct-sheet-row">
                    <div class="ct-sheet-cell ct-sheet-wide">
                      <a-form-item label="关联证照">
                        <a-select
                          v-model:value="editForm.bindedCertId"
                          :options="certLinkOptions"
                          :loading="certLinkLoading"
                          show-search
                          option-filter-prop="label"
                          :filter-option="false"
                          placeholder="按证照名称/编号检索选择（影像/验真复用该证照）"
                          style="width: 100%"
                          @search="loadCertLinkOptions"
                        />
                      </a-form-item>
                    </div>
                  </div>
                </div>

                <div class="ct-sheet" style="margin-top: 14px">
                  <div class="ct-sheet-group">批准范围与备注</div>
                  <div class="ct-sheet-row">
                    <div class="ct-sheet-cell ct-sheet-wide">
                      <a-form-item label="批准范围">
                        <a-textarea v-model:value="editForm.approvalScope" :rows="2" placeholder="批准承包/承揽范围" />
                      </a-form-item>
                    </div>
                  </div>
                  <div class="ct-sheet-row">
                    <div class="ct-sheet-cell ct-sheet-wide">
                      <a-form-item label="备注">
                        <a-textarea v-model:value="editForm.remark" :rows="2" />
                      </a-form-item>
                    </div>
                  </div>
                </div>
              </a-form>
              <div class="ct-tip">
                保存需回传乐观锁 version；提示"数据已被他人修改"时页面会自动刷新，请确认后重新保存。
                状态字段不在表单内 —— 保存后由后端按新有效期重算日期推导态（已过期 / 正常），
                延续中、受限、吊销等业务态不受本表单影响。
                关联证照当前只支持"改绑"，不支持置空解绑。
              </div>
            </template>
          </div>
        </div>

        <!-- ② 文件附件 -->
        <div class="ct-panel">
          <div class="ct-panel-h">
            <span class="ct-panel-t">文件附件</span>
            <span class="ct-panel-meta">
              来自绑定证照
              <template v-if="detail?.bindedCertId"> #{{ detail.bindedCertId }}</template>
            </span>
          </div>
          <div class="ct-panel-b">
            <template v-if="detail?.bindedCertId">
              <div class="qf-bind">
                <div class="qf-bind-name">{{ detail.bindedCertName || '（证照已删除或未命名）' }}</div>
                <div class="ct-panel-meta">
                  {{ detail.bindedCertNo || '—' }}
                  <a-tag v-if="detail.bindedCertStatus" :color="tagOf('cert_status', detail.bindedCertStatus).color" style="margin-left: 6px">
                    {{ tagOf('cert_status', detail.bindedCertStatus).label }}
                  </a-tag>
                </div>
              </div>
              <!-- 只读态必须显式关掉写入口，否则与"详情页只读"矛盾 -->
              <CertFiles :cert-id="detail.bindedCertId" :can-write="false" />
            </template>
            <div v-else class="ct-empty">
              该资质未绑定证照 —— 影像件与验真复用证照台账，请在编辑态选择「关联证照」。
            </div>
          </div>
        </div>

        <!-- ③ 关联人员 -->
        <div class="ct-panel">
          <div class="ct-panel-h">
            <span class="ct-panel-t">关联人员</span>
            <span class="ct-panel-meta">共 {{ staffTotal }} 人</span>
          </div>
          <div class="ct-panel-b">
            <a-spin :spinning="staffLoading">
              <template v-if="staffRows.length > 0">
                <ul class="qf-staff">
                  <li v-for="s in staffRows.slice(0, 3)" :key="s.id">
                    <div class="qf-staff-name">
                      👷 {{ s.personName || '—' }}
                      <span class="ct-panel-meta">{{ textOf('qual_staff_role', s.staffRole) }}</span>
                    </div>
                    <div class="ct-panel-meta">
                      注册有效期至：{{ s.regValidTo ? fmtDate(s.regValidTo) : '—' }}
                      <template v-if="s.major"> · 专业：{{ s.major }}</template>
                    </div>
                  </li>
                </ul>
                <a class="text-sm text-blue-600" @click="staffAllOpen = true">
                  查看全部关联人员（{{ staffTotal }}）›
                </a>
              </template>
              <div v-else class="ct-empty">暂无关联人员 —— 增删改走列表页行内「要素录入」。</div>
            </a-spin>
          </div>
        </div>
      </div>

      <!-- ========== 安许风险联动 ========== -->
      <div v-if="!isCreate && detail" class="ct-panel" style="margin-top: 12px">
        <div class="ct-panel-h">
          <span class="ct-panel-t">安许风险联动</span>
          <span class="ct-panel-meta">按本企业主体统计未处理预警（该口径无资质维度，后端预警分页只支持 entId）</span>
        </div>
        <div class="ct-panel-b">
          <a-spin :spinning="alertLoading">
            <template v-if="alertTotal > 0">
              <div class="ct-warn">
                当前有 {{ alertTotal }} 条未处理预警（下列为最近 {{ alerts.length }} 条）。
                安许风险与企业主体绑定，不区分具体资质。
              </div>
              <ul class="ct-tl" style="margin-top: 8px">
                <li v-for="a in alerts" :key="a.id">
                  <div class="tl-t">
                    <a-tag :color="tagOf('risk_level', a.alertLevel).color">
                      {{ tagOf('risk_level', a.alertLevel).label }}
                    </a-tag>
                    <span>{{ a.refName || a.refId || '—' }}</span>
                  </div>
                  <div class="tl-m">
                    <span class="tl-k">来源：</span>{{ a.refType || '—' }}
                    <span class="tl-k"> · 触发：</span>{{ a.triggeredAt ? fmtDateTime(a.triggeredAt) : '—' }}
                    <div>{{ a.alertMessage || '—' }}</div>
                  </div>
                </li>
              </ul>
            </template>
            <template v-else>
              <div class="qf-ok">🟢 当前无风险<div class="ct-panel-meta">本企业主体下没有未处理的资质/安许到期预警。</div></div>
            </template>
            <a v-if="can('bidding:qualification:permit:query')" class="text-sm text-blue-600" style="margin-top: 8px; display: inline-block" @click="goSafetyRisk">
              前往「安许风险联动」查看详情 ›
            </a>
          </a-spin>
        </div>
      </div>

      <!-- ========== 变更历史 ========== -->
      <div v-if="!isCreate && detail" class="ct-panel" style="margin-top: 12px">
        <div class="ct-panel-h">
          <span class="ct-panel-t">变更历史</span>
          <span class="ct-panel-meta">口径：延续申报单（apply）—— 后端无资质主档通用变更史表</span>
          <a style="margin-left: auto" @click="toggleHistory">{{ historyOpen ? '收起' : '展开' }}</a>
        </div>
        <div v-if="historyOpen" class="ct-panel-b">
          <a-spin :spinning="applyLoading">
            <a-table
              :columns="[
                { title: '提交日期', dataIndex: 'submitDate', key: 'submitDate', width: 120 },
                { title: '申报类型', dataIndex: 'applyType', key: 'applyType', width: 110 },
                { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
                { title: '拟办结日期', dataIndex: 'dueDate', key: 'dueDate', width: 120 },
                { title: '办结文号', dataIndex: 'resultNo', key: 'resultNo', width: 150 },
                { title: '新证书编号', dataIndex: 'newCertNo', key: 'newCertNo', width: 170 },
                { title: '新有效期至', dataIndex: 'newValidTo', key: 'newValidTo', width: 120 },
                { title: '备注', dataIndex: 'remark', key: 'remark' },
              ]"
              :data-source="applies"
              :pagination="false"
              row-key="id"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'submitDate'">{{ record.submitDate ? fmtDate(record.submitDate) : '—' }}</template>
                <template v-else-if="column.key === 'applyType'">{{ textOf('qual_apply_type', record.applyType) }}</template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="tagOf('qual_apply_status', record.status).color">
                    {{ tagOf('qual_apply_status', record.status).label }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'dueDate'">{{ record.dueDate ? fmtDate(record.dueDate) : '—' }}</template>
                <template v-else-if="column.key === 'newValidTo'">{{ record.newValidTo ? fmtDate(record.newValidTo) : '—' }}</template>
                <template v-else>{{ record[column.key] ?? '—' }}</template>
              </template>
              <template #emptyText>
                <div class="ct-empty">暂无延续申报记录（该资质自建档以来未走申报流）。</div>
              </template>
            </a-table>
            <div v-if="applyTotal > applies.length" class="ct-tip">
              仅显示最近 {{ applies.length }} 条（共 {{ applyTotal }} 条），完整清单见列表页「延续申报台账」。
            </div>
          </a-spin>
        </div>
      </div>

      <div v-if="!isCreate && !detail && !loading" class="ct-empty">未取到资质详情</div>
    </a-spin>

    <!-- ========== 关联人员全量（只读看板）========== -->
    <a-modal v-model:open="staffAllOpen" title="关联人员" :width="900" :footer="null">
      <div class="ct-tip" style="margin-bottom: 8px">
        人员要素的增删改走列表页行内「要素录入」（权限 bidding:qualification:element）；本页为只读视图。
      </div>
      <a-table
        :columns="[
          { title: '姓名', dataIndex: 'personName', key: 'personName', width: 110 },
          { title: '岗位角色', dataIndex: 'staffRole', key: 'staffRole', width: 180 },
          { title: '专业', dataIndex: 'major', key: 'major', width: 140 },
          { title: '注册号', dataIndex: 'regNo', key: 'regNo', width: 160 },
          { title: '注册有效期至', dataIndex: 'regValidTo', key: 'regValidTo', width: 130 },
          { title: '社保', dataIndex: 'socialSecurityStatus', key: 'socialSecurityStatus', width: 110 },
        ]"
        :data-source="staffRows"
        :loading="staffLoading"
        :pagination="{ pageSize: 10, showSizeChanger: false, showTotal: (t: number) => `共 ${t} 人` }"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'staffRole'">{{ textOf('qual_staff_role', record.staffRole) }}</template>
          <template v-else-if="column.key === 'regValidTo'">{{ record.regValidTo ? fmtDate(record.regValidTo) : '—' }}</template>
          <template v-else>{{ record[column.key] || '—' }}</template>
        </template>
      </a-table>
    </a-modal>

    <!-- ========== 发起续期 ========== -->
    <a-modal
      v-model:open="renewOpen"
      :title="`发起续期申报：${detail?.certNo || ''}`"
      :width="560"
      :confirm-loading="renewSaving"
      ok-text="提交申报"
      cancel-text="取消"
      destroy-on-close
      @ok="submitRenew"
    >
      <a-form layout="horizontal">
        <a-form-item label="拟办结日期">
          <a-date-picker v-model:value="renewForm.dueDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="renewForm.remark" :rows="2" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">
        提交后主档状态转为「延续中」，进入审批流程；审批通过回写新有效期与证书编号。
        当前版本后端仅支持延续（RENEW）申报。
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
/* 三栏并排：基本信息 / 文件附件 / 关联人员（窄屏自动落为单栏） */
.qf-cols {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}
@media (max-width: 1280px) {
  .qf-cols {
    grid-template-columns: 1fr;
  }
}
.qf-badge {
  margin-right: 4px;
}
.qf-err {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: #cf1322;
}
.qf-editing {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 3px;
  padding: 1px 6px;
}
.qf-bind {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 0 10px;
  border-bottom: 1px dashed #e2e6ea;
  margin-bottom: 10px;
}
.qf-bind-name {
  font-size: 13.5px;
  font-weight: 700;
  color: #2e3947;
}
.qf-staff {
  list-style: none;
  margin: 0 0 8px;
  padding: 0;
}
.qf-staff li {
  padding: 8px 0;
  border-bottom: 1px dashed #e2e6ea;
}
.qf-staff li:last-child {
  border-bottom: none;
}
.qf-staff-name {
  font-size: 13.5px;
  font-weight: 700;
  color: #2e3947;
  display: flex;
  align-items: center;
  gap: 8px;
}
.qf-ok {
  font-size: 14px;
  font-weight: 700;
  color: #15803d;
}
</style>
