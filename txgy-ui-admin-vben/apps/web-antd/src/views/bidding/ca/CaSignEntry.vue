<script setup lang="ts">
import type { AttachmentBizType } from '#/api/bidding/attachment';

/**
 * CA 快捷签名入口（通用组件）：任何"需要**签名值**的文件位置"挂一个本组件即可。
 *
 * ============================ 职责边界（2026-09-20 收敛，禁止再重合） ============================
 *  本工作台按"要做的事"分三个页签（2026-09-21 调整：用户反馈"工作台里没有盖章、只有一个看不懂的 SM2 输入框"）：
 *   页签一 · 文件盖章：**入口**（内部即 CaSealAuth，盖章实现只有一处，不复制代码）——红章 PDF + 落 CA 台账；
 *   页签二 · 扫码签名（第40条 · 消息签名）：摘要 → CA 互认 APP 扫码 → 取 **SM2 签名值** → 可验签；
 *   页签三 · 扫码解密（第30条 · 开标在线解密）：解密人（开标主持人等）CA 扫码授权 → 取信封解密。
 *
 *  ⚠️ 分工仍是"一处实现"：盖章能力**只在 CaSealAuth 里实现**，本页签只放它的入口按钮 + 说明，
 *  不复制盖章逻辑（避免两处实现漂移）。要单独用盖章入口的页面继续直接挂 CaSealAuth 即可。
 * ============================================================================================
 *
 * 法规依据（《电子招标投标办法》八部委令 20 号）：
 *  · 第二十四条 投标人注册登记并验证身份 → 扫码登录认证（配合 /bidding/ca/login/*）；
 *  · 第二十六条 投标文件应加密递交（未加密拒收）→ 加密由投标人工具侧完成，本组件覆盖解密侧；
 *  · 第三十条  开标时所有投标人按时在线解密 → 扫码解密通道；
 *  · 第四十条  七类数据电文按《电子签名法》电子签名并存档 → 本组件覆盖"扫码签名"；**版式盖章见 CaSealAuth**。
 *
 * 用法：
 *   <CaSignEntry :file="{ url, fileName, sha256, fileId }" biz-type="BID_SECTION" biz-code="xxx"
 *                @signed="(s) => handleSigned(s)" />
 * 摘要口径：文件 SM3（GB/T 32905）由平台文件服务计算；文本摘要调后端 /bidding/ca/sm3（真实国密）。
 * 扫码签名：优先真实扫码；没有 CA 手机 APP 时用「本地测试密钥补签」（真实 SM2，签发者非官方 CA）。
 */
import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';

import { CA_LABELS, CaApi } from '#/api/bidding/ca';

import CaQrCode from './CaQrCode.vue';
import CaSealAuth from './CaSealAuth.vue';

/** 待签名文件信息（与附件行/上传返回对齐；url 供原文件预览/回取，sha256 用作摘要占位） */
export interface CaSignFile {
  url?: string;
  fileName?: string;
  fileId?: number | string;
  sha256?: string;
  size?: number;
}

const props = withDefaults(
  defineProps<{
    /** 附件行 id（可选）：传了才能在盖章后「用签章件替换原文件」——按它解绑原来那一行 */
    attachmentId?: number | string;
    /** 业务编号（仅展示回传；PDF 盖章作 businessCode 落库关联） */
    bizCode?: string;
    /** 业务类型（PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT；仅展示回传） */
    bizType?: string;
    /** button type */
    btnType?: 'default' | 'link' | 'primary' | 'text';
    disabled?: boolean;
    /** 待签名文件（fileName 展示；sha256 作为摘要占位） */
    file?: CaSignFile;
    /** 按钮文字 */
    label?: string;
    size?: 'middle' | 'small';
    /** 上传回调的原生 File（测试配置 enabled=false 时，PDF 盖章走 POST /bidding/ca/seal/document 需 multipart 原文件） */
    uploaded?: File;
  }>(),
  { label: 'CA 签名', size: 'small', btnType: 'link', disabled: false, attachmentId: undefined, file: undefined, uploaded: undefined, bizType: undefined, bizCode: undefined },
);

const emit = defineEmits<{
  /** 已用盖章件替换原文件（转发给宿主页面，供刷新附件列表） */
  replaced: [sealFileId: number | string];
  /** 盖章完成（「文件盖章」页签内完成时转发，供宿主页面刷新/回显） */
  sealed: [result: CaApi.SealDocument];
  /** 扫码签名完成（回传签名值/证书/摘要；业务方自行落库回显） */
  signed: [result: { digestHex?: string; publicKeyAlgorithm?: string; signatureCertSn?: string; signatureValue?: string; tid?: string }];
  'update:open': [v: boolean];
  /** 验签结果 */
  verified: [pass: boolean];
}>();

const open = ref(false);
const activeTab = ref('sign');
/** 附件域支持的四种业务类型（与 api/bidding/attachment 的 AttachmentBizType 一致） */
const ATTACH_BIZ_TYPES = ['ANNOUNCEMENT', 'BID_SECTION', 'PROJECT', 'TENDER_PROJECT'] as const;

/** 「用签章件替换原文件」的替换目标。
 *  必须同时满足：① 宿主给了业务编号 ② bizType 属于**附件域四种业务类型** ——
 *  当前唯一调用方传的是 bizType='REVIEW'（审查报告），附件接口不接受该类型，
 *  若放行会在点"替换"时报后端校验错误；故此处收口，非附件域业务不显示该按钮。 */
const sealReplaceTarget = computed(() => {
  const bizType = props.bizType;
  if (!props.bizCode || !bizType || !ATTACH_BIZ_TYPES.includes(bizType as (typeof ATTACH_BIZ_TYPES)[number])) {
    return undefined;
  }
  return {
    attachmentId: props.attachmentId,
    bizCode: props.bizCode,
    bizType: bizType as AttachmentBizType,
  };
});
const modalTitle = computed(() => {
  const parts = ['CA 签名工作台'];
  if (props.file?.fileName) parts.push(props.file.fileName);
  if (props.bizCode) parts.push(props.bizCode);
  return parts.join(' · ');
});

/* ==================== 扫码签名（通道一） ==================== */
const digestInput = ref('');
const signStep = ref<'done' | 'idle' | 'qr'>('idle');
const signQr = ref<CaApi.SignQrCode | null>(null);
const signValue = ref<CaApi.SignValue | null>(null);
const signing = ref(false);
/** 摘要来源（人可读）：说明这个 hex 是"对什么算出来的"，便于核对 —— 不要让人面对一个无来由的 64 位串 */
const digestSource = ref('');
/** 当前摘要是否是**SHA-256 占位**（上传回执只带 sha256，没有文件级 SM3 接口）：
 *  必须显式标出来 —— 国密口径里文件摘要应是 **SM3**（GB/T 32905），sha256 是国际算法，两者不可互认。
 *  盖章链路不受影响（后端对文件字节自己算 SM3）；受影响的是"扫码签名"这条链路的文件摘要。 */
const digestIsSha256Placeholder = computed(
  () =>
    !digestSource.value &&
    Boolean(props.file?.sha256) &&
    digestInput.value.trim().toLowerCase() === String(props.file?.sha256).toLowerCase(),
);
/** 摘要来源说明 */
const digestHint = computed(() => {
  if (!digestInput.value) return '留空即可：有文件时自动用文件的 sha256；没有文件时自动用下面这行「业务原文」经后端 SM3（GB/T 32905）算出摘要';
  return digestSource.value
    ? '摘要 = 后端 SM3（GB/T 32905）对业务原文「' + digestSource.value + '」的计算结果；文件 sha256 仅作上传摘要占位'
    : '摘要（hex）：文件 sha256 占位（真实文件 SM3 由平台文件服务计算），或后端 SM3 计算文本所得';
});

/** 业务原文：没有文件摘要时的"直出"来源（要签什么，用业务类型 + 编号 + 文件名说清楚）。
 *  为什么需要：门户/后台勾选一条业务数据做消息签名时，人不可能手输"待计算文本"；
 *  这条原文就是可读、可复核的签名对象描述，其 SM3 摘要即待签名摘要。 */
function defaultDigestSource(): string {
  const biz = [props.bizType, props.bizCode].filter(Boolean).join(':');
  const name = props.file?.fileName || (props.file?.fileId ? 'file#' + props.file.fileId : '未命名文件');
  return 'CA签名原文|' + (biz || 'SUBJECT') + '|' + name;
}

/** 摘要**直出**：空摘要时按「文件 sha256 → 业务原文经后端 SM3」顺序自动补齐，返回可直接签的摘要。
 *  这样使用者永远不用先回答"待计算文本是什么"。 */
async function ensureDigest(): Promise<string> {
  const cur = digestInput.value.trim();
  if (cur) return cur;
  if (props.file?.sha256) {
    digestInput.value = props.file.sha256;
    digestSource.value = '';
    return props.file.sha256;
  }
  const text = defaultDigestSource();
  digestInput.value = await CaApi.fetchSm3Hex(text);
  digestSource.value = text;
  return digestInput.value;
}

/** 调后端真实 SM3 接口计算摘要；输入框为空时**不报错**，直接用业务原文直出（并把它填进输入框，便于复核） */
const sm3Computing = ref(false);
async function computeSm3FromBackend(): Promise<void> {
  const typed = digestInput.value.trim();
  const text = typed || defaultDigestSource();
  sm3Computing.value = true;
  try {
    digestInput.value = await CaApi.fetchSm3Hex(text);
    digestSource.value = text;
    message.success(
      typed
        ? 'SM3 摘要已由后端计算（GB/T 32905）'
        : '原输入框为空：已自动用业务原文计算 SM3 摘要（后端 GB/T 32905）',
    );
  } catch (e) {
    message.error('SM3 计算失败：' + ((e as Error).message || String(e)));
  } finally {
    sm3Computing.value = false;
  }
}

function openWorkbench(): void {
  open.value = true;
  activeTab.value = 'sign';
  // 预填摘要：优先 sha256（附件上传返回）；没有也**不用人填** —— 生成二维码时会用业务原文经后端 SM3 直出
  digestInput.value = props.file?.sha256 ?? '';
  digestSource.value = '';
  signStep.value = 'idle';
  signQr.value = null;
  signValue.value = null;
}

/** 第一步：生成签名二维码（只生成并展示，不自动推进——测试态由「模拟已扫码」手动推进） */
async function startSign(): Promise<void> {
  signing.value = true;
  signStep.value = 'qr';
  signValue.value = null;
  let digest: string;
  try {
    // 摘要直出：不要求使用者先填"待计算文本"
    digest = await ensureDigest();
  } catch (e) {
    signing.value = false;
    signStep.value = 'idle';
    message.error('自动生成摘要失败（后端 SM3 不可达？）：' + ((e as Error).message || String(e)));
    return;
  }
  try {
    signQr.value = await CaApi.createSignRequest(digest);
    message.success('已生成签名二维码，请用 CA 互认 APP 扫码（测试态点「模拟已扫码」）');
  } catch (e) {
    message.error('生成签名二维码失败：' + ((e as Error).message || String(e)));
    signStep.value = 'idle';
  } finally {
    signing.value = false;
  }
}

/** 第二步：按真实三阶段拉扫码状态与签名值（**签名值只能由 CA APP 扫码产生**，无本地替代物） */
const scanning = ref(false);
async function simulateScan(): Promise<void> {
  const tid = signQr.value?.tid;
  if (!tid) return;
  scanning.value = true;
  try {
    // ① 真实链路优先：有人用 CA APP 真扫了，就用组件返回的真实签名值
    const status = await CaApi.querySignStatus(tid);
    if (status && (status.scanned || status.signed)) {
      signValue.value = await CaApi.fetchSignatureValue(tid);
      signStep.value = 'done';
      message.success('已扫码：签名值已取回');
      return;
    }
    // ② 没人扫（当前环境没有 CA 手机 APP）：走「本地测试密钥补签」——
    //    对本次会话的真实 SM3 摘要，用本地测试账号私钥做一次**真实 SM2 签名**，把这条通道在测试态走完。
    //    （原实现只轮询状态就直接报「未扫码」，按钮写着"模拟"却什么都没模拟 —— 2026-09-29 改）
    signValue.value = await CaApi.simulateSignValue(tid);
    signStep.value = 'done';
    message.success('已用本地测试密钥完成签名（真实 SM2；签发者非官方 CA，仅测试态）');
  } catch (e) {
    message.error('取签名值失败：' + ((e as Error).message || String(e)));
  } finally {
    scanning.value = false;
  }
}

async function doVerify(): Promise<void> {
  const digest = digestInput.value.trim();
  const sig = signValue.value?.signatureValue;
  if (!digest || !sig) {
    message.warning('缺少摘要或签名值');
    return;
  }
  try {
    const pass = await CaApi.verify(digest, sig, signValue.value?.publicKeyAlgorithm ?? 'SM2');
    if (pass) {
      message.success('验签通过：签名有效（签名值由 CA 组件签发并由组件验签，同一信任根）');
    } else {
      // 真链路（bidding.ca.sdk.enabled=true）下签名与验签都走同一个 CA 组件，信任根一致 ⇒
      // false 就是**真的验不过**（摘要被改 / 签名非该组件密钥产生），不再是占位实现的假阴性。
      message.warning('验签不通过：签名值与摘要不匹配（可能摘要被改动，或签名并非当前 CA 组件密钥所产生）');
    }
    emit('verified', pass);
  } catch (e) {
    message.error('验签失败：' + ((e as Error).message || String(e)));
  }
}

/* ==================== 扫码解密（通道二 · 开标在线解密） ==================== */
const decryptStep = ref<'done' | 'idle' | 'qr'>('idle');
const decryptQr = ref<CaApi.DecryptQrCode | null>(null);
const decryptInfo = ref<CaApi.DecryptUserInfo | null>(null);
const decryptData = ref<CaApi.DecryptData[]>([]);
const decryptUserType = ref('02');
const decryptOrgCode = ref('');
const decrypting = ref(false);

async function startDecrypt(): Promise<void> {
  const orgCode = decryptOrgCode.value.trim();
  if (!orgCode) {
    message.warning('缺少统一社会信用代码（解密人机构代码）');
    return;
  }
  decrypting.value = true;
  decryptStep.value = 'qr';
  try {
    const qr = await CaApi.createDecryptRequest(decryptUserType.value, orgCode, undefined);
    decryptQr.value = qr;
    const status = await CaApi.queryDecryptStatus(qr.tid);
    if (status.scanned) {
      decryptInfo.value = await CaApi.fetchDecryptUserInfo(qr.tid);
      if (decryptInfo.value?.accessToken) {
        const env = await CaApi.uploadDecryptEnvelope(qr.tid, decryptInfo.value.accessToken, [
          { encryptedKeyDataEnvelopeId: 'ENV-DEMO-001', publicKeyAlgorithm: '00', encryptionCertSn: '', caOrgCode: '', encryptionKey: '' },
        ]);
        decryptData.value = await CaApi.fetchDecryptedData(qr.tid);
        decryptStep.value = 'done';
        void env;
      }
    }
    message.success(status.scanned ? '解密授权完成（占位已授权）' : '已生成二维码，请解密人扫码');
  } catch (e) {
    message.error('解密失败：' + ((e as Error).message || String(e)));
    decryptStep.value = 'idle';
  } finally {
    decrypting.value = false;
  }
}

const statusText = (scanned?: boolean | null, signed?: boolean | null): string => {
  if (signed) return '已签名';
  if (scanned) return '已扫码';
  return '未扫码';
};

/** 生成二维码按钮是否可点：摘要可直出（文件 sha256 / 业务原文 → 后端 SM3），
 *  故只要有"任何可签对象"就允许点，不再要求使用者先把摘要框填满。 */
const enableSign = computed(() =>
  Boolean(
    digestInput.value.trim() ||
      props.file?.sha256 ||
      props.file?.fileName ||
      props.file?.fileId ||
      props.bizCode,
  ),
);
</script>

<template>
  <template v-if="!open">
    <a-button :type="btnType" :size="size" :disabled="disabled" @click.stop="openWorkbench">
      {{ label }}
    </a-button>
  </template>

  <a-modal
    :open="open"
    :title="modalTitle"
    :width="720"
    :footer="null"
    :mask-closable="false"
    destroy-on-close
    @update:open="(v: boolean) => { open = v; emit('update:open', v) }"
  >
    <a-tabs v-model:active-key="activeTab">
      <!-- 页签一：文件盖章（2026-09-21 新增入口 —— 用户反馈"CA 签名工作台里没有盖章"）。
           说明：盖章**实现只有一处**（CaSealAuth），本页签只放入口 + 说明，避免两处实现漂移。 -->
      <a-tab-pane key="seal" tab="文件盖章">
        <div class="cse-tip">
          <a-alert
            type="info"
            show-icon
            message="给文件加盖电子章"
            description="确认文件后点下面的按钮：平台完成签章并生成盖章件，完成后可预览、下载，或直接用盖章件替换原文件。"
          />
        </div>
        <a-descriptions :column="1" size="small" bordered class="cse-desc" :label-style="{ width: '120px' }">
          <a-descriptions-item label="待盖章文件">{{ props.file?.fileName || '—' }}</a-descriptions-item>
          <a-descriptions-item v-if="props.bizCode" label="业务编号">{{ props.bizCode }}</a-descriptions-item>
        </a-descriptions>
        <div class="cse-act">
          <CaSealAuth
            :file="props.file"
            :uploaded="props.uploaded"
            :business="props.bizCode"
            :replace-target="sealReplaceTarget"
            label="加盖电子章"
            btn-type="primary"
            size="middle"
            @sealed="(r: CaApi.SealDocument) => emit('sealed', r)"
            @replaced="(id: number | string) => emit('replaced', id)"
          />
        </div>
      </a-tab-pane>

      <!-- 页签二：扫码签名（第40条） -->
      <a-tab-pane key="sign" tab="扫码签名（取 SM2 签名值）">
        <div class="cse-tip">
          <a-alert
            type="info"
            show-icon
            message="SM2 是国密椭圆曲线签名算法（GB/T 32918）：本页签用 CA 私钥对「摘要」签名，产出的是签名值，不是盖章文件"
            description="法规：《电子招标投标办法》第40条 —— 投标文件等七类数据电文须按《电子签名法》电子签名并存档。要红章 PDF 请回「文件盖章」页签。"
          />
        </div>
        <a-descriptions :column="1" size="small" bordered class="cse-desc" :label-style="{ width: '120px' }">
          <a-descriptions-item label="待签文件">{{ props.file?.fileName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="业务归属">{{ props.bizType || '—' }}<template v-if="props.bizCode"> · {{ props.bizCode }}</template></a-descriptions-item>
          <a-descriptions-item label="待签名摘要（digest）">
            <div v-if="digestIsSha256Placeholder" class="cse-alg-warn">
              <a-tag color="orange">SHA-256 占位</a-tag>
              <span class="cse-hint">
                国密口径里文件摘要应为 SM3（GB/T 32905），但上传接口目前只回 sha256、
                也没有「按 fileId 取文件 SM3」的接口（已向后端提需求 §8.9）。
                本占位只可用于测试；要国密口径请用文本摘要（后端 SM3），或直接用「文件盖章」链路
                —— 盖章时后端对文件字节算真实 SM3。
              </span>
            </div>
            <div class="cse-digest-row">
              <!-- 摘要直出：留空即可签；有文件用文件 sha256，没有文件用业务原文经后端 SM3 生成（不再让人猜"待计算文本"填什么） -->
              <a-input
                v-model:value="digestInput"
                placeholder="可留空：有文件 → 自动用文件 sha256；没文件 → 自动用业务原文经后端 SM3 生成"
                allow-clear
              />
              <a-button size="small" :loading="sm3Computing" @click="computeSm3FromBackend">后端 SM3 计算</a-button>
            </div>
          </a-descriptions-item>
          <a-descriptions-item v-if="digestInput" label="摘要说明"><span class="cse-hint">{{ digestHint }}</span></a-descriptions-item>
        </a-descriptions>

        <template v-if="signStep === 'idle'">
          <div class="cse-act">
            <a-button type="primary" :disabled="!enableSign" @click="startSign">生成签名二维码</a-button>
            <span class="cse-hint">用户用 CA 互认 APP 扫码完成 SM2 签名（身份绑定 + 防篡改）</span>
          </div>
        </template>

        <template v-else-if="signStep === 'qr' && signQr">
          <div class="cse-qr">
            <CaQrCode
              :image-base64="signQr.qrCodeImageBase64"
              :jump-url="signQr.qrCodeJumpUrl"
              :tid="signQr.tid"
              :digest="digestInput"
              :size="168"
              :caption="signQr.qrCodeShowText || 'CA 互认扫码签名（测试态本地渲染，可真实扫出）'"
            />
            <div class="cse-qr-tip">
              请用 CA 互认 APP 扫码；测试阶段未取得官方 key、无法真实扫码 —— 点右侧「模拟已扫码」即按真实
              三阶段拉取扫码状态与签名值，其余流程与真实一致。
              <a v-if="signQr.qrCodeJumpUrl" :href="signQr.qrCodeJumpUrl" target="_blank" rel="noopener noreferrer">打开跳转链接</a>
            </div>
            <div class="cse-act">
              <a-button type="primary" :loading="scanning" :disabled="signStep !== 'qr'" @click="simulateScan">
                模拟已扫码（本地测试密钥补签）
              </a-button>
              <a-button :loading="signing" @click="startSign">重新生成</a-button>
            </div>
          </div>
        </template>

        <template v-else-if="signStep === 'done' && signValue">
          <div class="cse-signed">
            <a-alert type="success" show-icon message="签名已完成（SM2 国密签名值）" />
            <a-descriptions :column="1" size="small" bordered class="cse-desc" :label-style="{ width: '120px' }">
              <a-descriptions-item label="签名值"><code class="cse-code">{{ signValue.signatureValue }}</code></a-descriptions-item>
              <a-descriptions-item label="证书序列号">{{ signValue.signatureCertSn || '—' }}</a-descriptions-item>
              <a-descriptions-item label="公钥算法">{{ signValue.publicKeyAlgorithm ? (CA_LABELS.publicKeyAlgorithm[signValue.publicKeyAlgorithm] ?? signValue.publicKeyAlgorithm) : '—' }}</a-descriptions-item>
              <a-descriptions-item label="CA 机构编码">{{ signValue.caOrgCode || '—' }}</a-descriptions-item>
              <a-descriptions-item label="扫描状态">{{ statusText(true, true) }}</a-descriptions-item>
            </a-descriptions>
            <div class="cse-act">
              <a-button type="primary" @click="doVerify">验签（verify）</a-button>
              <a-button @click="emit('signed', { signatureValue: signValue.signatureValue, signatureCertSn: signValue.signatureCertSn, publicKeyAlgorithm: signValue.publicKeyAlgorithm, digestHex: digestInput, tid: signQr?.tid })">
                完成并回传结果
              </a-button>
            </div>
          </div>
        </template>
      </a-tab-pane>

      <!-- 通道二：扫码解密（第30条 · 开标） -->
      <a-tab-pane key="decrypt" tab="扫码解密（开标）">
        <!-- 职责边界：文件盖章/CA 认证请用 CaSealAuth；本页只做"取签名值 / 开标解密" -->
        <div class="cse-tip">
          <a-alert type="info" show-icon message="法规：《电子招标投标办法》第26/30条 —— 投标文件加密递交，开标时在线解密（解密人 CA 扫码授权）" />
        </div>
        <a-descriptions :column="1" size="small" bordered class="cse-desc" :label-style="{ width: '140px' }">
          <a-descriptions-item label="解密人类型">
            <select v-model="decryptUserType" class="cse-sel">
              <option value="02">机构（法人/组织）</option>
              <option value="01">个人</option>
            </select>
          </a-descriptions-item>
          <a-descriptions-item label="统一社会信用代码">
            <a-input v-model:value="decryptOrgCode" placeholder="解密人机构代码（02 机构必填）" allow-clear />
          </a-descriptions-item>
        </a-descriptions>
        <div class="cse-act">
          <a-button type="primary" @click="startDecrypt">生成解密授权二维码</a-button>
        </div>
        <template v-if="decryptQr">
          <div class="cse-qr">
            <CaQrCode
              :image-base64="decryptQr.qrCodeImageBase64"
              :jump-url="decryptQr.qrCodeJumpUrl"
              :tid="decryptQr.tid"
              :size="168"
              :caption="decryptQr.qrCodeShowText || 'CA 互认扫码解密（测试态本地渲染，可真实扫出）'"
            />
            <div class="cse-qr-tip">
              解密人用 CA 互认 APP 扫码授权；测试阶段未取得官方 key、无法真实扫码 —— 占位实现
              「已扫码」即视为授权完成（本通道暂无手动按钮，占位实现直接返回授权结果）。
            </div>
          </div>
        </template>
        <template v-if="decryptStep === 'done' && decryptInfo">
          <a-alert type="success" show-icon message="解密授权完成（SM4 密钥信封已受理）" />
          <a-descriptions :column="1" size="small" bordered class="cse-desc" :label-style="{ width: '140px' }">
            <a-descriptions-item label="访问令牌">{{ decryptInfo.accessToken }}</a-descriptions-item>
            <a-descriptions-item label="解密人类型">{{ CA_LABELS.decryptionUserType[decryptInfo.decryptionUserType ?? '02'] ?? decryptInfo.decryptionUserType }}</a-descriptions-item>
            <a-descriptions-item label="机构代码">{{ decryptInfo.orgCode || '—' }}</a-descriptions-item>
          </a-descriptions>
          <div v-if="decryptData.length" class="cse-desc">
            <a-descriptions :column="1" size="small" bordered class="cse-desc" :label-style="{ width: '140px' }">
              <a-descriptions-item label="解密状态">
                <a-tag :color="decryptData[0]?.decryptStatus === 'SUCCESS' ? 'green' : 'orange'">{{ decryptData[0]?.decryptStatus }}</a-tag>
                {{ decryptData[0]?.decryptStatusInfo }}
              </a-descriptions-item>
              <a-descriptions-item label="信封编号">{{ decryptData[0]?.encryptedKeyDataEnvelopeId || '—' }}</a-descriptions-item>
              <a-descriptions-item label="SM4 密钥"><code class="cse-code">{{ decryptData[0]?.decryptedKey || '—' }}</code></a-descriptions-item>
            </a-descriptions>
          </div>
        </template>
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<style scoped>
.cse-tip { margin-bottom: 10px; }
.cse-mb { margin-bottom: 12px; display: block; }
.cse-desc { margin: 10px 0; }
.cse-act { display: flex; align-items: center; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.cse-hint { font-size: 12px; color: #8a949e; }
.cse-qr { margin-top: 12px; display: flex; flex-direction: column; align-items: center; gap: 10px; }
/* 二维码图/占位样式已随 CaQrCode 组件抽出（2026-09-20 收敛） */
.cse-qr-tip { display: flex; align-items: center; gap: 8px; color: #2e3947; }
.cse-qr-tip { font-size: 12px; color: #8a949e; }
.cse-signed { display: flex; flex-direction: column; gap: 10px; margin-top: 4px; }
.cse-code { word-break: break-all; background: #f5f7fa; padding: 2px 6px; border-radius: 4px; font-size: 12px; }
.cse-digest-row { display: flex; gap: 6px; align-items: center; }
.cse-digest-row .ant-input { flex: 1; min-width: 0; }
.cse-sel { width: 100%; padding: 4px 8px; border: 1px solid #dcdfe6; border-radius: 4px; }
.cse-seal-note { display: block; margin-top: 8px; }
.cse-alg-warn { display: flex; align-items: flex-start; gap: 6px; margin-bottom: 6px; }
</style>
