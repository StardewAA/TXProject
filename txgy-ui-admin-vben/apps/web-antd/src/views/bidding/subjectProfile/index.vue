<!-- ============================================================================
  主体档案 · 企业自维护（投标人 BIDDER / 招标人 TENDERER）
  业务（07-主体域前端交付-业务口径说明）：档案对象 = 企业（法人/其他组织），不是个人。
    主档案与投标/招标资格挂企业；人员只是企业下经办自然人（由企业绑在其档案下，人员不单独构成主体）。
    同一租户可分别维护 投标人/招标人 两套档案（主体类型是企业扮演的角色，非二选一）。
  理由：资格审查的法定对象是企业（营业执照/统一社会信用代码/资质）；公告/公示/合同署名主体是企业；
    uscc 是企业档案唯一键（GB 32100，个人无 uscc 无法承担主键）——把档案做成人员粒度会“公告主体对不上”。
  依据（法规/技术规范，含文件类型）：
    · 招标投标法 第25/26条（法律）——投标人应为法人/其他组织并具备资格条件；
    · 电子招标投标办法 第24/14条（部门规章/政府文件，八部委令第20号）——注册登记、如实递交并经验证、初始信息真实性验证；
    · GB 32100-2015（技术规范）——统一社会信用代码 18 位编码规则与校验位；GB/T 2260（行政区划）等数据规范表89 法人基本信息。
  后端契约：07-主体域 §3/§4（企业信息卡字段、资格提示话术）；04-全链路 链路A-A1/链路D-D1/D3；
    GET /bidding/subject/profile/get、POST /save（T 轨本租户隔离）、GET /eligibility（授权∧档案完善=资格门）。
  权限点：bidding:subject:query / update（企业自维护）。
============================================================================ -->
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

/* 保存前校验：必填 + uscc 18 位（GB 32100-2015，技术规范）。
 * 依据：07 §5 易错点「uscc 用 18 位校验，非法格式应拦截」；后端 save 亦强校验（错误码 106_035）。 */
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
/* 保存档案（新建/更新，一租户一类型一档案）：后端实时核算完善度并校验 uscc。
 * 业务/理由：企业如实递交并经平台验证（电子办法 24/14 条，部门规章/政府文件）；授权≠完善——保存后档案齐=资格门一半达成。
 * 契约：04-全链路 D1（save 幂等）；错误码 106_035（USCC 不合法）。 */
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
        <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">主体档案 · 企业自维护（{{ SubjectApi.SUBJECT_TYPE_LABEL[subjectType] || subjectType }}）<span class="ind-mark">PROFILE</span></div>
        <div class="ind-sub">同一租户可分别维护投标人（BIDDER）/ 招标人（TENDERER）两套档案——主体类型是企业扮演的角色，非二选一。授权后仍须完善必填信息（资格 = 授权 + 档案完善）。</div>
      </div>
      <div class="ind-actions">
        <Radio.Group v-model:value="subjectType" option-type="button" :options="typeOptions" @change="switchType(subjectType)" />
      </div>
    </div>
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
