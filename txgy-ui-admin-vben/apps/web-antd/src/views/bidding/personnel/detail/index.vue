<script setup lang="ts">
/* 员工（人员）详情页 —— 独立路由 /bidding/personnel/detail/:id（hideInMenu）
 * 2026-09-28 整改：原先「查看详情」在表格下方/抽屉里占位展示，用户找不到文件入口。
 *   改为独立详情页 + 分区（基本信息 / 文件版本 / 验真），文件入口显眼：
 *     ① 列表行内直接有「查看文件 / 上传」；② 本页「文件版本」顶部主按钮「上传文件」；
 *     ③ 每个文件行都有「预览（PdfPreview 内联）/ 下载」。
 * 数据来源：personnel-profile/get+overview、personnel-certificate/list-by-person、
 *   personnel-record/{training,social-security,labor-contract}/list、safety-permit/personnel/risk（只读）。 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  buildBiddingFileDownloadUrl,
  uploadBiddingFile,
  withBiddingFileToken,
} from '#/api/bidding/file';
import {
  getPersonnelCertByPerson,
  getPersonnelLaborContractList,
  getPersonnelOverview,
  getPersonnelProfile,
  getPersonnelSocialSecurityList,
  getPersonnelTrainingList,
  type PersonnelCertApi,
  type PersonnelProfileApi,
  updatePersonnelCert,
  verifyPersonnelCert,
} from '#/api/bidding/personnel';
import { getPersonnelRisk, type SafetyPermitApi } from '#/api/bidding/safetyPermit';

import { fmtDateTime } from '../../certificate/format';
import { useCertMeta } from '../../certificate/useCertMeta';
import CopyText from '../../components/CopyText.vue';
import { type FileVersionRow } from '../../components/fileVersion';
import FileVersionTable from '../../components/FileVersionTable.vue';
import PdfPreview from '../../vo/PdfPreview.vue';

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictLabel, dictTag } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const personId = computed(() => String(route.params.id ?? ''));

const loading = ref(false);
const profile = ref<null | PersonnelProfileApi.Profile>(null);
const overview = ref<null | PersonnelProfileApi.Overview>(null);
const certs = ref<PersonnelCertApi.Cert[]>([]);
const trainings = ref<PersonnelProfileApi.Training[]>([]);
const socials = ref<PersonnelProfileApi.SocialSecurity[]>([]);
const contracts = ref<PersonnelProfileApi.LaborContract[]>([]);
const risk = ref<null | SafetyPermitApi.PersonRisk>(null);
const tab = ref('basic');

/** 姓名兜底：id 缺失时不要拼出「员工 #undefined」 */
const personLabel = computed(() => {
  const p = profile.value;
  if (!p) return '—';
  return p.personName || (p.id ? `员工 #${p.id}` : '未关联员工');
});

async function load(): Promise<void> {
  if (!personId.value) return;
  loading.value = true;
  try {
    const [p, ov, cs, tr, so, co, rk] = await Promise.all([
      getPersonnelProfile(personId.value).catch(() => null),
      getPersonnelOverview(personId.value).catch(() => null),
      getPersonnelCertByPerson(personId.value).catch(() => [] as PersonnelCertApi.Cert[]),
      getPersonnelTrainingList(personId.value).catch(() => [] as PersonnelProfileApi.Training[]),
      getPersonnelSocialSecurityList(personId.value).catch(() => [] as PersonnelProfileApi.SocialSecurity[]),
      getPersonnelLaborContractList(personId.value).catch(() => [] as PersonnelProfileApi.LaborContract[]),
      getPersonnelRisk(personId.value).catch(() => null),
    ]);
    profile.value = p;
    overview.value = ov;
    certs.value = cs;
    trainings.value = tr;
    socials.value = so;
    contracts.value = co;
    risk.value = rk;
  } finally {
    loading.value = false;
  }
}

/* ===================== 文件版本 ===================== */

/** 文件预览（一律 fileId → PdfPreview 内联出口） */
const preview = reactive<{ fileId?: number | string; name: string; open: boolean; url: string }>({
  fileId: undefined,
  name: '',
  open: false,
  url: '',
});
function onFilePreview(row: FileVersionRow): void {
  openPreview(row.fileId, row.name, row.url);
}
function openPreview(fileId: number | string | undefined, name: string, url?: string): void {
  if (fileId === undefined || fileId === null || fileId === '') {
    message.warning('该记录没有文件编号，无法预览');
    return;
  }
  preview.fileId = url ? undefined : fileId;
  preview.url = url ?? '';
  preview.name = name;
  preview.open = true;
}

/** 人员证书 → 保存入参（只回传可写字段，避免把出参只读字段当入参提交） */
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

/** 上传文件（先选证书，再上传；上传成功即替换该证书的当前文件） */
const uploadOpen = ref(false);
const uploadCertId = ref<number | undefined>(undefined);
const uploadSaving = ref(false);
const uploadFileName = ref('');
const certOptions = computed(() =>
  certs.value.map((c) => ({
    label: `${c.certName || dictLabel('person_cert_type', c.certType)}（${c.certNo || '无编号'}）`,
    value: c.id,
  })),
);
function openUpload(certId?: number): void {
  uploadCertId.value = certId ?? certs.value[0]?.id;
  uploadFileName.value = '';
  uploadOpen.value = true;
}
async function onPickFile(file: File): Promise<boolean> {
  const cert = certs.value.find((c) => c.id === uploadCertId.value);
  if (!cert) {
    message.warning('请先选择要挂载文件的证书');
    return false;
  }
  uploadSaving.value = true;
  try {
    const up = await uploadBiddingFile(file, 'bidding/personnel');
    if (!up?.fileId) {
      message.warning('文件上传失败（未取得文件编号），请重试');
      return false;
    }
    await updatePersonnelCert(certSaveReq(cert, up.fileId));
    uploadFileName.value = up.name || file.name;
    message.success('文件已上传并挂载到该证书');
    uploadOpen.value = false;
    await load();
  } catch {
    /* 失败原因由请求层 toast 后端 msg */
  } finally {
    uploadSaving.value = false;
  }
  return false;
}

/** 下载地址（受控内联出口 + token；不新开需登录的地址页） */
function downloadHref(fileId: number | string): string {
  return withBiddingFileToken(buildBiddingFileDownloadUrl(fileId));
}

/** 履历附件（培训证明 / 社保缴费证明 / 劳动合同扫描件） */
const recordFileRows = computed<FileVersionRow[]>(() => {
  const out: FileVersionRow[] = [];
  for (const t of trainings.value) {
    if (t.fileId) {
      out.push({
        fileId: t.fileId,
        name: t.trainingName ? `培训证明 · ${t.trainingName}` : '培训证明',
        categoryLabel: '培训',
        note: [t.organizer, t.startDate].filter(Boolean).join(' · '),
      });
    }
  }
  for (const s of socials.value) {
    if (s.evidenceFileId) {
      out.push({
        fileId: s.evidenceFileId,
        name: `社保缴费证明 · ${s.payMonth || ''}`,
        categoryLabel: '社保',
        note: s.payerName,
      });
    }
  }
  for (const c of contracts.value) {
    if (c.fileId) {
      out.push({
        fileId: c.fileId,
        name: c.contractNo ? `劳动合同 · ${c.contractNo}` : '劳动合同',
        categoryLabel: '劳动合同',
        note: [c.positionName, c.contractStatus].filter(Boolean).join(' · '),
      });
    }
  }
  return out;
});

/* ===================== 验真 ===================== */
const verifying = ref<number | undefined>(undefined);
async function doVerify(cert: PersonnelCertApi.Cert): Promise<void> {
  if (!cert.id) return;
  verifying.value = cert.id;
  try {
    await verifyPersonnelCert({ certId: cert.id });
    message.success('已提交验真，结论以平台核验状态为准');
    await load();
  } catch {
    /* 错误由请求层提示 */
  } finally {
    verifying.value = undefined;
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
        <h2 class="ct-title">员工详情 · {{ personLabel }}</h2>
        <p class="ct-sub">
          基本信息 / 文件版本（上传 · 预览 · 下载）/ 验真 三个分区；文件预览一律走受控内联出口，不会变成下载。
        </p>
      </div>
      <div class="ct-actions">
        <a-button @click="goBack">
          <IconifyIcon icon="lucide:arrow-left" class="mr-1 inline-block align-middle" />
          返回员工证件台账
        </a-button>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <a-tabs v-model:active-key="tab">
        <!-- ① 基本信息 -->
        <a-tab-pane key="basic" tab="基本信息">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">档案信息</span>
              <span class="ct-panel-meta">身份证只写不读（回读为脱敏值）</span>
            </div>
            <div class="ct-panel-b">
              <a-descriptions :column="3" size="small" bordered>
                <a-descriptions-item label="姓名">{{ personLabel }}</a-descriptions-item>
                <a-descriptions-item label="工号">
                  <CopyText :text="profile?.personNo" label="工号" />
                </a-descriptions-item>
                <a-descriptions-item label="身份证（脱敏）">{{ profile?.idCardMasked || '—' }}</a-descriptions-item>
                <a-descriptions-item label="性别">
                  {{ profile?.gender ? dictLabel('gender', profile.gender) : '—' }}
                </a-descriptions-item>
                <a-descriptions-item label="出生日期">{{ profile?.birthDate || '—' }}</a-descriptions-item>
                <a-descriptions-item label="身份证有效期至">{{ profile?.idCardValidTo || '—' }}</a-descriptions-item>
                <a-descriptions-item label="部门">{{ profile?.deptName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="岗位">{{ profile?.positionName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="人事档案号">{{ profile?.archiveNo || '—' }}</a-descriptions-item>
                <a-descriptions-item label="用工形式">
                  <a-tag v-if="profile?.employmentType">{{ dictTag('employment_type', profile.employmentType).label }}</a-tag>
                  <span v-else>—</span>
                </a-descriptions-item>
                <a-descriptions-item label="学历">
                  {{ profile?.eduLevel ? dictLabel('edu_level', profile.eduLevel) : '—' }}
                  <span v-if="profile?.eduMajor" class="ct-panel-meta"> · {{ profile.eduMajor }}</span>
                </a-descriptions-item>
                <a-descriptions-item label="职称">{{ profile?.titleLevel || '—' }}</a-descriptions-item>
                <a-descriptions-item label="入职日期">{{ profile?.joinDate || '—' }}</a-descriptions-item>
                <a-descriptions-item label="在职状态">
                  <a-tag :color="dictTag('personnel_status', profile?.personnelStatus).color">
                    {{ dictTag('personnel_status', profile?.personnelStatus).label }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="状态变更日期">{{ profile?.statusChangeDate || '—' }}</a-descriptions-item>
                <a-descriptions-item label="手机">{{ profile?.mobile || '—' }}</a-descriptions-item>
                <a-descriptions-item label="邮箱">{{ profile?.email || '—' }}</a-descriptions-item>
                <a-descriptions-item label="人员风险">
                  <a-tag v-if="risk?.personRiskFlag" :color="dictTag('person_risk_flag', risk?.personRiskFlag).color">
                    {{ dictTag('person_risk_flag', risk?.personRiskFlag).label }}
                  </a-tag>
                  <a-tooltip v-else title="尚未跑过人员风险评估，不代表无风险">
                    <a-tag>未评估</a-tag>
                  </a-tooltip>
                </a-descriptions-item>
                <a-descriptions-item label="12 个月岗位变更">{{ risk?.jobChangeCount12m ?? '—' }}</a-descriptions-item>
                <a-descriptions-item label="备注" :span="3">{{ profile?.remark || '—' }}</a-descriptions-item>
              </a-descriptions>

              <div class="ct-panel-t mb-2 mt-4">履职概览</div>
              <div class="ct-kpis" style="margin-top: 0">
                <div class="ct-kpi k-blue">
                  <div class="k-label">可用证书</div>
                  <div class="k-num">{{ overview?.usableCertCount ?? '—' }}</div>
                  <div class="k-note">可参与投标</div>
                </div>
                <div class="ct-kpi">
                  <div class="k-label">在册证书</div>
                  <div class="k-num">{{ overview?.totalCertCount ?? certs.length }}</div>
                  <div class="k-note">含历史旧证</div>
                </div>
                <div class="ct-kpi" :class="overview?.socialSecurityConsistent === false ? 'k-red' : ''">
                  <div class="k-label">社保一致性</div>
                  <div class="k-num">{{ overview?.socialSecurityConsistent === undefined ? '—' : overview.socialSecurityConsistent ? '一致' : '不一致' }}</div>
                  <div class="k-note">最近缴费 {{ overview?.latestSocialSecurityMonth || '—' }}</div>
                </div>
                <div class="ct-kpi" :class="overview?.laborContractValid === false ? 'k-red' : ''">
                  <div class="k-label">劳动合同</div>
                  <div class="k-num">{{ overview?.laborContractValid === undefined ? '—' : overview.laborContractValid ? '有效' : '异常' }}</div>
                  <div class="k-note">与主体一致：{{ overview?.laborContractConsistent === undefined ? '—' : overview.laborContractConsistent ? '是' : '否' }}</div>
                </div>
                <div class="ct-kpi">
                  <div class="k-label">累计学时</div>
                  <div class="k-num">{{ overview?.totalEduHours ?? '—' }}</div>
                  <div class="k-note">培训记录合计</div>
                </div>
              </div>
              <a-alert
                v-if="overview?.risks?.length"
                class="mt-3"
                type="warning"
                show-icon
                message="风险项"
                :description="overview.risks.join('；')"
              />
            </div>
          </div>
        </a-tab-pane>

        <!-- ② 文件版本 -->
        <a-tab-pane key="files" tab="文件版本">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">证书文件</span>
              <span class="ct-panel-meta">每本证书一份现行文件；重新上传即替换现行版本</span>
              <a-button
                v-if="can('bidding:personnel:cert:update')"
                type="primary"
                style="margin-left: auto"
                :disabled="certs.length === 0"
                @click="openUpload()"
              >
                上传文件
              </a-button>
            </div>
            <div class="ct-panel-b">
              <a-table
                class="ct-table"
                :columns="[
                  { title: '证书', key: 'cert', width: 240 },
                  { title: '证书编号', key: 'certNo', width: 190 },
                  { title: '有效期', key: 'validity', width: 210 },
                  { title: '状态', key: 'certStatus', width: 120 },
                  { title: '文件', key: 'file', width: 120 },
                  { title: '操作', key: 'action', width: 170 },
                ]"
                :data-source="certs"
                :pagination="false"
                row-key="id"
                size="small"
                :scroll="{ x: 1000 }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'cert'">
                    <div style="font-weight: 700">{{ record.certName || dictLabel('person_cert_type', record.certType) }}</div>
                    <div class="ct-panel-meta">{{ record.certSubType || record.certLevel || '—' }}</div>
                  </template>
                  <template v-else-if="column.key === 'certNo'">
                    <CopyText :text="record.certNo" label="证书编号" />
                  </template>
                  <template v-else-if="column.key === 'validity'">
                    {{ record.validFrom || '—' }} ~ {{ record.validTo || '长期' }}
                    <a-tag v-if="record.remainingDays !== undefined && record.remainingDays !== null" :color="record.remainingDays < 0 ? 'red' : record.remainingDays <= 30 ? 'orange' : 'green'">
                      {{ record.remainingDays < 0 ? `已过期 ${Math.abs(record.remainingDays)} 天` : `剩余 ${record.remainingDays} 天` }}
                    </a-tag>
                  </template>
                  <template v-else-if="column.key === 'certStatus'">
                    <a-tag :color="dictTag('person_cert_status', record.certStatus).color">
                      {{ dictTag('person_cert_status', record.certStatus).label }}
                    </a-tag>
                  </template>
                  <template v-else-if="column.key === 'file'">
                    <a-tag v-if="record.fileId" color="green">已上传</a-tag>
                    <a-tag v-else color="orange">未上传</a-tag>
                  </template>
                  <template v-else-if="column.key === 'action'">
                    <a-space size="small">
                      <a v-if="record.fileId" @click="openPreview(record.fileId, record.certName || '证书文件')">预览</a>
                      <a v-if="record.fileId" :href="downloadHref(record.fileId)" download>下载</a>
                      <a v-if="can('bidding:personnel:cert:update')" @click="openUpload(record.id)">
                        {{ record.fileId ? '替换' : '上传' }}
                      </a>
                    </a-space>
                  </template>
                </template>
                <template #emptyText><div class="ct-empty">该员工暂无可挂载文件的证书</div></template>
              </a-table>

              <div class="ct-panel-t mb-2 mt-4">履历附件（培训证明 / 社保缴费证明 / 劳动合同）</div>
              <FileVersionTable
                :rows="recordFileRows"
                empty-text="暂无履历附件：在「履历」页签录入记录时上传"
                @preview="onFilePreview"
              />
            </div>
          </div>
        </a-tab-pane>

        <!-- ③ 验真 -->
        <a-tab-pane key="verify" tab="验真">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">平台核验</span>
              <span class="ct-panel-meta">验真结论以平台回传为准；未核验不代表无效</span>
            </div>
            <div class="ct-panel-b">
              <a-table
                class="ct-table"
                :columns="[
                  { title: '证书', key: 'cert', width: 240 },
                  { title: '证书编号', key: 'certNo', width: 190 },
                  { title: '核验状态', key: 'verified', width: 140 },
                  { title: '核验时间', key: 'verifiedAt', width: 170 },
                  { title: '核验来源', key: 'verifyUrl', width: 200 },
                  { title: '操作', key: 'action', width: 130 },
                ]"
                :data-source="certs"
                :pagination="false"
                row-key="id"
                size="small"
                :scroll="{ x: 1000 }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'cert'">
                    {{ record.certName || dictLabel('person_cert_type', record.certType) }}
                  </template>
                  <template v-else-if="column.key === 'certNo'">
                    <CopyText :text="record.certNo" label="证书编号" />
                  </template>
                  <template v-else-if="column.key === 'verified'">
                    <a-tag :color="record.platformVerified ? 'green' : 'default'">
                      {{ record.platformVerified ? '已核验' : '未核验' }}
                    </a-tag>
                  </template>
                  <template v-else-if="column.key === 'verifiedAt'">
                    {{ record.verifiedAt ? fmtDateTime(record.verifiedAt) : '—' }}
                  </template>
                  <template v-else-if="column.key === 'verifyUrl'">
                    <a v-if="record.verifyUrl" :href="record.verifyUrl" target="_blank" rel="noopener noreferrer">核验链接</a>
                    <span v-else class="ct-panel-meta">—</span>
                  </template>
                  <template v-else-if="column.key === 'action'">
                    <a v-if="can('bidding:personnel:cert:query')" @click="doVerify(record)">
                      {{ verifying === record.id ? '提交中…' : '发起验真' }}
                    </a>
                  </template>
                </template>
                <template #emptyText><div class="ct-empty">该员工暂无证书，先去「员工证件台账」录入</div></template>
              </a-table>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-spin>

    <!-- 上传文件：先选证书，再选文件；上传成功即替换该证书的现行文件 -->
    <a-modal
      v-model:open="uploadOpen"
      title="上传证书文件"
      :confirm-loading="uploadSaving"
      :footer="null"
      destroy-on-close
    >
      <div class="ct-lb">挂载到证书</div>
      <a-select
        v-model:value="uploadCertId"
        class="mb-3 w-full"
        :options="certOptions"
        placeholder="选择证书"
        show-search
        option-filter-prop="label"
      />
      <a-upload :before-upload="onPickFile" :show-upload-list="false" :disabled="uploadSaving">
        <a-button type="primary" :loading="uploadSaving">
          {{ uploadSaving ? '上传中…' : '选择文件并上传' }}
        </a-button>
      </a-upload>
      <div v-if="uploadFileName" class="ct-tip">已上传：{{ uploadFileName }}</div>
      <div class="ct-tip">支持 PDF / 图片等常见格式；上传后可在「文件版本」中预览与下载。</div>
    </a-modal>

    <!-- 文件预览：一律 fileId → 业务受控内联出口 -->
    <PdfPreview
      v-model:open="preview.open"
      :file-id="preview.fileId"
      :url="preview.url"
      :file-name="preview.name"
      :title="preview.name || '文件预览'"
    />
  </div>
</template>
