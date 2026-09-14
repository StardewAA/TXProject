<script setup lang="ts">
/* 证照建档 / 更新（契约 §2.3 / §2.4）
 * · 建档必填：certName / certNo / certType / holderName；validTo 留空 = 长期有效（不入到期扫描）
 * · 更新必须回传 id + version（乐观锁），1060106002 时自动重载详情让用户重新确认
 * · 幂等：提交中禁用按钮；幂等拦截（"重复请求，请稍后重试"）不做自动重试 */
import { computed, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import {
  CERT_ERR,
  certErrorCode,
  certFieldErrorFields,
  type CertificateApi,
  certTraceId,
  createCertificate,
  getCertificate,
  updateCertificate,
} from '#/api/bidding/certificate';
import { getPersonnelProfilePage } from '#/api/bidding/personnel';
import { fetchSubjectProfile } from '#/api/bidding/subject';

import { useCertMeta } from '../useCertMeta';

const props = defineProps<{
  open: boolean;
  record?: CertificateApi.Certificate | null;
}>();
const emit = defineEmits<{
  (e: 'saved', id?: number): void;
  (e: 'update:open', v: boolean): void;
}>();

const { dictOptions } = useCertMeta();
const formRef = ref();
const saving = ref(false);

const isEdit = computed(() => !!props.record?.id);

function blank(): CertificateApi.SaveReq {
  return {
    certName: '',
    certNo: '',
    certType: undefined,
    certCategoryCode: undefined,
    certLevel: undefined,
    certScope: undefined,
    holderName: '',
    holderType: 'ORG',
    holderId: undefined,
    issuingAuthority: undefined,
    issuingAuthorityCode: undefined,
    issueDate: undefined,
    validFrom: undefined,
    validTo: undefined,
    remark: undefined,
  };
}
const form = reactive<CertificateApi.SaveReq>(blank());

/* 持证主体：**能选就不要手打**（2026-09-14 修正"应由系统给出的数据却让用户输入"）
 *   · 后端 CertificateSaveReqVO 的 holderId 注释是"**关联档案 ID**"（人员证＝员工档案 id），
 *     而本页此前只渲染了一个 holderName 文本框 ⇒ holderId 永远为空，**证照与持证主体的关联一直被丢掉**；
 *   · 现在：类别=本企业(ORG) 可一键取「主体档案」单位名；类别=员工(PERSON) 从员工档案**选择**并回填 holderId；
 *     类别=外部(EXTERNAL) 保留手填（外部机构不在本系统档案里，手填是合理的）。 */
/** 已加载的员工档案（PERSON 类别的可选项） */
const personOptions = ref<Array<{ label: string; value: number | string }>>([]);
const personLoading = ref(false);
async function loadPersons(): Promise<void> {
  if (personOptions.value.length || personLoading.value) return;
  personLoading.value = true;
  try {
    const res = await getPersonnelProfilePage({ pageNo: 1, pageSize: 200 });
    personOptions.value = (res?.list ?? [])
      .filter((p) => p.id !== undefined && p.personName)
      .map((p) => ({ label: (p.personName ?? '') + (p.personNo ? ' · ' + p.personNo : ''), value: p.id as number }));
  } catch {
    personOptions.value = [];
  } finally {
    personLoading.value = false;
  }
}
/** 选中员工：姓名与关联档案一起回填（避免手打错名导致与档案对不上） */
function pickPerson(id: number | string): void {
  const hit = personOptions.value.find((o) => o.value === id);
  form.holderId = id as number;
  if (hit) form.holderName = hit.label.split(' · ')[0] ?? form.holderName;
}
/** 本企业主体名一键带入（来源：主体档案，与公告工作台的发布单位同一口径） */
async function takeOrgName(): Promise<void> {
  try {
    const p = (await fetchSubjectProfile('TENDERER')) ?? (await fetchSubjectProfile('BIDDER'));
    if (p?.subjectName) {
      form.holderName = p.subjectName;
      message.success('已带入主体档案单位名');
    } else {
      message.warning('主体档案尚无单位名，请在「主体档案」维护后再带入');
    }
  } catch {
    message.warning('主体档案读取失败（可能无权限或未建档），请手工填写');
  }
}
/** 类别切换：离开 PERSON 时清掉 holderId（其后端语义是"关联档案 ID"，对 ORG 不该带旧人员 id） */
watch(
  () => form.holderType,
  (t) => {
    if (t === 'PERSON') void loadPersons();
    else form.holderId = undefined;
  },
);

/** 持证主体类别：优先取字典 holder_type；字典未提供时用契约 §2.3 明示的三值（ORG/PERSON/EXTERNAL） */
const holderTypes = computed(() => {
  const fromDict = dictOptions('holder_type');
  return fromDict.length > 0
    ? fromDict
    : [
        { label: '机构', value: 'ORG' },
        { label: '个人', value: 'PERSON' },
        { label: '外部', value: 'EXTERNAL' },
      ];
});
const certTypes = computed(() => dictOptions('cert_type'));

const rules = {
  certName: [{ required: true, message: '证照名称不能为空', trigger: 'blur' }],
  certNo: [{ required: true, message: '证书编号不能为空（唯一）', trigger: 'blur' }],
  certType: [{ required: true, message: '证照大类不能为空', trigger: 'change' }],
  holderName: [{ required: true, message: '持证主体名称不能为空', trigger: 'blur' }],
};

function fill(record?: CertificateApi.Certificate | null) {
  Object.assign(form, blank());
  if (!record) return;
  Object.assign(form, {
    certName: record.certName,
    certNo: record.certNo,
    certType: record.certType,
    certCategoryCode: record.certCategoryCode,
    certLevel: record.certLevel,
    certScope: record.certScope,
    holderName: record.holderName,
    holderType: record.holderType ?? 'ORG',
    holderId: record.holderId,
    issuingAuthority: record.issuingAuthority,
    issuingAuthorityCode: record.issuingAuthorityCode,
    issueDate: record.issueDate,
    validFrom: record.validFrom,
    validTo: record.validTo ?? undefined,
    remark: record.remark,
  });
}

/** 打开即以 §2.2 详情回填（契约 §2.4④：不得用列表行旧值直接编辑，version 必须最新） */
const latest = ref<CertificateApi.Certificate | null>(null);
async function loadDetail() {
  if (!props.record?.id) return;
  try {
    latest.value = await getCertificate(props.record.id);
    fill(latest.value);
  } catch {
    latest.value = null; // 拉不到退回列表行数据，提交冲突由后端 1060106002 兜底
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      latest.value = null;
      fill(props.record);
      formRef.value?.clearValidate?.();
      void loadDetail();
    }
  },
);

/** 乐观锁冲突：重新拉取详情（契约 §2.4：自动重新拉取详情并提示用户重新确认改动） */
async function reloadLatest() {
  if (!props.record?.id) return;
  try {
    const latest = await getCertificate(props.record.id);
    fill(latest);
    message.warning('该证照已被他人修改，已载入最新数据，请确认后重新提交');
  } catch {
    /* 详情也取不到时保留用户输入，由后端 msg 提示 */
  }
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      // version 取刚拉到的详情（回退列表行），避免陈旧版本触发 1060106002
      await updateCertificate({
        ...form,
        id: props.record?.id,
        version: latest.value?.version ?? props.record?.version,
      });
      message.success('已保存');
      emit('saved', props.record?.id);
    } else {
      const id = await createCertificate({ ...form });
      message.success('建档成功');
      emit('saved', id as number);
    }
    emit('update:open', false);
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.CERT_NO_DUPLICATE) {
      formRef.value?.setFields([{ name: 'certNo', errors: ['该编号已登记'] }]);
    } else if (code === CERT_ERR.OPTIMISTIC_LOCK) {
      await reloadLatest();
    } else if (code === CERT_ERR.CERT_NOT_FOUND) {
      message.error('证照不存在，已刷新列表');
      emit('update:open', false);
      emit('saved');
    } else if (code === 400) {
      // 契约 §0.4：400 的 data.fieldErrors 必须逐字段落到表单项（后端 BiddingValidationAdvice 已返回）
      const fields = certFieldErrorFields(error);
      if (fields.length > 0) {
        formRef.value?.setFields(fields);
        message.warning(`有 ${fields.length} 处字段需要修正，详见表单提示`);
      }
    } else if (code === 500) {
      // 契约 §0.4：500 必须带 traceId 反馈运维
      const traceId = certTraceId(error);
      if (traceId) message.error(`服务异常，请把 traceId 反馈运维：${traceId}`);
    }
    // 其余错误（幂等拦截 / 403）由请求层统一 toast 后端 msg，此处不重复提示，
    // 且**禁止**对幂等拦截做自动重试（契约 §9）。
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="isEdit ? `编辑证照 #${record?.id ?? ''}` : '证照建档'"
    :width="880"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    destroy-on-close
    @ok="submit"
    @cancel="emit('update:open', false)"
  >
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="证照名称" name="certName">
            <a-input v-model:value="form.certName" placeholder="建议与证载名称一致，便于检索" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="证书编号" name="certNo">
            <a-input v-model:value="form.certNo" placeholder="租户内唯一；L3 敏感字段" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="证照大类" name="certType">
            <a-select
              v-if="certTypes.length > 0"
              v-model:value="form.certType"
              :options="certTypes"
              placeholder="请选择（取值来自字典 cert_type）"
              show-search
              option-filter-prop="label"
              allow-clear
            />
            <!-- 字典 cert_type 未配置时的降级：证书大类是必填项，不能让建档被字典缺失卡死 -->
            <a-input
              v-else
              v-model:value="form.certType"
              placeholder="字典 cert_type 未配置，可直接填类别 code"
            />
            <div v-if="certTypes.length === 0" class="ct-tip">
              建议在「字典管理」补种 cert_type 字典，之后本项自动变为下拉。
            </div>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="GB/T 36902 类型代码" name="certCategoryCode">
            <a-input v-model:value="form.certCategoryCode" placeholder="可空" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="等级" name="certLevel">
            <a-input v-model:value="form.certLevel" placeholder="自由文本，禁跨标准比较" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="许可/业务范围" name="certScope">
            <a-input v-model:value="form.certScope" placeholder="可空" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="持证主体类别" name="holderType">
            <a-select v-model:value="form.holderType" :options="holderTypes" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="持证主体名称" name="holderName">
            <!-- 人员证：直接从员工档案选择（value 即"关联档案 ID"，见 holderId 契约） -->
            <a-select
              v-if="form.holderType === 'PERSON'"
              v-model:value="form.holderId"
              :options="personOptions"
              :loading="personLoading"
              show-search
              option-filter-prop="label"
              placeholder="选择员工（来自员工档案）"
              allow-clear
              @change="pickPerson"
            />
            <!-- 本企业 / 外部机构：手填（本企业可一键带入主体档案单位名） -->
            <a-input v-else v-model:value="form.holderName" allow-clear />
            <div v-if="form.holderType === 'ORG'" class="ct-tip">
              <a class="text-xs text-blue-600" @click="takeOrgName">用「主体档案」的本企业名称</a>
            </div>
            <div v-if="form.holderId" class="ct-tip">已关联档案 ID：{{ form.holderId }}</div>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="关联档案 ID" name="holderId">
            <a-input-number v-model:value="form.holderId" class="w-full" :min="1" placeholder="可空" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="颁发机构" name="issuingAuthority">
            <a-input v-model:value="form.issuingAuthority" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="颁发机构代码" name="issuingAuthorityCode">
            <a-input v-model:value="form.issuingAuthorityCode" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="颁发日期" name="issueDate">
            <a-date-picker v-model:value="form.issueDate" class="w-full" value-format="YYYY-MM-DD" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="有效期起" name="validFrom">
            <a-date-picker v-model:value="form.validFrom" class="w-full" value-format="YYYY-MM-DD" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="有效期至" name="validTo">
            <a-date-picker v-model:value="form.validTo" class="w-full" value-format="YYYY-MM-DD" placeholder="留空 = 长期有效" allow-clear />
            <div class="ct-tip">留空表示长期有效：长期证不参与到期预警扫描。</div>
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="备注" name="remark">
            <a-textarea v-model:value="form.remark" :rows="2" allow-clear />
          </a-form-item>
        </a-col>
      </a-row>
      <div v-if="isEdit" class="ct-tip">乐观锁版本：v{{ record?.version ?? '—' }}（保存时回传，冲突会自动重载最新数据）</div>
    </a-form>
  </a-modal>
</template>
