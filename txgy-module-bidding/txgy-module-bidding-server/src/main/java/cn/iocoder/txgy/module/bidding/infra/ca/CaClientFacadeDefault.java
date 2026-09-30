package cn.iocoder.txgy.module.bidding.infra.ca;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CA SDK 占位实现（bidding.ca.sdk.enabled 缺省 false 时生效）。
 *
 * <p>结构/签名与真实 SDK 对齐，行为宽松（方便测试与前端联调），并 log 标记"[本地CA]"。
 * 拉高联调完成度：登录/认证返回**统一测试账号**（用户裁定：所有用户共用一个账号），
 * 签名/验签为**真实 SM2 签名**（{@link LocalCryptoUtils} 统一测试密钥对，可自洽验签），
 * 仅证书未获官方认可。远程共享组件（components-server）与自助开通凭证就绪后，
 * 配置 bidding.ca.sdk.enabled=true 即切换到 {@link CaClientFacadeSdkImpl} 真实实现，业务代码无需改动。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "bidding.ca.sdk", name = "enabled", havingValue = "false", matchIfMissing = true)
public class CaClientFacadeDefault implements CaClientFacade {

    /** 签名二维码 tid → 待签 SM3 摘要（fetchSignatureValue 需按 tid 回查原文摘要做真实签名） */
    private final Map<String, String> signTidDigestMap = new ConcurrentHashMap<>();

    @Override
    public boolean authenticate(String caCertNo, String subjectCode) {
        boolean pass = StrUtil.isNotBlank(caCertNo);
        if (pass) {
            log.info("[CA占位] 认证通过 cert={} subject={}", caCertNo, subjectCode);
        } else {
            log.warn("[CA占位] 认证失败：证书号为空");
        }
        return pass;
    }

    @Override
    public LoginQrCode createLoginRequest() {
        log.info("[CA占位] 生成登录二维码");
        LoginQrCode qr = new LoginQrCode();
        qr.tid = "TID-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return qr;
    }

    @Override
    public LoginStatus queryLoginStatus(String tid) {
        log.info("[CA占位] 查询登录状态 tid={}", tid);
        LoginStatus status = new LoginStatus();
        status.scanned = true;
        status.status = "01";
        status.message = "MOCK：已扫码";
        return status;
    }

    @Override
    public AuthInfo fetchAuthInfo(String tid) {
        log.info("[本地CA] 取认证信息统一测试账号 tid={}", tid);
        AuthInfo auth = new AuthInfo();
        auth.tid = tid;
        auth.orgName = LocalCryptoUtils.LOCAL_TEST_ORG_NAME;
        auth.orgCode = LocalCryptoUtils.LOCAL_TEST_ORG_CODE;
        auth.signatureCertSn = LocalCryptoUtils.LOCAL_TEST_CERT_SN;
        auth.publicKeyAlgorithm = "SM2";
        auth.loginUserType = "02";
        return auth;
    }

    @Override
    public String sign(String sm3DigestHex, String caCertNo) {
        log.info("[本地CA] 真实 SM2 签名 digest={} cert={}", sm3DigestHex, caCertNo);
        return LocalCryptoUtils.sm2SignDigestHex(StrUtil.nullToEmpty(sm3DigestHex));
    }

    @Override
    public SignQrCode createSignRequest(String sm3DigestHex) {
        log.info("[本地CA] 生成签名二维码 digest={}", sm3DigestHex);
        SignQrCode qr = new SignQrCode();
        qr.tid = "TID-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        signTidDigestMap.put(qr.tid, StrUtil.nullToEmpty(sm3DigestHex));
        return qr;
    }

    @Override
    public SignStatus querySignStatus(String tid) {
        log.info("[CA占位] 查询签名状态 tid={}", tid);
        SignStatus status = new SignStatus();
        status.scanned = true;
        status.signed = true;
        status.status = "01";
        status.message = "MOCK：已扫码已签名";
        return status;
    }

    @Override
    public SignValue fetchSignatureValue(String tid) {
        log.info("[本地CA] 取签名值 tid={}", tid);
        // 统一测试账号真实 SM2 签名（对 SM3 摘要签名，可自洽验签）；未关联到摘要时兜底用 tid 自身摘要
        String digest = StrUtil.nullToDefault(signTidDigestMap.get(tid), LocalCryptoUtils.sm3Hex(tid));
        SignValue value = new SignValue();
        value.signatureValue = LocalCryptoUtils.sm2SignDigestHex(digest);
        value.signatureCertSn = LocalCryptoUtils.LOCAL_TEST_CERT_SN;
        value.publicKeyAlgorithm = "SM2";
        return value;
    }

    @Override
    public boolean verify(String sm3DigestHex, String signatureBase64, String publicKeyAlgorithm) {
        boolean pass = LocalCryptoUtils.sm2VerifyDigestHex(StrUtil.nullToEmpty(sm3DigestHex), signatureBase64);
        log.info("[本地CA] 真实验签 result={} algo={}", pass, publicKeyAlgorithm);
        return pass;
    }

    @Override
    public DecryptQrCode createDecryptRequest(String decryptionUserType, String orgCode, String idCardHash) {
        log.info("[CA占位] 生成解密二维码 type={} org={}", decryptionUserType, orgCode);
        DecryptQrCode qr = new DecryptQrCode();
        qr.tid = "TID-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return qr;
    }

    @Override
    public DecryptStatus queryDecryptStatus(String tid) {
        log.info("[CA占位] 查询解密状态 tid={}", tid);
        DecryptStatus status = new DecryptStatus();
        status.scanned = true;
        status.status = "01";
        status.message = "MOCK：已扫码已授权";
        return status;
    }

    @Override
    public DecryptUserInfo fetchDecryptUserInfo(String tid) {
        log.info("[CA占位] 取解密用户信息 tid={}", tid);
        DecryptUserInfo info = new DecryptUserInfo();
        info.tid = tid;
        info.accessToken = "MOCK-ACCESS-" + tid;
        info.decryptionUserType = "02";
        info.orgCode = "91110000MOCK000001";
        return info;
    }

    @Override
    public DecryptEnvelopeResult uploadDecryptEnvelope(String tid, String accessToken, List<DecryptEnvelope> envelopes) {
        log.info("[CA占位] 上传加密密钥信封 tid={} envelopes={}", tid, envelopes == null ? 0 : envelopes.size());
        DecryptEnvelopeResult result = new DecryptEnvelopeResult();
        result.returnCode = "200";
        result.msg = "MOCK：信封上传成功";
        return result;
    }

    @Override
    public List<DecryptData> fetchDecryptedData(String tid) {
        log.info("[CA占位] 取解密数据 tid={}", tid);
        DecryptData data = new DecryptData();
        data.tid = tid;
        data.decryptStatus = "SUCCESS";
        data.decryptStatusInfo = "MOCK";
        data.encryptedKeyDataEnvelopeId = "ENV-MOCK-001";
        data.decryptedKey = "MOCK-SM4-KEY";
        return java.util.Collections.singletonList(data);
    }

    @Override
    public byte[] decrypt(byte[] cipher, String caCertNo) {
        log.info("[CA占位] 解密 bytes={} cert={}", cipher == null ? 0 : cipher.length, caCertNo);
        return cipher;
    }
}
