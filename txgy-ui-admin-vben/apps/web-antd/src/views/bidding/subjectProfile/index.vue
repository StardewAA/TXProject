<script lang="ts" setup>
import { SubjectApi } from '#/api/bidding/subject';

import { computed, onMounted, reactive, ref } from 'vue';

import { Alert, Button, Card, Input, message, Radio, Select, Spin } from 'ant-design-vue';

import { fetchSubjectEligibility, fetchSubjectProfile, saveSubjectProfile } from '#/api/bidding/subject';

const createMessage = message;
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const subjectType = ref<SubjectApi.SubjectTypeCode>('BIDDER');
const profile = ref<SubjectApi.Profile>();
const eligibility = ref<SubjectApi.Eligibility>();

interface ProfileForm {
  subjectName: string;
  uscc: string;
  legalPersonName: string;
  legalPersonTypeCode: string;
  regionCode: string;
  industryCode: string;
  regCapital: string;
  regCapCurrency: string;
  contactName: string;
  contactPhone: string;
  licenseFileId: string;
  licenseFileName: string;
  remark: string;
}
const emptyForm = (): ProfileForm => ({
  subjectName: '', uscc: '', legalPersonName: '', legalPersonTypeCode: '', regionCode: '',
  industryCode: '', regCapital: '', regCapCurrency: '156', contactName: '', contactPhone: '',
  licenseFileId: '', licenseFileName: '', remark: '',
});
const form = reactive<ProfileForm>(emptyForm());

const complete = computed(() => profile.value?.complete === true || eligibility.value?.profileComplete === true);
const missingNames = computed(() => (profile.value?.missingRequiredNames?.length ? profile.value.missingRequiredNames : eligibility.value?.missingRequiredNames) || []);

async function loadAll() {
  loading.value = true;
  loaded.value = false;
  try {
    const [pf, el] = await Promise.all([
      fetchSubjectProfile(subjectType.value).catch(() => undefined),
      fetchSubjectEligibility(subjectType.value).catch(() => undefined),
    ]);
    profile.value = pf;
    eligibility.value = el;
    Object.assign(form, emptyForm());
    if (pf) {
      form.subjectName = pf.subjectName || '';
      form.uscc = pf.uscc || '';
      form.legalPersonName = pf.legalPersonName || '';
      form.legalPersonTypeCode = pf.legalPersonTypeCode || '';
      form.regionCode = pf.regionCode || '';
      form.industryCode = pf.industryCode || '';
      form.regCapital = pf.regCapital !== undefined && pf.regCapital !== null ? String(pf.regCapital) : '';
      form.regCapCurrency = pf.regCapCurrency || '156';
      form.contactName = pf.contactName || '';
      form.contactPhone = pf.contactPhone || '';
      form.licenseFileId = pf.licenseFileId !== undefined && pf.licenseFileId !== null ? String(pf.licenseFileId) : '';
      form.licenseFileName = pf.licenseFileName || '';
    }
    loaded.value = true;
  } catch (e) {
    createMessage.error('加载失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
function switchType(t: SubjectApi.SubjectTypeCode) {
  subjectType.value = t;
  void loadAll();
}

function validate(): string | null {
  const need: Array<[unknown, string]> = [
    [form.subjectName, '单位名称'], [form.uscc, '统一社会信用代码'], [form.legalPersonName, '法定代表人姓名'],
    [form.legalPersonTypeCode, '法人机构类别代码'], [form.regionCode, '行政区划代码'],
    [form.contactName, '联系人'], [form.contactPhone, '联系电话'],
  ];
  for (const [v, label] of need) if (!String(v ?? '').trim()) return '请填写必填项：' + label;
  if (!/^[0-9A-HJ-NP-RTUW-Y]{18}$/.test(form.uscc.trim())) return '统一社会信用代码须为 18 位（GB 32100-2015 字符集）';
  if (!/^[0-9A-Z]$/.test(form.legalPersonTypeCode.trim())) return '法人机构类别代码须为 1 位字母/数字';
  if (!/^\d{6}$/.test(form.regionCode.trim())) return '行政区划代码须为 6 位数字';
  return null;
}
async function doSave() {
  const bad = validate();
  if (bad) { createMessage.warning(bad); return; }
  saving.value = true;
  try {
    const numOrUndef = (s: string) => (s.trim() ? Number(s.trim()) : undefined);
    const body: Record<string, unknown> = {
      subjectType: subjectType.value,
      subjectName: form.subjectName.trim(),
      uscc: form.uscc.trim(),
      legalPersonName: form.legalPersonName.trim(),
      legalPersonTypeCode: form.legalPersonTypeCode.trim(),
      regionCode: form.regionCode.trim(),
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
      industryCode: form.industryCode.trim() || undefined,
      regCapital: numOrUndef(form.regCapital),
      regCapCurrency: form.regCapCurrency.trim() || '156',
      licenseFileId: numOrUndef(form.licenseFileId),
      licenseFileName: form.licenseFileName.trim() || undefined,
      remark: form.remark.trim() || undefined,
    };
    await saveSubjectProfile(body);
    createMessage.success('档案已保存' + (complete.value ? '（信息完善）' : '；缺项补齐后即达完善状态'));
    void loadAll();
  } catch (e) {
    createMessage.error('保存失败：' + ((e as Error).message || String(e)));
  } finally {
    saving.value = false;
  }
}

const typeOptions = Object.keys(SubjectApi.SUBJECT_TYPE_LABEL).map((k) => ({ label: SubjectApi.SUBJECT_TYPE_LABEL[k] + '（' + k + '）', value: k }));
const eligibilityTip = () => {
  const e = eligibility.value;
  if (!e) return '';
  if (e.eligible) return '当前具备投标/发布资格（已授权 + 档案完善）';
  const reasons = (e.reasonCodes || []).map((r) => ({ NOT_AUTHORIZED: '未授权', AUTH_DISABLED: '授权已停用', PROFILE_MISSING: '档案缺失', PROFILE_INCOMPLETE: '档案不完善' })[r] || r).join('、');
  return (e.message || '') + (reasons ? '（' + reasons + '）' : '');
};

onMounted(loadAll);
</script>

<template>
  <div class="bid-ind ind-page">
    <IndPageHead :title="'主体档案 · 企业自维护（' + (SubjectApi.SUBJECT_TYPE_LABEL[subjectType] || subjectType) + '）'" mark="PROFILE" sub="同一租户可分别维护投标人（BIDDER）/ 招标人（TENDERER）两套档案——主体类型是企业扮演的角色，非二选一。授权后仍须完善必填信息（资格 = 授权 + 档案完善）。">
      <template #actions>
        <Radio.Group v-model:value="subjectType" option-type="button" :options="typeOptions" @change="switchType(subjectType)" />
      </template>
    </IndPageHead>
    <div class="ind-body">

    <Spin :spinning="loading">
      <div v-if="loaded" class="mb-3 grid grid-cols-1 gap-3 md:grid-cols-2">
        <Alert
          :type="eligibility?.eligible ? 'success' : 'warning'"
          :show-icon="true"
          :message="eligibility ? (eligibility.eligible ? '具备资格' : '暂不具备资格') : '资格状态不可用'"
          :description="eligibilityTip() || '资格状态暂不可用（登录后查看）'"
        />
        <Alert
          :type="complete ? 'success' : 'info'"
          :show-icon="true"
          :message="complete ? '档案信息已完善' : '档案信息尚未完善'"
          :description="missingNames.length ? '缺失必填字段：' + missingNames.join('、') : (complete ? '全部必填字段已齐' : '保存后实时核算')"
        />
      </div>

      <div v-if="loaded" class="form-panel">
        <div class="ind-sect" style="margin-top:0">基本档案 · 法人基本信息 <small>表89 字段口径</small></div>
        <div class="mb-3 text-sm font-medium text-gray-500">USCC 按 GB 32100-2015 校验；行政区划按 GB/T 2260（6 位市级）；必填：单位名称/信用代码/法定代表人/类别/区划/联系人/电话</div>
        <div class="grid grid-cols-1 gap-x-5 gap-y-3 md:grid-cols-3">
          <div>
            <div class="form-lbl">单位名称 *</div>
            <Input v-model:value="form.subjectName" placeholder="与营业执照一致" />
          </div>
          <div>
            <div class="form-lbl">统一社会信用代码 *</div>
            <Input v-model:value="form.uscc" placeholder="18 位（GB 32100-2015）" :maxlength="18" />
          </div>
          <div>
            <div class="form-lbl">法定代表人 *</div>
            <Input v-model:value="form.legalPersonName" />
          </div>
          <div>
            <div class="form-lbl">法人机构类别代码 *（C1）</div>
            <Select v-model:value="form.legalPersonTypeCode" placeholder="1 位字母/数字">
              <Select.Option value="A">A 企业法人</Select.Option>
              <Select.Option value="B">B 事业法人</Select.Option>
              <Select.Option value="C">C 机关法人</Select.Option>
              <Select.Option value="D">D 其他</Select.Option>
            </Select>
          </div>
          <div>
            <div class="form-lbl">行政区划代码 *（C6）</div>
            <Input v-model:value="form.regionCode" placeholder="如 610800 榆林市" :maxlength="6" />
          </div>
          <div>
            <div class="form-lbl">行业代码（C3，GB/T 4754）</div>
            <Input v-model:value="form.industryCode" placeholder="可选" />
          </div>
          <div>
            <div class="form-lbl">联系人 *</div>
            <Input v-model:value="form.contactName" />
          </div>
          <div>
            <div class="form-lbl">联系电话 *</div>
            <Input v-model:value="form.contactPhone" placeholder="手机/座机" />
          </div>
          <div>
            <div class="form-lbl">注册资本（万元）</div>
            <Input v-model:value="form.regCapital" placeholder="可选" />
          </div>
          <div>
            <div class="form-lbl">注册资本币种（默认 156 人民币）</div>
            <Input v-model:value="form.regCapCurrency" placeholder="GB/T 12406-2008" />
          </div>
          <div>
            <div class="form-lbl">营业执照影像 file_id</div>
            <Input v-model:value="form.licenseFileId" placeholder="infra file 主键（可选）" />
          </div>
          <div>
            <div class="form-lbl">影像附件名</div>
            <Input v-model:value="form.licenseFileName" placeholder="可选" />
          </div>
        </div>
        <div class="mt-4 flex items-center justify-end gap-2">
          <Button @click="loadAll">重置</Button>
          <Button type="primary" :loading="saving" @click="doSave">保存档案</Button>
        </div>
      </div>

      <Card v-if="!loaded && !loading" class="mt-3">
        <div class="text-xs text-gray-400">档案加载失败或无数据？刷新重试；本页为“企业自维护”入口（权限 bidding:subject:query/update）。</div>
      </Card>
    </Spin>
    </div>

  </div>
</template>
