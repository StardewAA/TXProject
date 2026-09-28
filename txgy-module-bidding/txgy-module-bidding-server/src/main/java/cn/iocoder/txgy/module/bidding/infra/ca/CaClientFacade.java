package cn.iocoder.txgy.module.bidding.infra.ca;

import java.util.List;

/**
 * 外部 CA SDK 适配层（Facade）。
 *
 * <p>用户裁定 2026-09-18：CA 加密/身份认证均对接外部接口——SDK 已从
 * gitee.com/cebpubservice（中国招标公共服务平台 CA 互认体系）下载并 mvn install 到本地仓库，
 * 本接口的真实实现见 {@link CaClientFacadeSdkImpl}（扫码登录认证/扫码签名验签/扫码解密）；
 * 远程共享组件（components-server）+ 自助开通凭证就绪前，配置 {@code bidding.ca.sdk.enabled=false}
 * 走 {@link CaClientFacadeDefault} 占位实现，保证业务闭环可测。
 *
 * <p>测试环境（2026-09-18 实测，无需商务资格）：本地自部署 CA 互认共享组件
 * components-server（端口 8992，启动脚本 .tmp/start-ca-component.ps1），
 * SDK 直连凭证：tradingSystemCode=axj11111111 / clientId=trading_system /
 * clientSecret=35AD59C501ACF5DCDF6F221D890917C3 / signatureSecret=256b00c7f8264a9fa1c11e1878f7879a；
 * 组件出网连官方测试节点（47.95.1.24:8443 / 123.56.236.113:8090，公网可达）。
 *
 * <p>接口清单见 docs/前端规范-全域接口说明/29-外部CA与身份认证对接清单-2026-09-18.md：
 * ① CA 登录认证 ② 证书有效性验证 ③ 数字签名/验签（SM2） ④ 数据加密/解密（SM2/SM4） ⑤ 可信时间戳（可选）。
 *
 * <p>签名/解密形态：CA 互认体系为**扫码异步**（非同步返回）——前端展示二维码 → 用户手机 CA 扫码 →
 * 轮询扫码状态 → 取签名值/解密结果。故除同步占位 {@link #sign}/{@link #decrypt} 外，
 * 提供扫码三阶段方法（createXxxRequest / queryXxxStatus / fetchXxx）。
 */
public interface CaClientFacade {

    // ==================== 身份认证 ====================

    /**
     * 身份认证（进开标房间 / 登录复用）：验证 CA 证书有效且与申报主体一致。
     * SDK 实现：共享登录注册 getUserAuthInfo（扫码登录）或证书有效性验证；
     * 占位实现：证书号非空即通过（测试便利）。
     *
     * @param caCertNo    CA 证书序列号
     * @param subjectCode 统一社会信用代码（申报主体）
     * @return 认证是否通过
     */
    boolean authenticate(String caCertNo, String subjectCode);

    // ==================== 扫码登录认证（共享登录注册 SDK） ====================

    /**
     * 生成登录二维码（认证第一步）：前端展示二维码，用户手机 CA 扫码完成身份认证。
     * SDK 实现：SharedLoginRegistration.generateLoginQRCode；
     * 占位实现：返回 mock tid。
     */
    LoginQrCode createLoginRequest();

    /**
     * 查询登录二维码扫码状态（第二步轮询：用户是否已扫码）。
     *
     * @param tid 登录会话 id
     */
    LoginStatus queryLoginStatus(String tid);

    /**
     * 取用户认证信息（第三步）：扫码完成后按 tid 取 CA 认证信息，用于进房身份核对。
     * 返回组织名称/组织代码/证书序列号/公钥算法等（GetUserAuthInfoResultDTO）。
     *
     * @param tid 登录会话 id
     * @return 认证信息（含头部核对字段）；未完成扫码返回 null
     */
    AuthInfo fetchAuthInfo(String tid);

    // ==================== 签名 / 验签（SM2，国密 GB/T 32918） ====================

    /**
     * 数字签名（对 SM3 摘要签名，SM2）。
     * SDK 实现：生成签名二维码后由用户扫码签名（异步），本方法为同步占位/本地测试路径。
     */
    String sign(String sm3DigestHex, String caCertNo);

    /**
     * 生成签名请求（扫码签名第一步）：提交待签原文摘要，返回二维码信息（tid + 二维码图片/链接）。
     *
     * @param sm3DigestHex 待签 SM3 摘要（十六进制）
     * @return 签名二维码会话（含 tid；前端展示 qrCodeImageBase64 / qrCodeJumpUrl）
     */
    SignQrCode createSignRequest(String sm3DigestHex);

    /**
     * 查询签名二维码扫码状态（第二步轮询：用户是否已扫码签名）。
     *
     * @param tid 签名会话 id
     * @return 扫码状态（已扫/未扫；CA 扫码即完成签名）
     */
    SignStatus querySignStatus(String tid);

    /**
     * 取签名值（第三步）：用户扫码签名完成后，按 tid 取签名值。
     *
     * @param tid 签名会话 id
     * @return 签名结果（签名值 base64 + 证书序列号 + 公钥算法）
     */
    SignValue fetchSignatureValue(String tid);

    /**
     * 验签。
     * SDK 实现：verifySignatureValue（原文摘要 + 签名值 + 公钥算法 SM2）。
     *
     * @param sm3DigestHex     待验签摘要
     * @param signatureBase64  签名值
     * @param publicKeyAlgorithm 证书公钥算法 RSA/SM2
     * @return 验签是否通过
     */
    boolean verify(String sm3DigestHex, String signatureBase64, String publicKeyAlgorithm);

    // ==================== 解密（SM4/SM2，扫码三阶段） ====================

    /**
     * 生成解密请求（扫码解密第一步）：解密人（开标主持人等）CA 扫码授权解密。
     *
     * @param decryptionUserType 解密人类型（01 个人 / 02 机构）
     * @param orgCode            组织代码（统一社会信用代码）
     * @param idCardHash         身份证号哈希（SM3，个人解密人必填；机构解密人可空）
     * @return 解密二维码会话（含 tid；前端展示 qrCodeImageBase64 / qrCodeJumpUrl）
     */
    DecryptQrCode createDecryptRequest(String decryptionUserType, String orgCode, String idCardHash);

    /**
     * 查询解密二维码扫码状态（第二步轮询：解密人是否已扫码授权）。
     *
     * @param tid 解密会话 id
     * @return 扫码状态（已扫/未扫）
     */
    DecryptStatus queryDecryptStatus(String tid);

    /**
     * 取解密用户信息（第三步前置）：扫码完成后取解密人信息 + accessToken。
     *
     * @param tid 解密会话 id
     * @return 解密人信息（含 accessToken，后续上传信封/取解密数据用）；未完成扫码返回 null
     */
    DecryptUserInfo fetchDecryptUserInfo(String tid);

    /**
     * 上传加密密钥信封：解密人扫码授权后，把投标文件加密密钥信封（SM2 加密的对称密钥）上传组件。
     *
     * @param tid       解密会话 id
     * @param accessToken fetchDecryptUserInfo 返回的访问令牌
     * @param envelopes 加密密钥信封列表（每信封含 caOrgCode/加密证书序列号/加密密钥）
     * @return 上传结果（returnCode/msg）
     */
    DecryptEnvelopeResult uploadDecryptEnvelope(String tid, String accessToken, List<DecryptEnvelope> envelopes);

    /**
     * 取解密后的数据（第三步）：信封上传后按 tid 取解密密钥，用于开标解密投标文件。
     *
     * @param tid 解密会话 id
     * @return 解密数据列表（含解密密钥 decryptedKey 与状态）
     */
    List<DecryptData> fetchDecryptedData(String tid);

    /**
     * 同步解密投标文件（开标解密）。
     * SDK 实现：走扫码解密（解密人 CA 扫码授权），同步接口不可用——抛异常提示用三阶段；
     * 占位实现：原样返回密文（测试便利）。
     */
    byte[] decrypt(byte[] cipher, String caCertNo);

    // ==================== 本地国密（不依赖远程） ====================

    /**
     * 本地 SM3 指纹（国密 GB/T 32905）：对字节数据计算 64 位十六进制摘要。
     * 投标文件指纹 document_hash 由此计算（用户裁定指纹 = SM3）。
     */
    default String sm3Hex(byte[] data) {
        return LocalCryptoUtils.sm3Hex(data);
    }

    // ==================== 值对象 ====================

    /** 登录二维码会话（扫码登录第一步返回值）。 */
    class LoginQrCode {
        public String tid;
        public String qrCodeImageBase64;
        public String qrCodeJumpUrl;
        public String qrCodeShowText;
    }

    /** 登录扫码状态（第二步返回值）。 */
    class LoginStatus {
        /** 已扫码 */
        public boolean scanned;
        /** 状态码（01已扫 00未扫） */
        public String status;
        public String message;
    }

    /** CA 认证信息（第三步返回值，进房身份核对依据）。 */
    class AuthInfo {
        public String tid;
        public String orgName;
        public String orgCode;
        public String personalName;
        public String signatureCertSn;
        public String signatureCert;
        public String publicKeyAlgorithm;
        public String loginUserType;
        public String caOrgCode;
        public String telephoneNumber;
        /** 机构交易码（平台侧主体关联键） */
        public String orgTransactionCode;
        /** 个人交易码（个人登录时） */
        public String personalTransactionCode;
        /** 身份证号 SM3 哈希（个人认证信息） */
        public String idCardHash;
        /** 认证信息签名值（dataSignatureValue，可二次验签） */
        public String dataSignatureValue;
        /** 签名原值（signatureData，与 dataSignatureValue 配对） */
        public String signatureData;
    }

    /** 签名二维码会话（扫码签名第一步返回值）。 */
    class SignQrCode {
        public String tid;
        public String qrCodeImageBase64;
        public String qrCodeJumpUrl;
        public String qrCodeShowText;
    }

    /** 扫码状态（第二步返回值）。 */
    class SignStatus {
        /** 已扫码（布尔） */
        public boolean scanned;
        /** 已签名（用户完成签名） */
        public boolean signed;
        /** 状态码/描述（原始透传） */
        public String status;
        public String message;
    }

    /** 签名值（第三步返回值）。 */
    class SignValue {
        public String signatureValue;
        public String signatureCertSn;
        public String signatureCert;
        public String publicKeyAlgorithm;
        /** CA 机构编码 */
        public String caOrgCode;
        /** 待签名原文（回显） */
        public String signatureMessage;
        /** 签名用户类型 */
        public String signatureUserType;
    }

    /** 解密二维码会话（扫码解密第一步返回值）。 */
    class DecryptQrCode {
        public String tid;
        public String qrCodeImageBase64;
        public String qrCodeJumpUrl;
        public String qrCodeShowText;
    }

    /** 解密扫码状态（第二步返回值）。 */
    class DecryptStatus {
        /** 已扫码（解密人已扫码授权） */
        public boolean scanned;
        /** 状态码（01已扫 00未扫） */
        public String status;
        public String message;
    }

    /** 解密用户信息（第三步前置返回值）。 */
    class DecryptUserInfo {
        public String tid;
        /** 访问令牌（上传信封/取解密数据用） */
        public String accessToken;
        /** 解密人类型（01个人/02机构） */
        public String decryptionUserType;
        public String idCardHash;
        public String orgCode;
    }

    /** 加密密钥信封（投标文件 SM4 密钥的 SM2 加密密文 + 证书信息）。 */
    class DecryptEnvelope {
        /** 信封唯一标识（业务平台自定义或取信封文件参数） */
        public String encryptedKeyDataEnvelopeId;
        /** CA 机构编码 */
        public String caOrgCode;
        /** 加密证书序列号 */
        public String encryptionCertSn;
        /** 加密后的密钥（SM2 加密对称密钥密文） */
        public String encryptionKey;
        /** 公钥算法（00:SM2 01:RSA） */
        public String publicKeyAlgorithm;
    }

    /** 信封上传结果。 */
    class DecryptEnvelopeResult {
        public String msg;
        public String returnCode;
    }

    /** 解密后的数据（第三步返回值，含解密密钥）。 */
    class DecryptData {
        public String tid;
        public String decryptStatus;
        public String decryptStatusInfo;
        public String encryptedKeyDataEnvelopeId;
        /** 解密后的密钥（SM4 对称密钥明文，用于解密投标文件） */
        public String decryptedKey;
    }
}
