<script setup lang="ts">
/* 员工资质 · 员工证书区块（契约 §8.1 分页 / §8.2 到期清单 / §8.3 细分统计 / §8.4 法定预警下限）
 * 人证合一：按员工、证书类型、状态、到期窗口检索；安许 A/B/C 三类人员配备自查依赖 certSubType 统计。
 * 权限：bidding:personnel:cert:{query,create,update,delete}、bidding:personnel:alert-run
 * 阈值：到期窗口默认取配置 personnel.cert.expiring.window.days；配备要求取 safety.abc.required（不硬编码天数/人数） */
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { certErrorMsg } from '#/api/bidding/certificate';
import {
  changePersonnelCertStatus,
  countPersonnelCertBySubType,
  createPersonnelCert,
  deletePersonnelCert,
  getPersonnelCertExpiring,
  getPersonnelCertPage,
  getPersonnelLegalFloorDays,
  type PersonnelCertApi,
  runPersonnelAlertScan,
  updatePersonnelCert,
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
          本页覆盖员工证书（契约 §8）；员工档案与履历（培训/社保/劳动合同）由另一份契约接入。
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
      :description="`${metaError}｜/bidding/meta/* 需要权限 bidding:certificate:query；只授了员工资质权限时会出现该提示，可让管理员补该权限或由后端放开。`"
    />

    <a-tabs v-model:active-key="tab">
      <a-tab-pane key="list" tab="员工证书">
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

      <a-tab-pane key="board" tab="到期看板">
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

      <a-tab-pane key="abc" tab="安许配备自查">
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
