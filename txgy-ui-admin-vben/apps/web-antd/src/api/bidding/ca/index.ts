import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/**
 * 契约来源：txgy-module-bidding CaController（controller/admin/ca/CaController.java）+ CaClientFacade + CaSealController。
 *  CA 互认（外部 CA 互认体系，扫码形态）：本文件封装 /bidding/ca/* 全部 15 个接口。
 *
 * 法规依据（《电子招标投标办法》八部委令 20 号）：
 *  · 第二十四条 投标人注册登记并验证身份 → 扫码登录认证（login 三阶段）；
 *  · 第二十六条 投标文件应当加密递交（未加密拒收）→ 加密/解密能力（decrypt 三阶段）；
 *  · 第二十七条 投标截止前任何单位和个人不得解密、提取投标文件 → 加密保密封存；
 *  · 第三十条 开标时所有投标人按时在线解密 → 扫码解密（decrypt 三阶段）；
 *  · 第三十一条 投标人原因未解密视为撤销 → 解密状态判定；
 *  · 第四十条 七类数据电文应当按《电子签名法》电子签名并存档（公告/招标文件/投标文件/评标报告/通知书/合同等）
 *    → 扫码签名（sign 三阶段）+ SM3 摘要（sm3）。
 * 技术口径（09-接口互认文档 15 号映射）：
 *  · 国家统一路径 = 中国招标投标公共服务平台「移动数字证书(CA)互认」体系，交易系统只对接本地部署的
 *    CA 互认共享组件（CAShare/Components），经全国互联节点点对点路由到具体 CA 机构/APP；
 *  · 签名/解密均为扫码异步三阶段（生成二维码 → 轮询扫码状态 → 取结果）；
 *  · 文件指纹 = SM3（GB/T 32905，用户裁定 document_hash = SM3）；签名 = SM2（国密）。
 *
 * 占位实现（bidding.ca.sdk.enabled=false 缺省）：CaClientFacadeDefault 返回 MOCK 值（TID-MOCK-* / SIG:*），
 *  前端链路可全通；共享组件部署 + 自助开通后配 enabled=true 切真实 SDK，本文件无需改动。
 */

export namespace CaApi {
  // ==================== 通用 ====================

  /** 扫码会话公共字段（第一步返回值） */
  export interface QrCodeBase {
    /** 会话 id（后续轮询/取值必须携带） */
    tid: string;
    /** 二维码图片（base64，可直接 <img :src>） */
    qrCodeImageBase64?: string;
    /** 二维码跳转链接 */
    qrCodeJumpUrl?: string;
    /** 二维码展示文字 */
    qrCodeShowText?: string;
  }

  // ==================== 身份认证 ====================

  /** 身份认证（进开标房间/登录复用）：入参证书号 + 统一社会信用代码，返回是否通过 */
  export function authenticate(caCertNo: string, subjectCode: string) {
    return requestClient.post<boolean>('/bidding/ca/authenticate', null, {
      params: { caCertNo, subjectCode },
    });
  }

  // ==================== 扫码登录认证（三阶段） ====================

  /** 登录二维码（第一步） */
  export interface LoginQrCode extends QrCodeBase {}

  /** 登录扫码状态（第二步） */
  export interface LoginStatus {
    /** 已扫码 */
    scanned: boolean;
    /** 状态码（01已扫 00未扫） */
    status: string;
    message?: string;
  }

  /** CA 认证信息（第三步，进房身份核对依据） */
  export interface AuthInfo {
    tid?: string;
    /** 组织名称 */
    orgName?: string;
    /** 统一社会信用代码 */
    orgCode?: string;
    personalName?: string;
    /** 证书序列号 */
    signatureCertSn?: string;
    signatureCert?: string;
    /** 公钥算法（SM2/RSA） */
    publicKeyAlgorithm?: string;
    /** 登录用户类型 */
    loginUserType?: string;
    /** CA 机构编码 */
    caOrgCode?: string;
    telephoneNumber?: string;
    /** 机构交易码（平台侧主体关联键） */
    orgTransactionCode?: string;
    personalTransactionCode?: string;
    /** 身份证号 SM3 哈希 */
    idCardHash?: string;
    /** 认证信息签名值（可二次验签） */
    dataSignatureValue?: string;
    signatureData?: string;
  }

  /** 生成登录二维码（第一步） */
  export function createLoginRequest() {
    return requestClient.post<LoginQrCode>('/bidding/ca/login/qrcode');
  }
  /** 查询登录二维码扫码状态（第二步轮询） */
  export function queryLoginStatus(tid: string) {
    return requestClient.get<LoginStatus>('/bidding/ca/login/status', { params: { tid } });
  }
  /** 取用户认证信息（第三步） */
  export function fetchAuthInfo(tid: string) {
    return requestClient.get<AuthInfo>('/bidding/ca/login/auth-info', { params: { tid } });
  }

  // ==================== 扫码签名 / 验签（三阶段，SM2） ====================

  /** 签名二维码（第一步） */
  export interface SignQrCode extends QrCodeBase {}

  /** 签名扫码状态（第二步） */
  export interface SignStatus {
    /** 已扫码 */
    scanned: boolean;
    /** 已签名（用户完成签名） */
    signed: boolean;
    /** 状态码/描述 */
    status?: string;
    message?: string;
  }

  /** 签名值（第三步） */
  export interface SignValue {
    /** 签名值（base64） */
    signatureValue?: string;
    /** 签名证书序列号 */
    signatureCertSn?: string;
    signatureCert?: string;
    /** 公钥算法（SM2/RSA） */
    publicKeyAlgorithm?: string;
    /** CA 机构编码 */
    caOrgCode?: string;
    /** 待签名原文（回显） */
    signatureMessage?: string;
    signatureUserType?: string;
  }

  /** 生成签名二维码（第一步）：提交待签 SM3 摘要（hex） */
  export function createSignRequest(digestHex: string) {
    return requestClient.post<SignQrCode>('/bidding/ca/sign/qrcode', null, {
      params: { digestHex },
    });
  }
  /** 查询签名二维码扫码状态（第二步轮询） */
  export function querySignStatus(tid: string) {
    return requestClient.get<SignStatus>('/bidding/ca/sign/status', { params: { tid } });
  }
  /** 取签名值（第三步） */
  export function fetchSignatureValue(tid: string) {
    return requestClient.get<SignValue>('/bidding/ca/sign/value', { params: { tid } });
  }
  /** 本地测试密钥补签（没有 CA 手机 APP 时把"扫码签名"通道走完）：
   *  后端按本次会话的**真实待签 SM3 摘要**，用本地测试账号私钥做一次真实 SM2 签名
   *  —— 国密算法规格真实，但签发者不是官方 CA，仅测试态使用。 */
  export function simulateSignValue(tid: string) {
    return requestClient.post<SignValue>('/bidding/ca/sign/simulate', null, { params: { tid } });
  }
  /** 验签：入参原文 SM3 摘要 + 签名值(base64) + 证书公钥算法(RSA/SM2) */
  export function verify(digestHex: string, signatureBase64: string, publicKeyAlgorithm: string) {
    return requestClient.post<boolean>('/bidding/ca/verify', null, {
      params: { digestHex, signatureBase64, publicKeyAlgorithm },
    });
  }

  // ==================== 扫码解密（开标，三阶段） ====================

  /** 解密二维码（第一步） */
  export interface DecryptQrCode extends QrCodeBase {}

  /** 解密扫码状态（第二步） */
  export interface DecryptStatus {
    scanned: boolean;
    /** 状态码（01已扫 00未扫） */
    status?: string;
    message?: string;
  }

  /** 解密用户信息（第三步前置） */
  export interface DecryptUserInfo {
    tid?: string;
    /** 访问令牌（上传信封/取解密数据用） */
    accessToken?: string;
    /** 解密人类型（01个人/02机构） */
    decryptionUserType?: string;
    idCardHash?: string;
    orgCode?: string;
  }

  /** 加密密钥信封（投标文件 SM4 密钥的 SM2 加密密文 + 证书信息） */
  export interface DecryptEnvelope {
    encryptedKeyDataEnvelopeId?: string;
    caOrgCode?: string;
    encryptionCertSn?: string;
    /** 加密后的密钥（SM2 加密对称密钥密文） */
    encryptionKey?: string;
    /** 公钥算法（00:SM2 01:RSA） */
    publicKeyAlgorithm?: string;
  }

  /** 信封上传结果 */
  export interface DecryptEnvelopeResult {
    msg?: string;
    returnCode?: string;
  }

  /** 解密后的数据（第三步，含解密密钥） */
  export interface DecryptData {
    tid?: string;
    decryptStatus?: string;
    decryptStatusInfo?: string;
    encryptedKeyDataEnvelopeId?: string;
    /** 解密后的密钥（SM4 对称密钥明文，用于解密投标文件） */
    decryptedKey?: string;
  }

  /** 生成解密二维码（第一步）：解密人（开标主持人等）CA 扫码授权 */
  export function createDecryptRequest(
    decryptionUserType: string,
    orgCode: string,
    idCardHash?: string,
  ) {
    return requestClient.post<DecryptQrCode>('/bidding/ca/decrypt/qrcode', null, {
      params: { decryptionUserType, orgCode, idCardHash },
    });
  }
  /** 查询解密扫码状态（第二步轮询） */
  export function queryDecryptStatus(tid: string) {
    return requestClient.get<DecryptStatus>('/bidding/ca/decrypt/status', { params: { tid } });
  }
  /** 取解密用户信息（第三步前置） */
  export function fetchDecryptUserInfo(tid: string) {
    return requestClient.get<DecryptUserInfo>('/bidding/ca/decrypt/user-info', { params: { tid } });
  }
  /** 上传加密密钥信封（开标解密前，解密人提交信封） */
  export function uploadDecryptEnvelope(tid: string, accessToken: string, envelopes: DecryptEnvelope[]) {
    return requestClient.post<DecryptEnvelopeResult>('/bidding/ca/decrypt/envelope', null, {
      params: { tid, accessToken },
      data: envelopes,
    });
  }
  /** 取解密数据（第三步） */
  export function fetchDecryptedData(tid: string) {
    return requestClient.get<DecryptData[]>('/bidding/ca/decrypt/data', { params: { tid } });
  }

  // ==================== 本地国密（SM3 摘要，GB/T 32905） ====================

  /** 请求后端计算 SM3 摘要（真实国密 GB/T 32905）：GET /bidding/ca/sm3?text=，返回 64 位 hex。
   * 对应 CaController#sm3 —— 投标文件指纹（document_hash）按用户裁定 = SM3；
   * 浏览器 WebCrypto 无 SM3，前端一律走后端接口（登录后自动带 token）。 */
  export function fetchSm3Hex(text: string) {
    return requestClient.get<string>('/bidding/ca/sm3', { params: { text } });
  }

  /** 本地兜底摘要（非国密）：仅当后端 sm3 接口不可达时，对文本做 UTF-8 → hex 展示用。
   * 真实签名链路一律用 {@link fetchSm3Hex}（后端 SM3，GB/T 32905）。
   * ⚠️ 文件级摘要目前没有国密来源：上传响应只回 sha256（国际算法），后端也没有「按 fileId 取文件 SM3」的接口，
   *   故文件类「扫码签名」只能用 sha256 占位（**仅测试**，界面已显式标注为 SHA-256 占位）；
   *   盖章链路不受影响 —— /bidding/ca/seal/document 由后端对文件字节自行计算 SM3。
   *   后端需求见《后端变更通知-CA签章对接官方口径-2026-09-21》§8.9。 */
  export function sm3Hex(data: string | Uint8Array): string {
    if (typeof data === 'string') {
      return Array.from(new TextEncoder().encode(data))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('');
    }
    return Array.from(data)
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }

  // ==================== CA 文件签章（seal · 红章 PDF/分离式签名，直接落库台账） ====================

  /** 文件签章记录（CaSealRespVO：签章证据链 + 验签状态；主键雪花 ID 以字符串传递） */
  export interface SealRecord {
    id?: number | string;
    documentId?: string;
    documentName?: string;
    documentType?: 'other' | 'pdf' | string;
    fileId?: number | string;
    fileUrl?: string;
    fileSha256?: string;
    sm3Hash?: string;
    signature?: string;
    certSn?: string;
    orgName?: string;
    sealTime?: string;
    sealFileId?: number | string;
    sealFileUrl?: string;
    sealFileName?: string;
    trackId?: number | string;
    verifyStatus?: 'FAIL' | 'PASS' | 'UNVERIFIED' | string;
    verifyTime?: number | string;
    operatorUserId?: number | string;
    remark?: string;
    createTime?: number | string;
  }

  /** 上传后 CA 认证检测（CaSealCheckRespVO） */
  export interface SealCheck {
    caAuthed?: boolean;
    sealId?: number | string;
    documentId?: string;
    documentName?: string;
    orgName?: string;
    sealTime?: string;
    verifyStatus?: string;
    sealFileId?: number | string;
    sealFileUrl?: string;
  }

  /** CA 签章统计（CaSealStatsRespVO：演示仪表卡） */
  export interface SealStats {
    total?: number;
    pass?: number;
    fail?: number;
    unverified?: number;
  }

  /** 签章 document 签发响应（SealDocumentRespVO：对齐 30 号文档 H5 document 参数语义） */
  export interface SealDocument {
    documentId?: string;
    tradingSystemCode?: string;
    networkPath?: string;
    callbackUrl?: string;
    signature?: string;
    certSn?: string;
    hash?: string;
    sha256?: string;
    orgName?: string;
    sealTime?: string;
    sealFileId?: number | string;
    sealFileUrl?: string;
    sealFileName?: string;
    trackId?: number | string;
  }

  /** 签章台账分页入参（CaSealPageReqVO） */
  export interface SealPageParams extends PageParam {
    documentName?: string;
    orgName?: string;
    verifyStatus?: string;
    trackId?: number | string;
  }

  /** 文件电子签章完成回调入参（SealCallbackReqVO；本地/官方 H5 统一收口） */
  export interface SealCallbackReq {
    businessCode: string;
    documentId: string;
    signature: string;
    certSn: string;
    hash: string;
    sha256?: string;
    sealFileId?: number | string;
    sealFileUrl?: string;
    caPublicKey?: string;
  }

  /** 文件签章（POST /bidding/ca/seal/document，multipart：file+businessCode+callbackUrl）；
   *  测试配置（enabled=false）本地真实 SM2 签名 + PDF 红章/分离式签名信息文件，直接落库台账。 */
  export function sealDocument(file: File, businessCode?: string, callbackUrl?: string) {
    return requestClient.upload<SealDocument>('/bidding/ca/seal/document', {
      file,
      ...(businessCode ? { businessCode } : {}),
      ...(callbackUrl ? { callbackUrl } : {}),
    });
  }

  /** 批量文件签章（POST /bidding/ca/seal/batch，multipart：多个 files；批内一个事务全成或全滚） */
  export function sealBatch(files: File[], businessCode?: string, callbackUrl?: string) {
    const form = new FormData();
    files.forEach((f) => form.append('files', f));
    if (businessCode) form.append('businessCode', businessCode);
    if (callbackUrl) form.append('callbackUrl', callbackUrl);
    return requestClient.post<SealDocument[]>('/bidding/ca/seal/batch', form);
  }

  /** 文件签章完成回调（POST /bidding/ca/seal/callback；本地入库前验签，不匹配拒绝） */
  export function sealCallback(req: SealCallbackReq) {
    return requestClient.post<boolean>('/bidding/ca/seal/callback', req);
  }

  /** 上传后检测文件是否已 CA 认证（GET /bidding/ca/seal/check?fileId=&sha256=；
   *  caAuthed=false 时前端提示「该文件未 CA 认证」并询问是否使用 CA 认证功能） */
  export function checkSealAuth(fileId: number | string, sha256?: string) {
    return requestClient.get<SealCheck>('/bidding/ca/seal/check', {
      params: { fileId, sha256 },
    });
  }

  /** CA 签章台账分页（GET /bidding/ca/seal/page；权限 bidding:ca:query） */
  export function getSealPage(params: SealPageParams) {
    return requestClient.get<PageResult<SealRecord>>('/bidding/ca/seal/page', { params });
  }

  /** CA 签章统计（GET /bidding/ca/seal/stats；权限 bidding:ca:query） */
  export function getSealStats() {
    return requestClient.get<SealStats>('/bidding/ca/seal/stats');
  }

  /** CA 签章记录详情（GET /bidding/ca/seal/get?id=；权限 bidding:ca:query） */
  export function getSealDetail(id: number | string) {
    return requestClient.get<SealRecord>('/bidding/ca/seal/get', { params: { id } });
  }

  /** 验签（POST /bidding/ca/seal/verify?id= —— 对台账 SM3 指纹 + SM2 签名真实验签，回写 PASS/FAIL；权限 bidding:ca:verify） */
  export function verifySeal(id: number | string) {
    return requestClient.post<SealRecord>('/bidding/ca/seal/verify', null, { params: { id } });
  }

  /** 生成演示签章数据（POST /bidding/ca/seal/demo-seed?count=；真实 SM2+红章，验签必 PASS） */
  export function seedSealDemo(count = 5) {
    return requestClient.post<SealRecord[]>('/bidding/ca/seal/demo-seed', null, { params: { count } });
  }

  /** 生成演示「被篡改」数据（POST /bidding/ca/seal/demo-tamper?count=；验签必 FAIL，展示检出能力） */
  export function seedSealTamper(count = 2) {
    return requestClient.post<SealRecord[]>('/bidding/ca/seal/demo-tamper', null, { params: { count } });
  }

  /** 清空演示签章数据（POST /bidding/ca/seal/demo-clear；含篡改样例，返回删除条数） */
  export function clearSealDemo() {
    return requestClient.post<number>('/bidding/ca/seal/demo-clear');
  }
}

/* ==================== 官方 CA 互认签章（/bidding/ca/share/**） ====================
 * 契约：《后端变更通知-CA签章对接官方口径-2026-09-21》§二/§五。
 * ⚠️ 与 /bidding/ca/seal/** 是**两套口径**，不要混用：
 *    · /seal/**   = 本模块自定义（本地测试签章、演示、单纯盖章）→ CaSealAuth 用
 *    · /share/**  = **官方固定格式**（对接官方 CA 互认签章工具/组件）→ 本组接口
 * ⚠️ **调起参数（含 clientSecret / accessKeySecret）必须由后端签发**，前端不得硬编码密钥。
 *    端点**已落地**（契约 §8.1，后端提交 c1388c02f）：POST /bidding/ca/share/launch-param。
 *    请求体字段是**官方那套名字**（awaitingSignatureFilePath / awaitingSignatureFileName …），
 *    不是 fileId/filePath —— 2026-09-21 实测：发 fileId/filePath 会 code=400 并由 fieldErrors 点名这两个必填。 */

/** 官方调起参数（字段对齐官方《指南》§5.3.1；**字段名照抄，含大小写**） */
export interface CaShareLaunchParam {
  /** 使用场景：00 文件签章 / 01 文件加密（签章传 00）
   *  ⚠️ 大驼峰，不是 caShareUseScene —— 官方原文如此，不要"纠正" */
  CAShareUseScene: string;
  keyParameter: {
    document: Array<{
      awaitingSignatureFileName: string;
      /** 待签章文件地址（本地路径或网络路径） */
      awaitingSignatureFilePath: string;
      /** pdf / ofd */
      awaitingSignatureFileType: string;
      signatureFileName: string;
      /** 签章后文件的保存路径 */
      signatureFileSavePath: string;
    }>;
    seal?: { dpi?: number; sealImgMaxValue?: number; transparency?: number };
  };
  caShareComponentConfig: {
    accessKeySecret: string;
    clientId: string;
    /** 官方 JSON 示例字段名（推荐用这个） */
    clientSecret?: string;
    /** 官方文档表格字段名（拼写瑕疵 ClientSercet）；后端两个都收，填其一即可 */
    ClientSercet?: string;
    serviceUrl: string;
    tradingSystemCode: string;
  };
  tradingSystemConfig: {
    /** ⚠️ 大写 B，不是 callbackUrl —— 官方原文如此 */
    callBackUrl: string;
  };
}

/** 官方回调类型：H5 组件 / 客户端工具（两者结构不同、**回调地址也不同**，不可混用） */
export type CaShareCallbackType = 'client' | 'h5';

/** 官方回调地址（填进 tradingSystemConfig.callBackUrl；按当前域名拼，避免各环境写死） */
export function caShareCallbackUrl(type: CaShareCallbackType): string {
  const path = type === 'h5' ? '/bidding/ca/share/seal/callback' : '/bidding/ca/share/seal/client-callback';
  const origin = globalThis.location?.origin ?? '';
  return origin + '/admin-api' + path;
}

/**
 * 签发官方调起参数（后端用配置里的平台凭证填充密钥，前端只拿结果 —— 明文密钥不出服务端）。
 *
 * 请求体字段 = 后端 `CaShareLaunchReqVO`（**官方字段名，照抄不改**）：
 *   awaitingSignatureFilePath / awaitingSignatureFileName（**必填**）；
 *   signatureFileSavePath / signatureFileName / fileType（可选：后端按原名派生 `_sealed`、fileType 默认 pdf）。
 *
 * ⚠️ 2026-09-21 实测勘误：本函数原先发 `{callbackType,fileId,fileName,filePath}`（照响应 VO 猜的名字），
 *   后端返回 `code=400` + `fieldErrors:[awaitingSignatureFilePath, awaitingSignatureFileName]`，
 *   表现为「点签发没反应/只报一个看不懂的 400」。官方名必须逐字照抄，别按 camelCase 直觉改名。
 * ⚠️ 请求体里**没有** `callbackType`：回调地址由后端按配置下发到 `tradingSystemConfig.callBackUrl`；
 *   前端切 H5 / 客户端只影响「展示、复制、连通性自测」的目标地址，不影响签发结果。
 */
// 字段顺序按 lint 的字母序（非契约顺序）；与后端 CaShareLaunchReqVO 的对应关系见上方注释，勿改名。
export function fetchCaShareLaunchParam(req: {
  /** 待签章文件名称（后端 @NotBlank 必填） */
  awaitingSignatureFileName: string;
  /** 待签章文件地址（官方支持本地路径，或平台文件网络路径；后端 @NotBlank 必填） */
  awaitingSignatureFilePath: string;
  /** pdf / ofd（缺省 pdf） */
  fileType?: string;
  /** 签章后文件名称（留空由后端派生） */
  signatureFileName?: string;
  /** 签章后保存路径（留空由后端按待签路径派生 _sealed） */
  signatureFileSavePath?: string;
}) {
  return requestClient.post<CaShareLaunchParam>('/bidding/ca/share/launch-param', req);
}

/** 自测用：向官方回调端点发一条**空 document** 的官方报文，验证回调链路通不通。
 *  document 为空 → 后端不落库（不写脏数据），可安全用于连通性自测；
 *  同时验证"不带 token / 不带 tenant-id 也能进"（官方组件就是这个行为）。 */
export async function probeCaShareCallback(
  type: CaShareCallbackType,
): Promise<{ code?: number; errorCode?: null | string; msg?: string; success?: boolean }> {
  const body =
    type === 'h5'
      ? {
          action: 'allDone',
          data: {
            signatureInfo: { completedSignatureTime: '', document: [] },
            status: '20',
            transactionId: 'probe-' + Date.now(),
          },
        }
      : { callbackRequestType: 'signedAndVerified', document: [], tradingSystemCode: '' };
  const path = type === 'h5' ? '/bidding/ca/share/seal/callback' : '/bidding/ca/share/seal/client-callback';
  // ⚠️ 必须用裸 fetch，**不能**走 requestClient：
  //   ① 官方端点不认本模块 CommonResult —— 它以 {code:200,success:true,...} 表示成功，
  //      而 requestClient 的响应拦截器按 "code===0 才算成功" 判定，会把官方成功响应当成业务错误抛出；
  //   ② 官方组件本就不带 Authorization / tenant-id，裸 fetch 与它行为一致（端点已 @PermitAll + @TenantIgnore）。
  // 另注意：本框架鉴权失败是 **HTTP 200 + body.code=401**，故**不能**用 res.status 判登录态，要看 body.code。
  const res = await fetch('/admin-api' + path, {
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  });
  return (await res.json()) as { code?: number; errorCode?: null | string; msg?: string; success?: boolean };
}

/** 中文映射（能枚举不手写）：扫码状态 / 签名算法 / 解密人类型 / 认证用户类型 */
export const CA_LABELS = {
  loginStatus: { '00': '未扫码', '01': '已扫码', MOCK: '占位已扫码' } as Record<string, string>,
  signStatus: { '00': '未扫码', '01': '已扫码', '02': '已签名' } as Record<string, string>,
  publicKeyAlgorithm: { SM2: '国密 SM2', RSA: 'RSA' } as Record<string, string>,
  decryptionUserType: { '01': '个人', '02': '机构' } as Record<string, string>,
  loginUserType: { '01': '个人', '02': '机构' } as Record<string, string>,
  /** 签名/解密扫码状态中文（布尔兜底） */
  scannedText(scanned?: boolean): string {
    return scanned ? '已扫码' : '未扫码';
  },
};
