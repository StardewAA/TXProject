<!-- ============================================================================
  CA 文件签章认证（CaSealAuth）—— 上传后 CA 认证/盖章工作台（契约 CaSealController + 用户裁定演示链路）。
  职责边界（2026-09-20 收敛，禁止再重合）：
    本组件 = 「给文件出盖章件」的唯一入口：认证检测 → 扫码 → **版式盖章** → 红章 PDF + SM3/SM2/证书证据链 + 落 CA 台账。
    不负责「取消息签名值」「开标解密」——那两类请用 CaSignEntry（本组件与其能力互斥，同一位置不要并排挂两个）。
  链路（2026-09-20 改：二维码改为**可见 + 手动确认**，不再盲等 2 秒自动关闭）：
    1) 打开自动调 GET /bidding/ca/seal/check（fileId + sha256 兜底）→ caAuthed=false 提示「该文件未 CA 认证」；
    2) 原文件预览（确认弹窗）；「生成签名二维码」→ POST /bidding/ca/sign/qrcode 拿**真实二维码**并展示，
       状态「待扫码」；
    3) **测试阶段没有官方 key，扫不了码** → 二维码旁边放「模拟已扫码（测试）」按钮：点击后按真实三阶段
       调 GET /bidding/ca/sign/status（第二步）→ GET /bidding/ca/sign/value（第三步）取签名值，
       状态推进「已扫码 → 已签名」；其余流程与真实扫码完全一致，只是把"扫码"这一动作换成点击；
    4) 「CA 认证（盖章）」→ POST /bidding/ca/seal/document（multipart 原文件）→ 后端本地真实 SM2 签名 +
       PDF 红章/分离式签名，直接落库 CA 签章台账（bidding_ca_seal）→ 展示红章 PDF/签名证据链。
    口径：所有 CA 认证默认**已登录**（接口 isAuthenticated()），签章一律出自**同一个测试账号**
      （本地CA测试单位 · SN-LOCAL-CA-TEST-0001）；缺官方 key 只影响"真实扫码"这一步。
  用法：
    <CaSealAuth v-model:open="open" :file="{ url, name, fileId, sha256 }" :uploaded="fileObj" :business="code" @sealed="onSealed" />
  props.uploaded 传上传回调拿到的原生 File（有它才能走 document 盖章闭环）；只有 url/fileId 时仅执行 check 检测与红章回显。
============================================================================ -->
<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { deleteAttachment, saveAttachment } from '#/api/bidding/attachment';
import { CaApi } from '#/api/bidding/ca';
import { buildBiddingFileDownloadUrl } from '#/api/bidding/file';
import { fetchFileBlob } from '#/utils/fetchFile';

import PdfPreview from '../vo/PdfPreview.vue';
import CaQrCode from './CaQrCode.vue';

const props = withDefaults(
  defineProps<{
    btnType?: 'default' | 'link' | 'primary' | 'text';
    /** 业务归属编号（businessCode，落库关联投标流程跟踪 id） */
    business?: string;
    /** 附件元信息（url/name/fileId/sha256）；check 与预览用 */
    file?: { fileId?: number | string; fileName?: string; name?: string; sha256?: string; url?: string; };
    /** 隐藏组件自带触发按钮（页面用自己的按钮/样式时用；此时由父级控制 open） */
    hideTrigger?: boolean;
    /** 挂载按钮文案 */
    label?: string;
    /** 受控打开（不传时由组件内部按钮自开；父级也可用 v-model:open 控制） */
    open?: boolean;
    /** 「用签章件替换原文件」的目标（传了才显示该按钮）：
     *  bizType+bizCode 决定绑定到哪个业务实体；attachmentId 传原附件行 id 时会把原行解绑（真正的"替换"） */
    replaceTarget?: {
      attachmentId?: number | string;
      bizCode?: string;
      bizType?: 'ANNOUNCEMENT' | 'BID_SECTION' | 'PROJECT' | 'TENDER_PROJECT';
    };
    size?: 'middle' | 'small';
    /** 上传回调的原生 File（有它才能再走 seal/document 盖章闭环） */
    uploaded?: File;
  }>(),
  { open: false, label: 'CA 认证', size: 'small', btnType: 'link', hideTrigger: false, replaceTarget: undefined, file: undefined, uploaded: undefined, business: undefined },
);

const emit = defineEmits<{
  /** 已用签章件替换原文件（回传签章件 fileId，供业务方刷新附件列表） */
  replaced: [sealFileId: number | string];
  /** 盖章完成（回传红章 document 结果，供业务方落库回显） */
  sealed: [result: CaApi.SealDocument];
  'update:open': [v: boolean];
}>();

/* ---------------- 状态 ---------------- */
const open = ref(props.open);
const checking = ref(false);
const sealing = ref(false);
const error = ref('');

const sealCheck = ref<CaApi.SealCheck | null>(null);
const sealResult = ref<CaApi.SealDocument | null>(null);
const qrVisible = ref(false);
const scanning = ref(false);

const previewOpen = ref(false);
const previewUrl = ref('');
const previewName = ref('');
/** 签章件预览（走 fileId → /bidding/file/download 内联，不用签名下载地址，否则点"预览"会变下载） */
const sealPreviewOpen = ref(false);
const replacing = ref(false);
/** 已替换为签章件（替换成功后按钮转为已替换态，避免重复绑定） */
const replacedDone = ref(false);

const fileName = computed(() => props.file?.fileName || props.file?.name || props.uploaded?.name || '文件');
const fileNameHint = computed(() => props.file?.fileName || props.file?.name || props.uploaded?.name || '');
/** 可盖章判定：有 file_id 且（有原生 File 或可回取的 URL，历史附件/公告版式无原 File 时按 URL 回取） */
const canSeal = computed(() => !!props.file?.fileId && (!!props.uploaded || !!props.file?.url));
const authText = computed(() => {
  if (sealCheck.value === null) return '正在校验文件…';
  return sealCheck.value.caAuthed ? '文件已认证，可直接盖章' : '文件尚未认证：可直接盖章，认证由平台完成';
});
const authOk = computed(() => sealCheck.value?.caAuthed === true);
const verifyTag = computed(() => {
  const s = sealCheck.value?.verifyStatus;
  if (s === 'PASS') return { color: 'green', text: '验签通过' };
  if (s === 'FAIL') return { color: 'red', text: '验签不通过' };
  return { color: 'orange', text: '未验签' };
});

/* ---------------- check ---------------- */
async function runCheck(): Promise<void> {
  const fileId = props.file?.fileId;
  const sha256 = props.file?.sha256;
  checking.value = true;
  error.value = '';
  try {
    if (fileId !== undefined && fileId !== null && fileId !== '') {
      sealCheck.value = await CaApi.checkSealAuth(fileId, sha256);
    } else {
      sealCheck.value = { caAuthed: false };
    }
  } catch (e) {
    error.value = (e as Error).message || String(e);
    sealCheck.value = { caAuthed: false };
  } finally {
    checking.value = false;
  }
}

function openWorkbench(): void {
  open.value = true;
  emit('update:open', true);
  authStep.value = 'idle';
  signQr.value = null;
  signValue.value = null;
  sealResult.value = null;
  qrVisible.value = false;
  void runCheck();
}

/** 关闭二维码面板（保留已取到的签名值，便于再次盖章） */
function closeQr(): void {
  qrVisible.value = false;
  if (authStep.value === 'waiting') authStep.value = 'idle';
}

function previewOriginal(): void {
  previewUrl.value = props.file?.url || '';
  previewName.value = fileNameHint.value;
  previewOpen.value = true;
}

/* ---------------- CA 认证（二维码 → 模拟已扫码 → seal/document 盖章） ---------------- */
/** 签章阶段：idle 未开始 / waiting 待扫码 / scanned 已扫码（已取到签名值）/ signing 盖章中 / done 完成 */
const authStep = ref<'done' | 'idle' | 'scanned' | 'signing' | 'waiting'>('idle');
/** 签名二维码会话（第一步产物；qrCodeImageBase64 可直接 <img :src>） */
const signQr = ref<CaApi.SignQrCode | null>(null);
/** 扫码后取到的签名值（第三步产物） */
const signValue = ref<CaApi.SignValue | null>(null);
/** 二维码会话的待签摘要（hex）：此处用文件 sha256 作占位（上传响应只有 sha256，缺"按 fileId 取文件 SM3"接口，
 *  见契约 §8.9）——它只作为 sign/qrcode 会话入参；盖章落库的指纹与签名不依赖它：
 *  /bidding/ca/seal/document 由后端对文件字节自行计算真实 SM3 并做 SM2 签名。 */
const digestHex = computed(() => props.file?.sha256 || 'seal-auth');
/** 统一测试账号身份（用户裁定：所有 CA 签章都出自同一个测试账号的章） */
const TEST_CA = { caOrgCode: 'TEST-CA', certSn: 'SN-LOCAL-CA-TEST-0001', orgName: '本地CA测试单位' };
/** 二维码步骤条下标（生成 → 扫码 → 取签名值 → 盖章落库） */
const qrStepIndex = computed(() => {
  switch (authStep.value) {
    case 'done': { return 4;
    }
    case 'scanned': { return 3;
    }
    case 'signing': { return 3;
    }
    case 'waiting': { return 1;
    }
    default: { return 1;
    }
  }
});
const stepText = computed(() => {
  switch (authStep.value) {
    case 'done': { return '签章完成';
    }
    case 'scanned': { return '已扫码（已取签名值）';
    }
    case 'signing': { return '盖章中…';
    }
    case 'waiting': { return '待扫码';
    }
    default: { return '未开始';
    }
  }
});
/** 取盖章原文件：优先上传回调的原生 File；历史附件/公告版式无原 File 时按 fileId/url 回取 */
async function resolveSealFile(): Promise<File | undefined> {
  if (props.uploaded) return props.uploaded;
  // 有 fileId 一律走业务受控出口 /bidding/file/download?id=（契约：取文件只走这里，需登录态 + 病毒拦截），
  // 只有 fileId 缺失的历史数据才回退到出参 url（平台文件地址）
  const fileId = props.file?.fileId;
  const url =
    fileId === undefined || fileId === null || fileId === ''
      ? props.file?.url
      : buildBiddingFileDownloadUrl(fileId);
  if (!url) return undefined;
  try {
    // 用 fetchFileBlob 而非裸 fetch：本框架失败响应是 HTTP 200 + {code,msg}，
    // 裸 res.ok 会把「账号未登录」的 JSON 当成 PDF 送去盖章（红章件直接是坏文件）
    const blob = await fetchFileBlob(url);
    return new File([blob], fileNameHint.value || '待签章文件.pdf', { type: blob.type || 'application/pdf' });
  } catch (e) {
    message.error('原文件回取失败：' + ((e as Error).message || String(e)));
    return undefined;
  }
}

/** 第一步：生成签名二维码（真实调 POST /bidding/ca/sign/qrcode，返回可展示的二维码） */
async function generateQr(): Promise<void> {
  if (!props.file?.fileId) {
    message.warning('缺少文件 file_id，无法落库关联台账');
    return;
  }
  error.value = '';
  sealResult.value = null;
  signValue.value = null;
  try {
    signQr.value = await CaApi.createSignRequest(digestHex.value);
    authStep.value = 'waiting';
    qrVisible.value = true;
  } catch (e) {
    // 二维码接口不可达不阻断盖章（seal/document 才是实际签章通道）：给一个本地占位会话，流程照走
    signQr.value = { qrCodeShowText: '本地占位会话（签章通道不受影响）', tid: 'LOCAL-' + Date.now() };
    authStep.value = 'waiting';
    qrVisible.value = true;
    message.warning('签名二维码接口不可达，已降级为本地占位会话：' + ((e as Error).message || String(e)));
  }
}

/** 第二步（测试替代「扫码」）：模拟已扫码 —— 按真实三阶段拉状态与签名值，推进到「已扫码」 */
async function simulateScan(): Promise<void> {
  const tid = signQr.value?.tid;
  if (!tid) return;
  scanning.value = true;
  try {
    const status = await CaApi.querySignStatus(tid);
    // 占位实现扫码即已签名；真实实现需轮询到 signed=true
    if (status && (status.scanned || status.signed)) {
      signValue.value = await CaApi.fetchSignatureValue(tid);
      authStep.value = 'scanned';
      message.success('已扫码：签名值已取回（测试账号 ' + TEST_CA.certSn + '）');
    } else {
      // 真链路（本地自建 CA 共享组件，bidding.ca.sdk.enabled=true）下"未扫码"就是**真的没人扫**：
      // 二维码由组件真实签发，必须用装了 CA 证书的手机 APP 扫；没有 APP 时这里永远返回未扫码。
      // 这**不影响盖章**——盖章走服务端本地真实 SM2 签名（见 sealNow），故引导用户直接用「直接盖章」。
      authStep.value = 'waiting';
      message.warning('组件返回「未扫码」：需要 CA 手机 APP 真实扫码才能取到签名值；没有 APP 请直接用「直接盖章（最佳测试）」，盖章不依赖扫码');
    }
  } catch (e) {
    message.error('拉取扫码状态失败：' + ((e as Error).message || String(e)));
  } finally {
    scanning.value = false;
  }
}

/* ============ 盖章后的真实验签（最佳测试的核心：结论必须由接口给出，不能写死） ============ */
/** 验签状态：PASS/FAIL 来自台账验签接口；UNVERIFIED = 没取到结论（附原因），**不等于通过** */
type VerifyOutcome = { reason?: string; status: 'FAIL' | 'PASS' | 'UNVERIFIED' };
const verifyOutcome = ref<null | VerifyOutcome>(null);
const verifying = ref(false);

async function verifySealedRecord(doc: CaApi.SealDocument): Promise<VerifyOutcome> {
  if (!doc.documentId) {
    return { reason: '签章响应未带 documentId，无法定位台账记录', status: 'UNVERIFIED' };
  }
  try {
    // ① 定位本次台账记录：取最新 10 条按 documentId **精确比对**
    // （不按文件名过滤：台账 documentName 存的是**原文件名**，与签章响应里的 sealFileName 不同名）
    const page = await CaApi.getSealPage({ pageNo: 1, pageSize: 10 });
    const row = (page.list ?? []).find((r) => r.documentId === doc.documentId);
    if (!row?.id) {
      return { reason: '未在台账中定位到本次记录（需 bidding:ca:query 权限）', status: 'UNVERIFIED' };
    }
    // ② 真实验签（会回写台账 verifyStatus = PASS/FAIL）
    const verified = await CaApi.verifySeal(row.id);
    return verified?.verifyStatus === 'PASS' ? { status: 'PASS' } : { status: 'FAIL' };
  } catch (e) {
    return { reason: (e as Error).message || String(e), status: 'UNVERIFIED' };
  }
}

/** 第三步：盖章（POST /bidding/ca/seal/document：真实 SM2 签名 + PDF 红章，落库台账） */
async function sealNow(): Promise<void> {
  const sealFile = await resolveSealFile();
  if (!sealFile) {
    message.warning('CA 认证需原文件：请先上传文件后重试（document 需 multipart 原文件盖章）');
    return;
  }
  sealing.value = true;
  sealResult.value = null;
  replacedDone.value = false;
  authStep.value = 'signing';
  try {
    const doc = await CaApi.sealDocument(sealFile, props.business);
    sealResult.value = doc;
    sealCheck.value = {
      caAuthed: true,
      sealId: doc.sealFileId,
      documentId: doc.documentId,
      documentName: doc.sealFileName,
      orgName: doc.orgName,
      sealTime: doc.sealTime,
      sealFileId: doc.sealFileId,
      sealFileUrl: doc.sealFileUrl,
      verifyStatus: 'UNVERIFIED',
    };
    authStep.value = 'done';
    qrVisible.value = false;
    /* 盖章 ≠ 验签通过：盖章只是"签了"，**能否验过必须由验签接口回答**。
     * （原实现在这里直接写 verifyStatus='PASS'，是把结论当已知条件——属于假数据，2026-09-21 改掉。）
     * 真实口径：台账记录 → POST /bidding/ca/seal/verify?id= → 用统一测试账号公钥对
     * 「文件 SM3 指纹 + SM2 签名值」做 GB/T 32918 验签，结果回写台账并显示在这里。 */
    verifying.value = true;
    const outcome = await verifySealedRecord(doc);
    verifying.value = false;
    verifyOutcome.value = outcome;
    sealCheck.value = { ...sealCheck.value, verifyStatus: outcome.status };
    if (outcome.status === 'PASS') {
      message.success('CA 认证完成：红章 PDF 已生成，台账验签通过（SM2 真实验签）');
    } else if (outcome.status === 'FAIL') {
      message.warning('签章已完成但台账验签未通过（FAIL）：请核对签章证据链');
    } else {
      message.success('CA 认证完成：红章 PDF 已生成并落库台账（验签状态未取到：' + (outcome.reason || '未知原因') + '）');
    }
    emit('sealed', doc);
  } catch (e) {
    authStep.value = signValue.value ? 'scanned' : 'waiting';
    message.error('CA 认证失败：' + ((e as Error).message || String(e)));
  } finally {
    sealing.value = false;
  }
}

/** 「用签章件替换原文件」：把签章件绑定到原附件所在的业务实体，并解绑原附件行。
 *  为什么不是后端一个 /replace 接口：附件域已提供「幂等绑定(save) + 解绑(delete)」，
 *  两步即可表达"替换"；先绑后解可避免中途失败导致业务实体上没有可用文件。
 *  若后续要服务端原子替换，再加 POST /bidding/attachment/replace 即可（前端仅改这一处）。 */
async function replaceWithSealed(): Promise<void> {
  const sealFileId = sealResult.value?.sealFileId ?? sealCheck.value?.sealFileId;
  const target = props.replaceTarget;
  if (!sealFileId) {
    message.warning('签章件编号缺失，无法替换');
    return;
  }
  if (!target?.bizType || !target?.bizCode) {
    message.warning('未提供替换目标（业务类型/业务编号），无法替换');
    return;
  }
  replacing.value = true;
  try {
    await saveAttachment({
      attachmentName: '签章件',
      bizCode: target.bizCode,
      bizType: target.bizType,
      fileId: sealFileId,
      fileName: sealResult.value?.sealFileName || sealCheck.value?.documentName || '签章件.pdf',
    });
    if (target.attachmentId !== undefined && target.attachmentId !== '') {
      await deleteAttachment(target.attachmentId);
    }
    replacedDone.value = true;
    message.success('已用签章件替换原文件');
    emit('replaced', sealFileId);
  } catch (e) {
    message.error(`替换失败：${(e as Error).message || String(e)}`);
  } finally {
    replacing.value = false;
  }
}

watch(
  () => props.open,
  (v) => {
    open.value = v;
    if (v) void runCheck();
  },
);
</script>

<template>
  <a-button v-if="!hideTrigger" :type="btnType" :size="size" @click.stop="openWorkbench">{{ label }}</a-button>

  <a-modal
    :open="open"
    :title="`CA 认证 · ${ fileName}`"
    :width="680"
    :footer="null"
    :mask-closable="false"
    destroy-on-close
    @update:open="(v: boolean) => { open = v; emit('update:open', v) }"
  >
    <a-alert
      v-if="error"
      class="cse-mb"
      type="error"
      show-icon
      :message="error"
    />

    <!-- 认证状态 -->
    <a-alert
      class="cse-mb"
      :type="authOk ? 'success' : 'warning'"
      show-icon
      :message="authText"
    />

    <!-- 只给用户看必要信息：摘要/会话号/证书等技术项一律收进下方「技术详情」，不占视线 -->
    <a-descriptions :column="1" size="small" bordered class="cse-mb">
      <a-descriptions-item label="文件">{{ fileNameHint || '—' }}</a-descriptions-item>
      <a-descriptions-item v-if="sealCheck?.orgName" label="签章单位">{{ sealCheck.orgName }}</a-descriptions-item>
      <a-descriptions-item v-if="sealCheck?.sealTime" label="签章时间">{{ sealCheck.sealTime }}</a-descriptions-item>
      <a-descriptions-item v-if="sealCheck?.verifyStatus" label="验签结果">
        <a-tag :color="verifyTag.color">{{ verifyTag.text }}</a-tag>
      </a-descriptions-item>
    </a-descriptions>

    <!-- 扫码是**可选**高级路径（盖章不依赖它）：默认收起，不把"要不要扫码"变成用户必须回答的问题 -->
    <a-collapse ghost class="cse-mb">
      <a-collapse-panel key="scan" header="需要用手机 CA 扫码？（可选，不扫码也能盖章）">
        <div class="cse-act cse-act-top">
          <a-button size="small" :loading="checking" :disabled="!canSeal" @click="generateQr">生成二维码</a-button>
          <span class="cse-hint">有 CA 手机 APP 时可用；没有也不影响盖章。</span>
        </div>
    <div v-if="qrVisible && signQr" class="cse-qr">
      <div class="cse-qr-left">
        <CaQrCode
          :image-base64="signQr.qrCodeImageBase64"
          :jump-url="signQr.qrCodeJumpUrl"
          :tid="signQr.tid"
          :digest="digestHex"
          :size="168"
          :caption="signQr.qrCodeShowText || '请用手机 CA APP 扫描二维码'"
        />
      </div>
      <div class="cse-qr-right">
        <a-steps direction="vertical" size="small" :current="qrStepIndex" class="cse-steps">
          <a-step title="生成二维码" description="用手机 CA APP 扫描" />
          <a-step title="手机确认" description="在 APP 内完成身份确认" />
          <a-step title="取回签名" description="完成后回到本页继续" />
          <a-step title="完成盖章" description="由平台完成加密签章" />
        </a-steps>
        <div class="cse-act">
          <a-button
            type="primary"
            :loading="scanning"
            :disabled="authStep !== 'waiting'"
            @click="simulateScan"
          >
            我已扫码，取签名值
          </a-button>
          <a-button size="small" @click="generateQr">重新生成</a-button>
          <a-button size="small" @click="closeQr">收起</a-button>
        </div>
        <div class="cse-hint">没有手机 CA APP 也不影响盖章：扫码是可选步骤。</div>
        <a
          v-if="signQr.qrCodeJumpUrl"
          :href="signQr.qrCodeJumpUrl"
          target="_blank"
          rel="noopener noreferrer"
          >打开跳转链接</a>
      </div>
    </div>
      </a-collapse-panel>
    </a-collapse>

    <!-- 操作区：用户只需做一个决定——盖章 -->
    <div class="cse-act">
      <a-button size="small" @click="previewOriginal">预览原文件</a-button>
      <a-button v-if="sealCheck?.sealFileId" size="small" @click="sealPreviewOpen = true">预览签章件</a-button>
      <a-button
        v-if="!authOk"
        type="primary"
        :loading="sealing"
        :disabled="!canSeal"
        @click="sealNow"
      >
        加盖电子章
      </a-button>
      <span v-if="!canSeal" class="cse-hint">请先选择或上传文件，然后再盖章。</span>
    </div>

    <!-- 盖章结果：用户视角只回答"成了没、怎么用" -->
    <template v-if="sealResult">
      <a-alert
        class="cse-mb"
        type="success"
        show-icon
        message="电子签章已完成"
        description="签章件已生成：可预览、下载，或直接用签章件替换原文件。"
      />
      <a-descriptions :column="1" size="small" bordered class="cse-mb">
        <a-descriptions-item label="签章件">{{ sealResult.sealFileName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="签章单位">{{ sealResult.orgName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="签章时间">{{ sealResult.sealTime || '—' }}</a-descriptions-item>
        <a-descriptions-item label="验签结果">
          <a-spin v-if="verifying" size="small" />
          <template v-else-if="verifyOutcome">
            <a-tag :color="verifyOutcome.status === 'PASS' ? 'green' : (verifyOutcome.status === 'FAIL' ? 'red' : 'orange')">
              {{ verifyOutcome.status === 'PASS' ? '验签通过' : (verifyOutcome.status === 'FAIL' ? '验签未通过' : '待验签') }}
            </a-tag>
          </template>
          <span v-else class="cse-hint">待验签</span>
        </a-descriptions-item>
      </a-descriptions>
      <div class="cse-act">
        <!-- 预览一律走 fileId → /bidding/file/download（内联出口）；签名下载地址只用于 <a download>。
             把下载型地址塞进预览，点「预览」会直接变下载 —— 本次一并改掉。 -->
        <a-button v-if="sealResult.sealFileId" size="small" @click="sealPreviewOpen = true">预览签章件</a-button>
        <a-button v-if="sealResult.sealFileUrl" size="small" :href="sealResult.sealFileUrl" download>下载签章件</a-button>
        <a-button
          v-if="props.replaceTarget?.bizCode"
          size="small"
          type="primary"
          :loading="replacing"
          :disabled="replacedDone || !sealResult.sealFileId"
          @click="replaceWithSealed"
        >
          {{ replacedDone ? '已用签章件替换' : '用签章件替换原文件' }}
        </a-button>
      </div>
    </template>

    <!-- 技术详情：摘要/签名/证书/会话等只给排查用，默认收起，不占用户视线 -->
    <a-collapse ghost>
      <a-collapse-panel key="tech" header="技术详情（排查用）">
        <a-descriptions :column="1" size="small" bordered class="cse-mb">
          <a-descriptions-item v-if="props.file?.sha256" label="原文件 SHA-256">
            <code class="cse-code">{{ props.file.sha256 }}</code>
          </a-descriptions-item>
          <a-descriptions-item v-if="sealCheck?.documentId" label="签章会话">{{ sealCheck.documentId }}</a-descriptions-item>
          <a-descriptions-item v-if="sealResult" label="SM3 指纹"><code class="cse-code">{{ sealResult.hash || '—' }}</code></a-descriptions-item>
          <a-descriptions-item v-if="sealResult?.signature" label="SM2 签名"><code class="cse-code">{{ sealResult.signature }}</code></a-descriptions-item>
          <a-descriptions-item v-if="signValue" label="签名值"><code class="cse-code">{{ signValue.signatureValue || '—' }}</code></a-descriptions-item>
          <a-descriptions-item label="CA 证书">{{ sealResult?.certSn || signValue?.signatureCertSn || TEST_CA.certSn }}</a-descriptions-item>
          <a-descriptions-item label="当前阶段">{{ stepText }}</a-descriptions-item>
          <a-descriptions-item v-if="verifyOutcome?.reason" label="验签说明">{{ verifyOutcome.reason }}</a-descriptions-item>
        </a-descriptions>
      </a-collapse-panel>
    </a-collapse>
  </a-modal>

  <!-- 原文件预览 -->
  <PdfPreview v-model:open="previewOpen" :url="previewUrl" :file-name="previewName" title="原文件预览" />
  <!-- 签章件预览：传 fileId 走内联出口（内联预览），不要传签名下载地址 -->
  <PdfPreview
    v-model:open="sealPreviewOpen"
    :file-id="sealResult?.sealFileId ?? sealCheck?.sealFileId"
    :file-name="sealResult?.sealFileName || '签章件.pdf'"
    title="签章件预览"
  />
</template>

<style scoped>
.cse-mb { margin-bottom: 12px; }
.cse-act { display: flex; align-items: center; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.cse-act-top { margin-top: 0; }
.cse-hint { font-size: 12px; color: #8a949e; line-height: 1.6; }
.cse-code { word-break: break-all; background: #f5f7fa; padding: 2px 6px; border-radius: 4px; font-size: 12px; }
.cse-qr { display: flex; gap: 18px; padding: 12px; margin-bottom: 12px; border: 1px solid #eef1f5; border-radius: 8px; background: #fafbfc; }
.cse-qr-left { flex: 0 0 auto; display: flex; flex-direction: column; align-items: center; gap: 8px; }
/* 二维码图/占位/会话号样式已随 CaQrCode 组件抽出，此处不再保留（2026-09-20 收敛） */
.cse-qr-right { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.cse-steps { margin-bottom: 4px; }
</style>
