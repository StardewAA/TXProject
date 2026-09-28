<script setup lang="ts">
/* 证照台账 · 建档 / 编辑独立页（设计约束：建档与编辑用独立跳转页面，非弹窗）
 * 路由：/bidding/certificate/create（建档）/ /bidding/certificate/:id/edit（编辑）
 * 契约 §2.3 / §2.4：建档必填 certName/certNo/certType/holderName；validTo 留空=长期；
 *   更新必须回传 id+version（乐观锁，1060106002 自动重载让用户重新确认）；幂等不做自动重试。
 * 返回：顶栏「‹ 返回证照台账」= router.back()，回到列表（列表筛选/分页经 URL Query 保留）。 */
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

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

import { useCertMeta } from './useCertMeta';

const route = useRoute();
const router = useRouter();
const { dictOptions } = useCertMeta();

/** 编辑态：路由带 :id；建档态无 id */
const certId = computed(() => (route.params.id ? String(route.params.id) : ''));
const isEdit = computed(() => !!certId.value);

const formRef = ref();
const saving = ref(false);

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

/* 持证主体：能选就不要手打（与建档弹窗同一口径） */
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
function pickPerson(id: number | string): void {
  const hit = personOptions.value.find((o) => o.value === id);
  form.holderId = id as number;
  if (hit) form.holderName = hit.label.split(' · ')[0] ?? form.holderName;
}
async function takeOrgName(): Promise<void> {
  try {
    const p = await fetchSubjectProfile();
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
watch(
  () => form.holderType,
  (t) => {
    if (t === 'PERSON') void loadPersons();
    else form.holderId = undefined;
  },
);

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

/** 编辑态：打开即以 §2.2 详情回填（契约 §2.4④：不得用列表行旧值直接编辑，version 必须最新） */
const latest = ref<CertificateApi.Certificate | null>(null);
const loadError = ref('');
async function loadDetail(): Promise<void> {
  if (!certId.value) return;
  try {
    latest.value = await getCertificate(certId.value);
    fill(latest.value);
    if (form.holderType === 'PERSON') void loadPersons();
  } catch (e) {
    loadError.value = (e instanceof Error ? e.message : String(e)) || '证照详情读取失败';
  }
}

async function reloadLatest(): Promise<void> {
  if (!certId.value) return;
  try {
    const latest = await getCertificate(certId.value);
    fill(latest);
    message.warning('该证照已被他人修改，已载入最新数据，请确认后重新提交');
  } catch {
    /* 详情也取不到时保留用户输入，由后端 msg 提示 */
  }
}

onMounted(() => {
  if (isEdit.value) void loadDetail();
});

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    let id: number | string | undefined;
    if (isEdit.value) {
      await updateCertificate({
        ...form,
        id: Number(certId.value),
        version: latest.value?.version,
      });
      message.success('已保存');
      id = certId.value;
    } else {
      id = (await createCertificate({ ...form })) as number;
      message.success('建档成功');
    }
    // 保存后跳转证照详情页（去掉建档/编辑态）
    void router.replace(`/bidding/certificate/${id}`);
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.CERT_NO_DUPLICATE) {
      formRef.value?.setFields([{ name: 'certNo', errors: ['该编号已登记'] }]);
    } else if (code === CERT_ERR.OPTIMISTIC_LOCK) {
      await reloadLatest();
    } else if (code === CERT_ERR.CERT_NOT_FOUND) {
      message.error('证照不存在，返回台账');
      void router.back();
    } else if (code === 400) {
      const fields = certFieldErrorFields(error);
      if (fields.length > 0) {
        formRef.value?.setFields(fields);
        message.warning(`有 ${fields.length} 处字段需要修正，详见表单提示`);
      }
    } else if (code === 500) {
      const traceId = certTraceId(error);
      if (traceId) message.error(`服务异常，请把 traceId 反馈运维：${traceId}`);
    }
    // 其余错误（幂等拦截 / 403）由请求层统一 toast，不自动重试
  } finally {
    saving.value = false;
  }
}

function goBack(): void {
  router.back();
}
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">{{ isEdit ? '编辑证照' : '证照建档' }}</h2>
        <p class="ct-sub">
          {{ isEdit ? '编辑后保存回写台账；编号/大类建档后不可改。' : '证书编号租户内唯一；有效期留空=长期（不参与到期扫描）。' }}
        </p>
      </div>
      <div class="ct-actions">
        <a class="mr-3 text-sm text-blue-600" @click="goBack">‹ 返回证照台账</a>
        <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
      </div>
    </div>

    <a-alert v-if="loadError" type="error" show-icon class="mb-3" :message="loadError" />

    <div class="ct-panel">
      <div class="ct-panel-b p-4">
        <a-form ref="formRef" :model="form" :rules="rules" layout="horizontal">
          <!-- ① 证照标识 -->
          <div class="ct-sheet">
            <div class="ct-sheet-group">证照标识<span class="ct-sg-note">编号 / 大类 / 类型代码</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="证书编号" name="certNo">
                  <a-input v-model:value="form.certNo" placeholder="租户内唯一；L3 敏感字段" :disabled="isEdit" allow-clear />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="证照大类" name="certType">
                  <a-select
                    v-if="certTypes.length > 0"
                    v-model:value="form.certType"
                    :options="certTypes"
                    placeholder="请选择"
                    show-search
                    option-filter-prop="label"
                    allow-clear
                  />
                  <a-select v-else :options="[]" placeholder="证照大类字典未配置，请联系管理员补种" disabled />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="类型代码" name="certCategoryCode">
                  <a-input v-model:value="form.certCategoryCode" placeholder="GB/T 36902，可空" allow-clear />
                </a-form-item>
              </div>
            </div>
          </div>

          <!-- ② 证照信息 -->
          <div class="ct-sheet">
            <div class="ct-sheet-group">证照信息<span class="ct-sg-note">名称 / 等级 / 业务范围</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell ct-sheet-wide">
                <a-form-item label="证照名称" name="certName">
                  <a-input v-model:value="form.certName" placeholder="建议与证载名称一致，便于检索" allow-clear />
                </a-form-item>
              </div>
            </div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="等级" name="certLevel">
                  <a-input v-model:value="form.certLevel" placeholder="自由文本，禁跨标准比较" allow-clear />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell ct-sheet-wide">
                <a-form-item label="许可/业务范围" name="certScope">
                  <a-input v-model:value="form.certScope" placeholder="可空" allow-clear />
                </a-form-item>
              </div>
            </div>
          </div>

          <!-- ③ 持证主体 -->
          <div class="ct-sheet">
            <div class="ct-sheet-group">持证主体<span class="ct-sg-note">类别 / 名称（能选就不手打）</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="主体类别" name="holderType">
                  <a-select v-model:value="form.holderType" :options="holderTypes" allow-clear />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell ct-sheet-wide">
                <a-form-item label="主体名称" name="holderName">
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
                  <a-input v-else v-model:value="form.holderName" allow-clear />
                </a-form-item>
                <div v-if="form.holderType === 'ORG'" class="ct-tip">
                  <a class="text-xs text-blue-600" @click="takeOrgName">用「主体档案」的本企业名称</a>
                </div>
                <div v-if="form.holderId" class="ct-tip">已关联员工档案：{{ form.holderId }}</div>
              </div>
            </div>
          </div>

          <!-- ④ 颁发与有效期 -->
          <div class="ct-sheet">
            <div class="ct-sheet-group">颁发与有效期<span class="ct-sg-note">机构 / 日期</span></div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="颁发机构" name="issuingAuthority">
                  <a-input v-model:value="form.issuingAuthority" allow-clear />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="机构代码" name="issuingAuthorityCode">
                  <a-input v-model:value="form.issuingAuthorityCode" allow-clear />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="颁发日期" name="issueDate">
                  <a-date-picker v-model:value="form.issueDate" class="w-full" value-format="YYYY-MM-DD" />
                </a-form-item>
              </div>
            </div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell">
                <a-form-item label="有效期起" name="validFrom">
                  <a-date-picker v-model:value="form.validFrom" class="w-full" value-format="YYYY-MM-DD" />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <a-form-item label="有效期至" name="validTo">
                  <a-date-picker v-model:value="form.validTo" class="w-full" value-format="YYYY-MM-DD" placeholder="留空=长期" allow-clear />
                </a-form-item>
              </div>
              <div class="ct-sheet-cell">
                <div class="ct-tip" style="padding-top: 8px">留空表示长期有效：长期证不参与到期预警扫描。</div>
              </div>
            </div>
          </div>

          <!-- ⑤ 备注 -->
          <div class="ct-sheet">
            <div class="ct-sheet-group">备注</div>
            <div class="ct-sheet-row">
              <div class="ct-sheet-cell ct-sheet-wide">
                <a-form-item label="备注" name="remark">
                  <a-textarea v-model:value="form.remark" :rows="2" allow-clear />
                </a-form-item>
              </div>
            </div>
          </div>
          <div v-if="isEdit" class="ct-tip">乐观锁版本：v{{ latest?.version ?? '—' }}（保存时回传，冲突会自动重载最新数据）</div>
        </a-form>
      </div>
    </div>
  </div>
</template>
