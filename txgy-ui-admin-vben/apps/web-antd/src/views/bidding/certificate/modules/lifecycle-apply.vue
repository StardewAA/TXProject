<script setup lang="ts">
/* 发起证件生命周期流程（契约 §6.1 apply）
 * · 四类流程语义（后端枚举，标签一律取字典 lifecycle_type）：
 *   CHANGE 变更 → changes 必填（一行一字段，后端自动归组）；RENEW 续期 → renewalType + newValidTo 必填；
 *   REVOKE 注销吊销 → revokeType 必填；ANNUAL 年检 → reviewYear + reviewDate 必填。
 * · applyNo 唯一：用于幂等与对账，重复发起会被 1060107021 拒绝
 * · 三态机：PENDING → REVIEWING → APPROVED → EFFECTIVE（生效才回写台账，本页只负责"发起"） */
import { computed, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import {
  applyCertLifecycle,
  CERT_ERR,
  certErrorCode,
  certErrorMsg,
  certFieldErrors,
  type CertificateApi,
  getCertificate,
  getCertificatePage,
} from '#/api/bidding/certificate';
import { uploadBiddingFile } from '#/api/bidding/file';
import { rememberFileRef } from '../../vo/fileRefCache';

import { useCertMeta } from '../useCertMeta';

const props = defineProps<{
  /** 指定证照时（从台账发起）不再显示证照选择 */
  cert?: CertificateApi.Certificate | null;
  open: boolean;
}>();
const emit = defineEmits<{
  (e: 'applied', id?: number): void;
  (e: 'update:open', v: boolean): void;
}>();

const { dictOptions } = useCertMeta();

/** 契约 §6.1 明示的流程类型码（语义判定用；展示名取字典） */
const T_CHANGE = 'CHANGE';
const T_RENEW = 'RENEW';
const T_REVOKE = 'REVOKE';
const T_ANNUAL = 'ANNUAL';

/** 可选变更字段（字段名 → 中文名；oldValue 自动带出当前值） */
const CERT_FIELDS = [
  { label: '证照名称', value: 'certName' },
  { label: '证书编号', value: 'certNo' },
  { label: '证照大类', value: 'certType' },
  { label: 'GB/T 36902 类型代码', value: 'certCategoryCode' },
  { label: '等级', value: 'certLevel' },
  { label: '许可/业务范围', value: 'certScope' },
  { label: '持证主体名称', value: 'holderName' },
  { label: '持证主体类别', value: 'holderType' },
  { label: '颁发机构', value: 'issuingAuthority' },
  { label: '颁发机构代码', value: 'issuingAuthorityCode' },
  { label: '颁发日期', value: 'issueDate' },
  { label: '有效期起', value: 'validFrom' },
  { label: '有效期至', value: 'validTo' },
  { label: '电子证照标识', value: 'eCertIdent' },
  { label: '备注', value: 'remark' },
];

const saving = ref(false);
const picking = ref(false);
const options = ref<Array<{ label: string; value: number }>>([]);
const picked = ref<CertificateApi.Certificate | null>(null);

/** 佐证材料：直接上传，自动收集 fileId（用户不手填无从得知的文件编号） */
interface EvidenceFile {
  fileId: number;
  name: string;
}
const evidenceFiles = ref<EvidenceFile[]>([]);
const evidenceUploading = ref(false);
async function onPickEvidence(file: File): Promise<boolean> {
  evidenceUploading.value = true;
  try {
    const uploaded = await uploadBiddingFile(file, 'bidding/certificate');
    if (!uploaded?.fileId) {
      message.warning('文件上传失败（未取得文件编号），请重试');
      return false;
    }
    const displayName = uploaded.name || file.name;
    evidenceFiles.value = [...evidenceFiles.value, { fileId: uploaded.fileId, name: displayName }];
    /* 上传回执缓存：详情出参只回 fileId（后端按 id 查元数据未开放），此处留 URL/名称供后续预览 */
    rememberFileRef(uploaded.fileId, uploaded.url, displayName);
    message.success('佐证材料已上传');
  } catch {
    // 失败原因由请求层 toast 后端 msg
  } finally {
    evidenceUploading.value = false;
  }
  return false;
}
function removeEvidence(i: number) {
  evidenceFiles.value = evidenceFiles.value.filter((_, idx) => idx !== i);
}

const form = reactive<CertificateApi.LifecycleApplyReq>({
  bizDomain: 'CERT',
  bizId: 0,
  lifecycleType: T_CHANGE,
  applyNo: '',
  changes: [{ fieldName: 'certName', newValue: '' }],
  changeSummary: undefined,
  renewalType: undefined,
  newValidFrom: undefined,
  newValidTo: undefined,
  newNextReviewDate: undefined,
  newCertNo: undefined,
  revokeType: undefined,
  revokeAuthority: undefined,
  revokeReason: undefined,
  revokeEffectiveAt: undefined,
  reviewYear: undefined,
  reviewDate: undefined,
  reviewResult: undefined,
  evidenceFileIds: undefined,
  remark: undefined,
});

const active = computed(() => (props.cert ? props.cert : picked.value));
const typeOptions = computed(() => dictOptions('lifecycle_type'));
const renewalTypes = computed(() => dictOptions('renewal_type'));
const revokeTypes = computed(() => dictOptions('revoke_type'));
const bizDomains = computed(() => dictOptions('biz_domain'));
/** 年检结论：优先取字典 review_result；字典未提供时用契约 §6.1 说明的 1/0（通过/不通过） */
const reviewResults = computed(() => {
  const fromDict = dictOptions('review_result');
  return fromDict.length > 0
    ? fromDict
    : [
        { label: '通过', value: '1' },
        { label: '不通过', value: '0' },
      ];
});

function genApplyNo(id?: number) {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  const stamp = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
  return `LC${id ?? 'X'}-${stamp}`;
}

async function searchCerts(kw: string) {
  if (!kw) {
    options.value = [];
    return;
  }
  picking.value = true;
  try {
    const page = await getCertificatePage({ certName: kw, pageNo: 1, pageSize: 20 });
    options.value = (page.list ?? []).map((c) => ({
      label: `${c.certName}（${c.certNo}）`,
      value: c.id,
    }));
  } catch {
    options.value = [];
  } finally {
    picking.value = false;
  }
}

function onPickCert(id: number) {
  const c = options.value.find((o) => o.value === id);
  // 列表返回体不含完整字段，详情在提交前由后端校验；这里仅用于 applyNo 生成与旧值带出
  picked.value = { id, certName: c?.label ?? '', certNo: '' } as CertificateApi.Certificate;
  form.bizId = id;
  if (!form.applyNo.includes(String(id))) form.applyNo = genApplyNo(id);
}

function addChange() {
  form.changes = [...(form.changes ?? []), { fieldName: undefined as unknown as string, newValue: '' }];
}
function removeChange(i: number) {
  form.changes = (form.changes ?? []).filter((_, idx) => idx !== i);
}
/** 选中字段后自动带出当前值作为 oldValue（减少手填，仍可改）
 *  §6.1④：列表行字段不全，故以 §2.2 详情为准，拉一次最新详情填旧值。 */
async function fillOldValue(row: CertificateApi.FieldChange) {
  const src = (await ensureDetail()) as unknown as null | Record<string, unknown>;
  const v = src && row.fieldName ? src[row.fieldName] : undefined;
  row.oldValue = v === undefined || v === null ? '' : String(v);
}

/** 变更流程的旧值来源：按需拉取 §2.2 详情（缓存，避免每次点选都请求） */
const detailCache = ref<CertificateApi.Certificate | null>(null);
let detailFetching: null | Promise<CertificateApi.Certificate | null> = null;
async function ensureDetail() {
  if (detailCache.value) return detailCache.value;
  const id = props.cert?.id ?? form.bizId;
  if (!id) return active.value;
  detailFetching ??= getCertificate(id)
    .then((d) => {
      detailCache.value = d;
      return d;
    })
    .catch(() => active.value)
    .finally(() => {
      detailFetching = null;
    });
  return detailFetching;
}

watch(
  () => props.open,
  (v) => {
    if (!v) return;
    picked.value = null;
    options.value = [];
    detailCache.value = null; // 每次打开重取详情，避免旧值来自过期缓存
    form.bizId = props.cert?.id ?? 0;
    form.bizDomain = 'CERT';
    form.lifecycleType = T_CHANGE;
    form.applyNo = genApplyNo(props.cert?.id);
    form.changes = [{ fieldName: 'certName', newValue: '' }];
    form.changeSummary = undefined;
    form.renewalType = undefined;
    form.newValidFrom = undefined;
    form.newValidTo = undefined;
    form.newNextReviewDate = undefined;
    form.newCertNo = undefined;
    form.revokeType = undefined;
    form.revokeAuthority = undefined;
    form.revokeReason = undefined;
    form.revokeEffectiveAt = undefined;
    form.reviewYear = new Date().getFullYear();
    form.reviewDate = undefined;
    form.reviewResult = undefined;
    form.evidenceFileIds = undefined;
    evidenceFiles.value = [];
    form.remark = undefined;
  },
);

/** 按类型做条件必填预校验（对齐 §6.1 必填口径，最终仍由后端裁判） */
function precheck(): string {
  if (!form.bizId) return '请选择作用对象';
  if (!form.applyNo?.trim()) return '请填写申请单号（唯一，用于幂等与对账）';
  if (form.lifecycleType === T_CHANGE) {
    const valid = (form.changes ?? []).filter((c) => c.fieldName);
    if (valid.length === 0) return '变更流程必须至少提交一项字段变更';
  }
  if (form.lifecycleType === T_RENEW && !form.newValidTo) return '续期必须填写续期后的有效期至';
  if (form.lifecycleType === T_RENEW && !form.renewalType) return '请选择续期方式';
  if (form.lifecycleType === T_REVOKE && !form.revokeType) return '请选择注销吊销方式';
  if (form.lifecycleType === T_ANNUAL && (!form.reviewYear || !form.reviewDate)) return '年检必须填写年度与年检日期';
  return '';
}

async function submit() {
  const bad = precheck();
  if (bad) {
    message.warning(bad);
    return;
  }
  saving.value = true;
  const payload: CertificateApi.LifecycleApplyReq = {
    bizDomain: form.bizDomain,
    bizId: form.bizId,
    lifecycleType: form.lifecycleType,
    applyNo: form.applyNo,
    remark: form.remark,
  };
  if (form.lifecycleType === T_CHANGE) {
    payload.changes = (form.changes ?? []).filter((c) => c.fieldName);
    payload.changeSummary = form.changeSummary;
  } else if (form.lifecycleType === T_RENEW) {
    payload.renewalType = form.renewalType;
    payload.newValidFrom = form.newValidFrom;
    payload.newValidTo = form.newValidTo;
    payload.newNextReviewDate = form.newNextReviewDate;
    payload.newCertNo = form.newCertNo;
  } else if (form.lifecycleType === T_REVOKE) {
    payload.revokeType = form.revokeType;
    payload.revokeAuthority = form.revokeAuthority;
    payload.revokeReason = form.revokeReason;
    payload.revokeEffectiveAt = form.revokeEffectiveAt;
  } else if (form.lifecycleType === T_ANNUAL) {
    payload.reviewYear = form.reviewYear;
    payload.reviewDate = form.reviewDate;
    payload.reviewResult = form.reviewResult === undefined ? undefined : Number(form.reviewResult);
  }
  payload.evidenceFileIds = evidenceFiles.value.length > 0 ? evidenceFiles.value.map((f) => f.fileId) : undefined;
  try {
    const id = await applyCertLifecycle(payload);
    message.success(`流程已发起（#${id}），待审核`);
    emit('applied', id);
    emit('update:open', false);
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.APPLY_NO_DUPLICATE) {
      message.warning('该申请单号已提交过，请更换或到流程台账查看');
      form.applyNo = genApplyNo(form.bizId);
    } else if (code === 400) {
      // 契约 §0.4：400 的 data.fieldErrors 逐条展示（本表单字段多，用合并提示）
      const fields = certFieldErrors(error);
      message.warning(
        fields.length > 0
          ? `参数校验未通过：${fields.map((f) => `${f.field} ${f.message}`).join('；')}`
          : certErrorMsg(error),
      );
    } else if (code === CERT_ERR.CHANGE_EMPTY || code === CERT_ERR.LIFECYCLE_FIELD_MISSING) {
      message.warning(certErrorMsg(error));
    } else if (code === CERT_ERR.LIFECYCLE_BIZ_NOT_FOUND) {
      message.error('关联的业务对象不存在，请刷新后重试');
      emit('applied');
      emit('update:open', false);
    }
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    title="发起证件生命周期流程"
    :width="880"
    :confirm-loading="saving"
    ok-text="提交申请"
    cancel-text="取消"
    destroy-on-close
    @ok="submit"
    @cancel="emit('update:open', false)"
  >
    <a-form layout="horizontal">
    <a-row :gutter="16">
      <a-col v-if="!cert" :span="24" class="mb-3">
        <div class="ct-lb"><span class="ct-ast">*</span>作用对象（证照）</div>
        <a-select
          class="w-full"
          show-search
          :filter-option="false"
          :loading="picking"
          :options="options"
          placeholder="输入证照名称搜索（模糊）"
          @search="searchCerts"
          @change="onPickCert"
        />
      </a-col>
      <a-col v-else :span="24" class="mb-3">
        <div class="ct-lb">作用对象</div>
        <div>
          <a-tag>{{ cert.certName }}</a-tag>
          <span class="ct-id">{{ cert.certNo }}</span>
          <span class="ct-panel-meta">（证照 #{{ cert.id }}）</span>
        </div>
      </a-col>

      <a-col :span="24">
        <div class="ct-sheet">
          <div class="ct-sheet-group">流程申请<span class="ct-sg-note">对象域 / 流程类型 / 申请单号</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="对象域">
                <a-select
                  v-model:value="form.bizDomain"
                  :options="bizDomains.length ? bizDomains : [{ label: '证照台账（CERT）', value: 'CERT' }]"
                  style="width: 100%"
                />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="流程类型">
                <a-select v-model:value="form.lifecycleType" :options="typeOptions" style="width: 100%" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="申请单号">
                <a-input v-model:value="form.applyNo" placeholder="唯一，重复提交会被幂等拒绝" />
              </a-form-item>
            </div>
          </div>
        </div>
      </a-col>

      <!-- CHANGE -->
      <template v-if="form.lifecycleType === T_CHANGE">
        <a-col :span="24">
          <div class="ct-sheet">
            <div class="ct-sheet-group">变更字段清单<span class="ct-sg-note">一行一字段，后端自动归组</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell ct-sheet-wide">
                <div v-for="(row, i) in form.changes ?? []" :key="i" class="flex items-center gap-2 mb-2">
                  <a-select
                    v-model:value="row.fieldName"
                    :options="CERT_FIELDS"
                    style="width: 220px"
                    placeholder="字段"
                    show-search
                    option-filter-prop="label"
                    @change="fillOldValue(row)"
                  />
                  <a-input v-model:value="row.oldValue" style="width: 200px" placeholder="旧值" />
                  <span>→</span>
                  <a-input v-model:value="row.newValue" style="width: 220px" placeholder="新值" />
                  <a-button danger size="small" type="text" @click="removeChange(i)">删除</a-button>
                </div>
                <a-button size="small" @click="addChange">+ 添加字段</a-button>
                <div class="ct-tip">旧值会在选择字段后自动带出当前台账值，可手工修正。</div>
              </div>
            </div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell ct-sheet-wide">
                <a-form-item label="变更摘要">
                  <a-textarea v-model:value="form.changeSummary" :rows="2" placeholder="审批人看" />
                </a-form-item>
              </div>
            </div>
          </div>
        </a-col>
      </template>

      <!-- RENEW -->
      <template v-else-if="form.lifecycleType === T_RENEW">
        <a-col :span="24">
          <div class="ct-sheet">
            <div class="ct-sheet-group">续期<span class="ct-sg-note">方式 / 新编号 / 新有效期</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="续期方式" required>
                  <a-select v-model:value="form.renewalType" :options="renewalTypes" style="width: 100%" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell ct-sheet-wide">
                <a-form-item label="换证后新编号">
                  <a-input v-model:value="form.newCertNo" />
                </a-form-item>
              </div>
            </div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="新有效期起">
                  <a-date-picker v-model:value="form.newValidFrom" style="width: 100%" value-format="YYYY-MM-DD" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="新有效期至" required>
                  <a-date-picker v-model:value="form.newValidTo" style="width: 100%" value-format="YYYY-MM-DD" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="下次年检日">
                  <a-date-picker v-model:value="form.newNextReviewDate" style="width: 100%" value-format="YYYY-MM-DD" />
                </a-form-item>
              </div>
            </div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell ct-sheet-wide">
                <div class="ct-tip" style="padding-top: 6px">生效时一次性回写台账（有效期/编号/下次年检日），并驱动下一轮到期预警。</div>
              </div>
            </div>
          </div>
        </a-col>
      </template>

      <!-- REVOKE -->
      <template v-else-if="form.lifecycleType === T_REVOKE">
        <a-col :span="24">
          <div class="ct-sheet">
            <div class="ct-sheet-group">注销 / 吊销<span class="ct-sg-note">方式 / 决定机关 / 生效时间</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="注销吊销方式" required>
                  <a-select v-model:value="form.revokeType" :options="revokeTypes" style="width: 100%" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="决定机关">
                  <a-input v-model:value="form.revokeAuthority" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="决定生效时间">
                  <a-date-picker v-model:value="form.revokeEffectiveAt" style="width: 100%" value-format="YYYY-MM-DD" />
                </a-form-item>
              </div>
            </div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell ct-sheet-wide">
                <a-form-item label="事由">
                  <a-textarea v-model:value="form.revokeReason" :rows="2" placeholder="吊销/注销事由，写入留痕" />
                </a-form-item>
              </div>
            </div>
          </div>
        </a-col>
      </template>

      <!-- ANNUAL -->
      <template v-else-if="form.lifecycleType === T_ANNUAL">
        <a-col :span="24">
          <div class="ct-sheet">
            <div class="ct-sheet-group">年检<span class="ct-sg-note">年度 / 日期 / 结论</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="年检年度" required>
                  <a-input-number v-model:value="form.reviewYear" style="width: 100%" :min="2000" :max="2100" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="年检日期" required>
                  <a-date-picker v-model:value="form.reviewDate" style="width: 100%" value-format="YYYY-MM-DD" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="年检结论">
                  <a-select v-model:value="form.reviewResult" :options="reviewResults" allow-clear style="width: 100%" />
                </a-form-item>
              </div>
            </div>
          </div>
        </a-col>
      </template>

      <a-col :span="24">
        <div class="ct-sheet" style="margin-top: 14px">
          <div class="ct-sheet-group">佐证材料<span class="ct-sg-note">直接上传，不必填写文件编号</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="佐证材料">
                <a-upload
                  :before-upload="onPickEvidence"
                  :show-upload-list="false"
                  :disabled="evidenceUploading"
                  multiple
                >
                  <a-button :loading="evidenceUploading">{{ evidenceUploading ? '上传中…' : '选择文件上传' }}</a-button>
                </a-upload>
                <ul v-if="evidenceFiles.length" class="mt-2 space-y-1">
                  <li v-for="(f, i) in evidenceFiles" :key="f.fileId" class="flex items-center gap-2 text-sm">
                    <span class="truncate">{{ f.name }}</span>
                    <a class="text-xs text-red-500" @click="removeEvidence(i)">移除</a>
                  </li>
                </ul>
                <div v-else class="ct-tip">佐证材料选填；如需提交请直接选择本地文件上传，系统自动记录文件编号。</div>
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>
    </a-form>
    <div class="ct-tip mt-3">
      流程走「申请 → 审核 → 生效」状态机：<b>审核通过 ≠ 已生效</b>，续期需拿到新证、注销需等决定书，请在「证件全生命周期」页执行生效。
    </div>
  </a-modal>
</template>
