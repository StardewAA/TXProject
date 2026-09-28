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
import { computed, onMounted, reactive, ref } from 'vue';

import { Alert, Button, Card, Input, message, Modal, Select, Spin, Tag, Upload } from 'ant-design-vue';

import { buildBiddingFileDownloadUrl, withBiddingFileToken } from '#/api/bidding/file';
import { SubjectApi } from '#/api/bidding/subject';
import { deleteSubjectLicense, fetchSubjectEligibility, fetchSubjectProfile, saveSubjectProfile } from '#/api/bidding/subject';

import CaSignEntry, { type CaSignFile } from '../ca/CaSignEntry.vue';
import { eligibleReasonText } from '../subject/labels';
import { uploadAttachment } from '../vo/attachmentUpload';

const createMessage = message;
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
/* 契约《主体档案合并一租户一档》：投标人/招标人是企业扮演的角色，共用同一份企业档案。
 * ⇒ 读接口不传 subjectType（取本租户唯一档案）、保存也不传（后端把它降级为可选角色标记，不传默认 BIDDER）。
 * 原先这里硬编码 'TENDERER' 只是后端未落地前的**兼容参数**，已随后端落地（93cb6b7a2）删除 ——
 * 继续传会把"投标人/招标人共用的一份档案"错误地钉成 TENDERER 标记。
 * 将来若要"按角色查看资格"（如投标页 vs 招标页分别判定），显式传参即可。 */
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

/* ===== 资格两条件（2026-10-02 用户口径：必须摊开说清，不能两张卡各说一半） =====
 * 资格 = ① 平台授权 ∧ ② 企业档案完善，二者缺一不可（后端 SubjectEligibilityService 判定）。
 * 此前页面是左右两张 Alert（「具备资格」+「档案信息已完善」），读者看到
 * 「档案已完善」却「暂不具备资格」无法理解 —— 条件必须并列展示、缺失项写在各自条件行上。 */
/** 条件①：是否已获平台授权（bidding_subject_auth，ENABLED） */
const authorized = computed(() => eligibility.value?.authorized === true);
/** 条件①的授权时间后缀（有则「（2026-10-02）」） */
const authTimeText = computed(() => {
  const t = eligibility.value?.authorizeTime;
  if (!t) return '';
  const d = new Date(Number(t));
  if (Number.isNaN(d.getTime())) return '';
  const p = (n: number) => String(n).padStart(2, '0');
  return '（' + d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + '）';
});
/** 条件①未达成时的补充原因：仅透出「授权已停用」这类状态原因；
 *  NOT_AUTHORIZED 本身就是"未授权"，不必在同一行重复一遍 */
const authReasonText = computed(() => {
  const e = eligibility.value;
  if (!e || authorized.value) return '';
  return eligibleReasonText((e.reasonCodes || []).filter((c) => c !== 'NOT_AUTHORIZED'));
});
/** 条件②未达成时的缺失清单（档案接口优先，回退资格接口） */
const profileMissingText = computed(() => (missingNames.value.length ? '缺失必填字段：' + missingNames.value.join('、') : '保存后实时核算'));

async function loadAll() {
  loading.value = true;
  loaded.value = false;
  try {
    // 单一企业档案：投标/招标共用一份，接口 subjectType 可选（契约《主体档案合并一租户一档》）
    const [pf, el] = await Promise.all([
      fetchSubjectProfile().catch(() => undefined),
      fetchSubjectEligibility().catch(() => undefined),
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


/* 保存前校验：必填 + uscc 18 位（GB 32100-2015，技术规范）。
 * 依据：07 §5 易错点「uscc 用 18 位校验，非法格式应拦截」；后端 save 亦强校验（错误码 106_035）。 */
function validate(): null | string {
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
      // 契约《主体档案合并一租户一档》：不传 subjectType（后端降级为角色标记，不传默认 BIDDER），不再按角色分档
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

/* ===== 营业执照影像：标准上传交互（选择/拖拽文件 → 上传 → 前端自动绑定系统返回的 file_id）=====
 * 业务/理由：file_id 是平台文件服务生成的系统主键，用户不可能知道也拿不到，
 *   因此绝不能让用户手填；用户只做"选文件"，file_id 由前端在上传成功后写入表单隐藏字段随表单提交。
 * 依据：07-主体域 §3（营业执照影像是企业资格法定材料）；红线 8（文件三件套 file_id/sha256/size）；
 *   后端 BiddingFileController 注释：彻底移除"让用户手填文件 ID"的退化交互。
 * 契约：POST /bidding/file/upload（响应必带 fileId，并同时回 url/sha256/size，供即时查看）。
 * 链路：uploadAttachment 单一链路（主 /bidding/file/upload，降级预签名直传），均保证返回 fileId；
 *   拿不到 fileId 直接抛「文件注册失败，请重试」，前端绝不会退回"让用户手填 ID"（规范 §3.2/§3.4）。 */
const licenseUploading = ref(false);
/** 上传回执里的影像地址（仅本次会话有效：上传响应会一并返回 url，用于即时查看） */
const licenseUploadedUrl = ref('');
/** 影像访问地址（**刷新后也能看**）：
 *   · 本会话刚上传 → 用上传回执的 url（免再取一次）；
 *   · 刷新/重进页面 → 表单里只有 file_id（后端出参只回 fileId + fileName，不回 url），
 *     此时用 file_id 走**业务受控出口** /admin-api/bidding/file/download?id=（需登录、内联预览）。
 *  原实现把地址只存在上传回执里，刷新后 licenseFileUrl 为空 ⇒ 「查看」链接直接消失（用户实测：保存前能看、刷新就看不到）。 */
/** ⚠️ fileId 分支必须带 token：`<a target="_blank">` 由浏览器自己发请求、不带 Authorization 头，
 *  而业务出口要求登录 —— 实测裸 URL 返回 {"code":401}，补 ?token= 后 200 + PDF 字节。 */
const licenseFileUrl = computed(() =>
  licenseUploadedUrl.value ||
  (form.licenseFileId
    ? withBiddingFileToken(buildBiddingFileDownloadUrl(form.licenseFileId))
    : ''),
);
/** 允许的影像类型：营业执照通常为图片或 PDF 扫描件 */
const licenseAccept = '.jpg,.jpeg,.png,.pdf';

/** 选文件后立即上传（阻止组件默认提交），成功后自动把 file_id 绑进表单 */
async function beforeLicenseUpload(file: File): Promise<boolean> {
  const name = (file?.name ?? '').toLowerCase();
  if (!/\.(jpe?g|png|pdf)$/.test(name)) {
    createMessage.warning('营业执照影像仅支持 jpg / png / pdf');
    return false;
  }
  licenseUploading.value = true;
  try {
    const up = await uploadAttachment(file, 'bidding/subject/license');
    licenseUploadedUrl.value = up.url;
    form.licenseFileName = up.name || file.name;
    /* fileId 由上传链路保证存在（缺失时 uploadAttachment 已抛错），这里只做自动绑定 */
    form.licenseFileId = String(up.fileId);
    createMessage.success('营业执照影像已上传（file_id=' + up.fileId + '）');
    /* 契约《后端变更请求-主体档案影像移除与更换-2026-09-23》§3.2（前端配合项）：上传成功后**自动落库**。
     * 原实现只绑表单变量不调 save —— 用户看到「已上传」以为已替换，实际必须再点一次「保存档案」才生效
     * （契约 §1.3 实测：上传后不保存直接离开 = 换了没反应；后端链路本身正常）。
     * 为什么先 validate()：save 是整档提交，必填项缺失会被前端校验拦下；新档案只传一张影像必然存不进去，
     * 那种情况不给「已生效」假象，改为明确告知要补必填项。 */
    const bad = validate();
    if (bad) {
      createMessage.warning('影像已上传并绑定，但档案尚未生效：' + bad + '；补齐必填项后点「保存档案」');
    } else {
      await doSave(); // 内部已有成功/失败提示，且保存成功后会自动重拉档案（含 licenseFileId/Name）
    }
  } catch (e) {
    createMessage.error('上传失败：' + ((e as Error).message || String(e)));
  } finally {
    licenseUploading.value = false;
  }
  return false;
}

/** 后端影像清除端点是否已上线（契约 B1）。第一次点「移除」实探：404 → 置 true，之后不再发必然失败的请求。 */
const licenseClearUnsupported = ref(false);

/** 移除已上传影像（契约《后端变更请求-主体档案影像移除与更换-2026-09-23》§3.1/§3.3）：
 *  · 原实现只清前端变量、不调后端 —— 用户以为删了，实际库里一字未动；且该字段靠 save 也清不掉
 *    （MyBatis-Plus 默认 NOT_NULL 策略忽略 null；空串被 numOrUndef 丢成 undefined 后 JSON.stringify 丢字段，见契约 §1.2 实测）；
 *  · 现改为调专用清除端点 DELETE /bidding/subject/profile/license（后端 B1 待实施）；
 *  · 二次确认后再执行：影像是资格审核材料，误删成本高（契约 §3.3）。 */
function clearLicense(): void {
  if (licenseClearUnsupported.value) {
    Modal.info({
      title: '影像移除暂不可用',
      content: '后端清除端点（DELETE /bidding/subject/profile/license）尚未上线（契约 B1 待实施），当前无法真正移除影像，请稍后再试。',
      okText: '知道了',
    });
    return;
  }
  Modal.confirm({
    title: '确认移除营业执照影像？',
    content: '影像是资格审核材料，移除后需重新上传；本操作将直接清除已保存的影像记录。',
    okText: '确认移除',
    okType: 'danger',
    cancelText: '取消',
    onOk: doClearLicense,
  });
}
async function doClearLicense(): Promise<void> {
  try {
    await deleteSubjectLicense();
    form.licenseFileId = '';
    form.licenseFileName = '';
    licenseUploadedUrl.value = '';
    createMessage.success('营业执照影像已移除');
  } catch (e) {
    // 真实抓到的错误形态不一：vben 请求客户端抛 Error 时 .message 可能是解析体对象（String 后变 [object Object]），
    // 也可能带 .data（含 code/msg）或 .response.status；统一做形状鲁棒提取，避免出现 '[object Object]' 式提示。
    const err = e as { data?: { code?: number; msg?: unknown }; message?: unknown; response?: { data?: { code?: number; msg?: unknown }; status?: number; }; };
    const data = err?.data ?? err?.response?.data;
    const dataMsg = typeof data?.msg === 'string' ? data.msg : '';
    const rawMsg = typeof err?.message === 'string' ? err.message : dataMsg;
    const msg = rawMsg || '未知错误（' + JSON.stringify(data ?? err?.message ?? e) + '）';
    const code = data?.code ?? err?.response?.status;
    if (code === 404 || /请求地址不存在|Not Found/i.test(msg)) {
      licenseClearUnsupported.value = true;
      createMessage.error('移除失败：后端清除端点尚未上线（契约 B1），本次未做任何变更，影像仍保留。');
    } else {
      createMessage.error('移除失败：' + msg);
    }
  }
}
/** 营业执照影像 → 待签名文件（企业资格法定材料；第24条身份验证 / 第40条资格预审文件电子签名） */
function signFileOf(): CaSignFile | undefined {
  if (!form.licenseFileId && !licenseFileUrl.value) return undefined;
  return {
    url: licenseFileUrl.value || undefined,
    fileName: form.licenseFileName || '营业执照影像',
    // fileId 为雪花 ID（超 JS Number 安全范围），原样传字符串避免精度丢失
    fileId: form.licenseFileId || undefined,
  };
}
function onLicenseSigned(rec: { digestHex?: string; signatureValue?: string; }): void {
  if (rec.signatureValue) createMessage.success('营业执照影像 CA 签名完成：' + (rec.digestHex?.slice(0, 12) ?? '') + '…');
}

onMounted(() => {
  void loadAll();
});
</script>

<template>
  <div class="bid-ind ind-page">
        <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">主体档案 · 企业自维护<span class="ind-mark">PROFILE</span></div>
        <div class="ind-sub">本企业档案唯一（契约《主体档案合并一租户一档》）：投标人 / 招标人共用同一份企业档案，角色由平台授权决定（资格 = 授权 + 档案完善）。</div>
      </div>
      <div class="ind-actions">
        <Tag color="blue">投标人</Tag>
        <Tag color="green">招标人</Tag>
        <span class="text-xs text-muted-foreground">角色共用本档案</span>
      </div>
    </div>
    <div class="ind-body">
<Spin :spinning="loading">
      <div v-if="loaded" class="mb-3">
        <!-- 资格与档案条件合并在**一张卡**里（2026-10-02 用户口径）：
             此前左右两张 Alert（「具备资格 / 暂不具备资格」+「档案信息已完善」）语义打架 ——
             读者看到「档案已完善」却「暂不具备资格」无法理解。资格 = 授权 ∧ 档案完善，
             两个条件必须并列摊开，缺失项写在各自条件行上，缺一不可。 -->
        <Alert
          :type="eligibility?.eligible ? 'success' : 'warning'"
          :show-icon="true"
          :message="eligibility ? (eligibility.eligible ? '具备投标 / 招标资格' : '暂不具备投标 / 招标资格') : '资格状态不可用'"
        >
          <template #description>
            <div class="space-y-1 text-sm">
              <div class="flex gap-2">
                <span class="shrink-0 text-muted-foreground">① 平台授权</span>
                <span v-if="authorized" class="text-emerald-600">已授权{{ authTimeText }}</span>
                <span v-else class="text-amber-600">未授权{{ authReasonText ? '（' + authReasonText + '）' : '' }} —— 由平台管理员在「资质与证书管理 · 主体授权管理」中授权</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-muted-foreground">② 企业档案</span>
                <span v-if="complete" class="text-emerald-600">已完善（全部必填字段已齐）</span>
                <span v-else class="text-amber-600">待完善（{{ profileMissingText }}）</span>
              </div>
              <div class="text-xs text-muted-foreground">资格 = ① 平台授权 ∧ ② 企业档案完善，二者缺一不可</div>
            </div>
          </template>
        </Alert>
      </div>

      <div v-if="loaded" class="form-panel">
        <div class="ind-sect" style="margin-top:0">基本档案 · 法人基本信息 <small>表89 字段口径</small></div>
        <div class="mb-3 text-sm font-medium text-muted-foreground">USCC 按 GB 32100-2015 校验；行政区划按 GB/T 2260（6 位市级）；必填：单位名称/信用代码/法定代表人/类别/区划/联系人/电话</div>
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
            <div class="form-lbl">营业执照影像</div>
            <Upload.Dragger
              class="lic-drag"
              :accept="licenseAccept"
              :before-upload="beforeLicenseUpload"
              :disabled="licenseUploading"
              :show-upload-list="false"
            >
              <p class="lic-drag-t">{{ licenseUploading ? '正在上传…' : '点击或拖拽文件到此处上传' }}</p>
              <p class="lic-drag-h">支持 jpg / png / pdf；上传成功后系统自动绑定文件标识（file_id），无需也无法手填</p>
            </Upload.Dragger>
            <div v-if="form.licenseFileId || form.licenseFileName" class="lic-info">
              <span class="lic-ok">已上传</span>
              <span class="lic-name" :title="form.licenseFileName">{{ form.licenseFileName || '（未命名）' }}</span>
              <span v-if="form.licenseFileId" class="lic-id">file_id #{{ form.licenseFileId }}</span>
              <a v-if="licenseFileUrl" :href="licenseFileUrl" target="_blank" rel="noopener noreferrer">查看</a>
              <CaSignEntry v-if="signFileOf()" :file="signFileOf()" biz-type="SUBJECT" :biz-code="form.uscc" label="CA 签名" @signed="onLicenseSigned" />
              <a class="lic-del" @click="clearLicense">移除</a>
            </div>
            <div v-else class="lic-hint">尚未上传营业执照影像（保存档案时不阻断，但资格审核通常需要）</div>
          </div>
        </div>
        <div class="mt-4 flex items-center justify-end gap-2">
          <Button @click="loadAll">重置</Button>
          <Button type="primary" :loading="saving" @click="doSave">保存档案</Button>
        </div>
      </div>

      <Card v-if="!loaded && !loading" class="mt-3">
        <div class="text-xs text-muted-foreground">档案加载失败或无数据？刷新重试；本页为“企业自维护”入口（权限 bidding:subject:query/update）。</div>
      </Card>
    </Spin>
    </div>
</div>
</template>
<style scoped>
/* 上传区（线稿风：白底细边、直角、提示字清晰）—— 配色走平台主题 token（2026-09-11） */
.lic-drag :deep(.ant-upload-drag) {
  border: 1px dashed hsl(var(--border));
  border-radius: var(--radius);
  background: hsl(var(--muted) / 40%);
  padding: 10px 8px;
}
.lic-drag :deep(.ant-upload-drag:hover) { border-color: hsl(var(--primary)); background: hsl(var(--primary) / 5%); }
.lic-drag-t { margin: 0; font-size: 13px; font-weight: 600; color: hsl(var(--foreground)); }
.lic-drag-h { margin: 4px 0 0; font-size: 11.5px; color: hsl(var(--muted-foreground)); line-height: 1.6; }
.lic-info {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  font-size: 12.5px;
  color: hsl(var(--foreground));
}
.lic-ok { color: var(--ind-ok); font-weight: 600; }
.lic-name { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lic-id { font-size: 11px; color: hsl(var(--muted-foreground)); background: hsl(var(--muted) / 50%); border: 1px solid hsl(var(--border)); border-radius: 4px; padding: 0 4px; }
.lic-del { color: hsl(var(--destructive)); cursor: pointer; }
.lic-hint { margin-top: 6px; font-size: 11.5px; color: hsl(var(--muted-foreground)); }
</style>
