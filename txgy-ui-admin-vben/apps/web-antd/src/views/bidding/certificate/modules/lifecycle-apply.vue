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
  type CertificateApi,
  getCertificatePage,
} from '#/api/bidding/certificate';

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
  { label: '有效期止', value: 'validTo' },
  { label: '电子证照标识', value: 'eCertIdent' },
  { label: '备注', value: 'remark' },
];

const saving = ref(false);
const picking = ref(false);
const options = ref<Array<{ label: string; value: number }>>([]);
const picked = ref<CertificateApi.Certificate | null>(null);

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
/** 选中字段后自动带出当前值作为 oldValue（减少手填，仍可改） */
function fillOldValue(row: CertificateApi.FieldChange) {
  const src = active.value as unknown as null | Record<string, unknown>;
  const v = src && row.fieldName ? src[row.fieldName] : undefined;
  row.oldValue = v === undefined || v === null ? '' : String(v);
}

watch(
  () => props.open,
  (v) => {
    if (!v) return;
    picked.value = null;
    options.value = [];
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
  if (form.lifecycleType === T_RENEW && !form.newValidTo) return '续期必须填写续期后的有效期止';
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
  payload.evidenceFileIds = form.evidenceFileIds;
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
    <a-row :gutter="16">
      <a-col v-if="!cert" :span="24">
        <div class="ct-lb"><span class="ct-ast">*</span>作用对象（证照）</div>
        <a-select
          class="w-full mb-3"
          show-search
          :filter-option="false"
          :loading="picking"
          :options="options"
          placeholder="输入证照名称搜索（模糊）"
          @search="searchCerts"
          @change="onPickCert"
        />
      </a-col>
      <a-col v-else :span="24">
        <div class="ct-lb">作用对象</div>
        <div class="mb-3">
          <a-tag>{{ cert.certName }}</a-tag>
          <span class="ct-id">{{ cert.certNo }}</span>
          <span class="ct-panel-meta">（证照 #{{ cert.id }}）</span>
        </div>
      </a-col>

      <a-col :span="8">
        <div class="ct-lb">对象域</div>
        <a-select
          v-model:value="form.bizDomain"
          class="w-full mb-3"
          :options="bizDomains.length ? bizDomains : [{ label: '证照台账（CERT）', value: 'CERT' }]"
        />
      </a-col>
      <a-col :span="8">
        <div class="ct-lb"><span class="ct-ast">*</span>流程类型</div>
        <a-select v-model:value="form.lifecycleType" class="w-full mb-3" :options="typeOptions" />
      </a-col>
      <a-col :span="8">
        <div class="ct-lb"><span class="ct-ast">*</span>申请单号</div>
        <a-input v-model:value="form.applyNo" class="mb-3" placeholder="唯一，重复提交会被幂等拒绝" />
      </a-col>

      <!-- CHANGE -->
      <template v-if="form.lifecycleType === T_CHANGE">
        <a-col :span="24">
          <div class="ct-lb"><span class="ct-ast">*</span>变更字段清单（一行一字段，后端自动归组）</div>
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
        </a-col>
        <a-col :span="24">
          <div class="ct-lb mt-3">变更摘要（审批人看）</div>
          <a-textarea v-model:value="form.changeSummary" :rows="2" class="mb-3" />
        </a-col>
      </template>

      <!-- RENEW -->
      <template v-else-if="form.lifecycleType === T_RENEW">
        <a-col :span="12">
          <div class="ct-lb"><span class="ct-ast">*</span>续期方式</div>
          <a-select v-model:value="form.renewalType" class="w-full mb-3" :options="renewalTypes" />
        </a-col>
        <a-col :span="12">
          <div class="ct-lb">换证后新编号</div>
          <a-input v-model:value="form.newCertNo" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">新有效期起</div>
          <a-date-picker v-model:value="form.newValidFrom" class="w-full mb-3" value-format="YYYY-MM-DD" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb"><span class="ct-ast">*</span>新有效期止</div>
          <a-date-picker v-model:value="form.newValidTo" class="w-full mb-3" value-format="YYYY-MM-DD" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">下次年检日</div>
          <a-date-picker v-model:value="form.newNextReviewDate" class="w-full mb-3" value-format="YYYY-MM-DD" />
        </a-col>
        <a-col :span="24">
          <div class="ct-tip">生效时一次性回写台账（有效期/编号/下次年检日），并驱动下一轮到期预警。</div>
        </a-col>
      </template>

      <!-- REVOKE -->
      <template v-else-if="form.lifecycleType === T_REVOKE">
        <a-col :span="8">
          <div class="ct-lb"><span class="ct-ast">*</span>注销吊销方式</div>
          <a-select v-model:value="form.revokeType" class="w-full mb-3" :options="revokeTypes" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">决定机关</div>
          <a-input v-model:value="form.revokeAuthority" class="mb-3" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">决定生效时间</div>
          <a-date-picker
            v-model:value="form.revokeEffectiveAt"
            class="w-full mb-3"
            value-format="YYYY-MM-DD HH:mm:ss"
            show-time
          />
        </a-col>
        <a-col :span="24">
          <div class="ct-lb">事由</div>
          <a-textarea v-model:value="form.revokeReason" :rows="2" class="mb-3" placeholder="吊销/注销事由，写入留痕" />
        </a-col>
      </template>

      <!-- ANNUAL -->
      <template v-else-if="form.lifecycleType === T_ANNUAL">
        <a-col :span="8">
          <div class="ct-lb"><span class="ct-ast">*</span>年检年度</div>
          <a-input-number v-model:value="form.reviewYear" class="w-full mb-3" :min="2000" :max="2100" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb"><span class="ct-ast">*</span>年检日期</div>
          <a-date-picker v-model:value="form.reviewDate" class="w-full mb-3" value-format="YYYY-MM-DD" />
        </a-col>
        <a-col :span="8">
          <div class="ct-lb">年检结论</div>
          <a-select v-model:value="form.reviewResult" class="w-full mb-3" :options="reviewResults" allow-clear />
        </a-col>
      </template>

      <a-col :span="24">
        <div class="ct-lb">佐证材料文件 ID（多个用半角逗号分隔）</div>
        <a-input v-model:value="form.evidenceFileIds" class="mb-3" placeholder="如 1024,1025（先在文件版本中上传取得 fileId）" />
      </a-col>
      <a-col :span="24">
        <div class="ct-lb">备注</div>
        <a-textarea v-model:value="form.remark" :rows="2" />
      </a-col>
    </a-row>
    <div class="ct-tip mt-3">
      流程走「申请 → 审核 → 生效」状态机：<b>审核通过 ≠ 已生效</b>，续期需拿到新证、注销需等决定书，请在「证件全生命周期」页执行生效。
    </div>
  </a-modal>
</template>
