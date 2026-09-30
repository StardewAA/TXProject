package cn.iocoder.txgy.module.bidding.infra.ca;

import cn.hutool.core.util.StrUtil;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.EncryptAndDecryptSingleton;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.MessageDecrypt;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.param.EncryptedKeyDataEnvelopeDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.param.GenerateMessageDecryptQRCodeParamDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.param.GetDecryptUserInfoParamDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.param.GetDecryptedDataParamDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.param.QueryQRCodeScannedStatusParamDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.param.UploadEncryptedKeyDataEnvelopeParamDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.result.GenerateMessageDecryptQRCodeResultDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.result.GetDecryptUserInfoResultDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.result.GetDecryptedDataResultDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.result.QueryQRCodeScannedStatusResultDTO;
import com.cebpubservice.cashare.sdk.encryptanddecrypt.decrypt.result.UploadEncryptedKeyDataEnvelopeResultDTO;
import com.cebpubservice.cashare.sdk.sharedloginregistration.SharedLoginRegistration;
import com.cebpubservice.cashare.sdk.sharedloginregistration.SharedLoginRegistrationSingleton;
import com.cebpubservice.cashare.sdk.sharedloginregistration.param.GetUserAuthInfoParamDTO;
import com.cebpubservice.cashare.sdk.sharedloginregistration.result.GenerateLoginQRCodeResultDTO;
import com.cebpubservice.cashare.sdk.sharedloginregistration.result.GetUserAuthInfoResultDTO;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.MessageSignature;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.SignatureAndVerifySignatureSingleton;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.param.GenerateMessageSignatureQRCodeParamDTO;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.param.GetMessageSignatureValueParamDTO;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.param.VerifySignatureValueParamDTO;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.result.GenerateMessageSignatureQRCodeResultDTO;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.result.GetMessageSignatureValueResultDTO;
import com.cebpubservice.cashare.sdk.signatureandverifysignature.result.VerifySignatureValueResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部 CA 互认 SDK 真实实现（bidding.ca.sdk.enabled=true 时生效）。
 *
 * <p>对接 gitee.com/cebpubservice CA 互认体系（已 mvn install 到本地仓库）：
 * <ul>
 *   <li>共享登录注册 SharedLoginRegistration：扫码登录 + 用户认证信息（进房身份认证）</li>
 *   <li>签名验签 MessageSignature：扫码签名（H5 端展示二维码 → 手机 CA 签名） + 验签</li>
 *   <li>加解密 EncryptAndDecrypt：扫码解密（投标文件开标解密）</li>
 * </ul>
 *
 * <p>所有远程调用均为「扫码异步」形态：本实现按三阶段流程封装
 * （生成二维码 → 轮询扫码状态 → 取签名值/认证信息），并保留同步占位方法
 * {@link #sign}（业务未走扫码时不可用，抛清晰异常）。
 *
 * <p>测试环境（2026-09-18 实测，无需商务资格）：本地自部署 CA 互认共享组件
 * components-server（端口 8992，启动脚本 .tmp/start-ca-component.ps1）SDK 直连凭证：
 * tradingSystemCode=axj11111111 / serviceUrl=http://127.0.0.1:8992/ /
 * clientId=trading_system / clientSecret=35AD59C501ACF5DCDF6F221D890917C3 /
 * signatureSecret=256b00c7f8264a9fa1c11e1878f7879a；
 * 组件出网连官方测试节点（47.95.1.24:8443 / 123.56.236.113:8090）。
 *
 * <p>注意：SDK 的 Singleleton.init 为全局静态单例，本实现懒初始化一次；
 * 配置缺失（serviceUrl/clientId 等）时启动不报错，首次调用抛 {@link IllegalStateException} 提示配置项。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "bidding.ca.sdk", name = "enabled", havingValue = "true")
public class CaClientFacadeSdkImpl implements CaClientFacade {

    private final CaSdkProperties properties;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public CaClientFacadeSdkImpl(CaSdkProperties properties) {
        this.properties = properties;
    }

    // ==================== 初始化 ====================

    /**
     * 懒初始化三个 SDK 单例（幂等，线程安全）。
     * 配置缺失时抛 IllegalStateException 并给出完整配置项提示。
     */
    private void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        synchronized (this) {
            if (initialized.get()) {
                return;
            }
            if (StrUtil.isBlank(properties.getServiceUrl())
                    || StrUtil.isBlank(properties.getClientId())
                    || StrUtil.isBlank(properties.getClientSecret())
                    || StrUtil.isBlank(properties.getSignatureSecret())) {
                throw new IllegalStateException(
                        "外部 CA 互认 SDK 已启用但配置缺失：bidding.ca.sdk.serviceUrl/clientId/clientSecret/signatureSecret 必须完整配置。"
                                + "本地测试服务可直接用：serviceUrl=http://127.0.0.1:8992/ tradingSystemCode=axj11111111 "
                                + "clientId=trading_system clientSecret=35AD59C501ACF5DCDF6F221D890917C3 "
                                + "signatureSecret=256b00c7f8264a9fa1c11e1878f7879a（组件启动见 .tmp/start-ca-component.ps1，"
                                + "详见 docs/接口互认/CA认证示例代码/对接前置条件清单.md）");
            }
            String tradingSystemCode = StrUtil.nullToDefault(properties.getTradingSystemCode(), "DEFAULT");
            String url = properties.getServiceUrl();
            String clientId = properties.getClientId();
            String clientSecret = properties.getClientSecret();
            String signatureSecret = properties.getSignatureSecret();
            SignatureAndVerifySignatureSingleton.init(tradingSystemCode, url, clientId, clientSecret, signatureSecret);
            SharedLoginRegistrationSingleton.init(tradingSystemCode, url, clientId, clientSecret, signatureSecret);
            EncryptAndDecryptSingleton.init(tradingSystemCode, url, clientId, clientSecret, signatureSecret);
            initialized.set(true);
            log.info("[CA SDK] 已初始化 serviceUrl={} tradingSystemCode={}", url, tradingSystemCode);
        }
    }

    // ==================== 身份认证（扫码登录三阶段） ====================

    @Override
    public LoginQrCode createLoginRequest() {
        ensureInitialized();
        GenerateLoginQRCodeResultDTO dto = SharedLoginRegistration.generateLoginQRCode(new com.cebpubservice.cashare.sdk.sharedloginregistration.param.GenerateLoginQRCodeParamDTO());
        if (dto == null) {
            throw new IllegalStateException("CA 扫码登录生成二维码失败：组件返回 null");
        }
        LoginQrCode qr = new LoginQrCode();
        qr.tid = dto.getTid();
        qr.qrCodeImageBase64 = dto.getQrCodeImageBase64();
        qr.qrCodeJumpUrl = dto.getQrCodeJumpUrl();
        qr.qrCodeShowText = dto.getQrCodeShowText();
        return qr;
    }

    @Override
    public LoginStatus queryLoginStatus(String tid) {
        ensureInitialized();
        com.cebpubservice.cashare.sdk.sharedloginregistration.param.QueryQRCodeScannedStatusParamDTO param =
                new com.cebpubservice.cashare.sdk.sharedloginregistration.param.QueryQRCodeScannedStatusParamDTO();
        param.setTid(tid);
        com.cebpubservice.cashare.sdk.sharedloginregistration.result.QueryQRCodeScannedStatusResultDTO dto =
                SharedLoginRegistration.queryQRCodeScannedStatus(param);
        LoginStatus status = new LoginStatus();
        if (dto == null) {
            status.scanned = false;
            status.status = "00";
            status.message = "组件返回 null";
            return status;
        }
        status.scanned = "01".equals(dto.getScannedStatus());
        status.status = dto.getScannedStatus();
        status.message = status.scanned ? "已扫码" : "未扫码";
        return status;
    }

    @Override
    public AuthInfo fetchAuthInfo(String tid) {
        ensureInitialized();
        GetUserAuthInfoParamDTO param = new GetUserAuthInfoParamDTO();
        param.setTid(tid);
        GetUserAuthInfoResultDTO dto = SharedLoginRegistration.getUserAuthInfo(param);
        if (dto == null) {
            return null;
        }
        AuthInfo auth = new AuthInfo();
        auth.tid = dto.getTid();
        auth.orgName = dto.getOrgName();
        auth.orgCode = dto.getOrgCode();
        auth.personalName = dto.getPersonalName();
        auth.signatureCertSn = dto.getSignatureCertSn();
        auth.signatureCert = dto.getSignatureCert();
        auth.publicKeyAlgorithm = dto.getPublicKeyAlgorithm();
        auth.loginUserType = dto.getLoginUserType();
        auth.caOrgCode = dto.getCaOrgCode();
        auth.telephoneNumber = dto.getTelephoneNumber();
        auth.orgTransactionCode = dto.getOrgTransactionCode();
        auth.personalTransactionCode = dto.getPersonalTransactionCode();
        auth.idCardHash = dto.getIdCardHash();
        auth.dataSignatureValue = dto.getDataSignatureValue();
        auth.signatureData = dto.getSignatureData();
        return auth;
    }

    /**
     * 同步身份认证（进房入口）。
     * SDK 真实认证为扫码异步流程，同步接口无 tid 可核对——此处语义：
     * ① 证书号非空为前置门槛；② 提示调用方按三阶段扫码完成强认证。
     * 真实进房流程应使用 {@link #createLoginRequest}/{@link #queryLoginStatus}/{@link #fetchAuthInfo} 后核对 orgCode/caCertNo。
     */
    @Override
    public boolean authenticate(String caCertNo, String subjectCode) {
        ensureInitialized();
        boolean pass = StrUtil.isNotBlank(caCertNo);
        if (pass) {
            log.info("[CA SDK] 同步认证门槛通过 cert={}（完整认证请走扫码三阶段核对）", caCertNo);
        }
        return pass;
    }

    // ==================== 签名 / 验签（扫码签名） ====================

    @Override
    public SignQrCode createSignRequest(String sm3DigestHex) {
        ensureInitialized();
        GenerateMessageSignatureQRCodeParamDTO param = new GenerateMessageSignatureQRCodeParamDTO();
        param.setSignatureMessage(sm3DigestHex);
        GenerateMessageSignatureQRCodeResultDTO dto = MessageSignature.generateMessageSignatureQRCode(param);
        if (dto == null) {
            throw new IllegalStateException("CA 扫码签名生成二维码失败：组件返回 null");
        }
        SignQrCode qr = new SignQrCode();
        qr.tid = dto.getTid();
        qr.qrCodeImageBase64 = dto.getQrCodeImageBase64();
        qr.qrCodeJumpUrl = dto.getQrCodeJumpUrl();
        qr.qrCodeShowText = dto.getQrCodeShowText();
        return qr;
    }

    @Override
    public SignStatus querySignStatus(String tid) {
        ensureInitialized();
        com.cebpubservice.cashare.sdk.signatureandverifysignature.param.QueryQRCodeScannedStatusParamDTO param =
                new com.cebpubservice.cashare.sdk.signatureandverifysignature.param.QueryQRCodeScannedStatusParamDTO();
        param.setTid(tid);
        com.cebpubservice.cashare.sdk.signatureandverifysignature.result.QueryQRCodeScannedStatusResultDTO dto =
                MessageSignature.queryQRCodeScannedStatus(param);
        SignStatus status = new SignStatus();
        if (dto == null) {
            status.scanned = false;
            status.signed = false;
            status.status = "00";
            status.message = "组件返回 null";
            return status;
        }
        status.scanned = "01".equals(dto.getScannedStatus());
        status.signed = status.scanned; // 扫码后即签名（CA 手机端完成签名才回扫）
        status.status = dto.getScannedStatus();
        status.message = status.scanned ? "已扫码已签名" : "未扫码";
        return status;
    }

    @Override
    public SignValue fetchSignatureValue(String tid) {
        ensureInitialized();
        GetMessageSignatureValueParamDTO param = new GetMessageSignatureValueParamDTO();
        param.setTid(tid);
        GetMessageSignatureValueResultDTO dto = MessageSignature.getMessageSignatureValue(param);
        if (dto == null) {
            return null;
        }
        SignValue value = new SignValue();
        value.signatureValue = dto.getMessageSignatureValue();
        value.signatureCertSn = dto.getSignatureCertSn();
        value.signatureCert = dto.getSignatureCert();
        value.publicKeyAlgorithm = dto.getPublicKeyAlgorithm();
        value.caOrgCode = dto.getCaOrgCode();
        value.signatureMessage = dto.getSignatureMessage();
        value.signatureUserType = dto.getSignatureUserType();
        return value;
    }

    /**
     * 同步签名：SDK 签名必须走扫码流程，本方法抛异常提示（业务侧若需同步签名应改用扫码三阶段）。
     */
    @Override
    public String sign(String sm3DigestHex, String caCertNo) {
        throw new UnsupportedOperationException(
                "CA 互认 SDK 签名必须走扫码流程：先 createSignRequest 展示二维码，用户手机 CA 签名后 fetchSignatureValue 取签名值");
    }

    @Override
    public boolean verify(String sm3DigestHex, String signatureBase64, String publicKeyAlgorithm) {
        ensureInitialized();
        VerifySignatureValueParamDTO param = new VerifySignatureValueParamDTO();
        param.setSignatureMessage(sm3DigestHex);
        param.setMessageSignatureValue(signatureBase64);
        param.setPublicKeyAlgorithm(StrUtil.blankToDefault(publicKeyAlgorithm, properties.getPublicKeyAlgorithm()));
        VerifySignatureValueResultDTO dto = MessageSignature.verifySignatureValue(param);
        if (dto == null) {
            log.warn("[CA SDK] 验签组件返回 null digest={}", sm3DigestHex);
            return false;
        }
        return dto.getVerifySignatureResult();
    }

        // ==================== 解密（扫码三阶段，MessageDecrypt） ====================

    @Override
    public DecryptQrCode createDecryptRequest(String decryptionUserType, String orgCode, String idCardHash) {
        ensureInitialized();
        GenerateMessageDecryptQRCodeParamDTO param = new GenerateMessageDecryptQRCodeParamDTO();
        param.setDecryptionUserType(decryptionUserType);
        param.setOrgCode(orgCode);
        param.setIdCardHash(idCardHash);
        GenerateMessageDecryptQRCodeResultDTO dto = MessageDecrypt.generateMessageDecryptQRCode(param);
        if (dto == null) {
            throw new IllegalStateException("CA 扫码解密生成二维码失败：组件返回 null");
        }
        DecryptQrCode qr = new DecryptQrCode();
        qr.tid = dto.getTid();
        qr.qrCodeImageBase64 = dto.getQrCodeImageBase64();
        qr.qrCodeJumpUrl = dto.getQrCodeJumpUrl();
        qr.qrCodeShowText = dto.getQrCodeShowText();
        return qr;
    }

    @Override
    public DecryptStatus queryDecryptStatus(String tid) {
        ensureInitialized();
        QueryQRCodeScannedStatusParamDTO param = new QueryQRCodeScannedStatusParamDTO();
        param.setTid(tid);
        QueryQRCodeScannedStatusResultDTO dto = MessageDecrypt.queryQRCodeScannedStatus(param);
        DecryptStatus status = new DecryptStatus();
        if (dto == null) {
            status.scanned = false;
            status.status = "00";
            status.message = "组件返回 null";
            return status;
        }
        status.scanned = "01".equals(dto.getScannedStatus());
        status.status = dto.getScannedStatus();
        status.message = status.scanned ? "已扫码已授权" : "未扫码";
        return status;
    }

    @Override
    public DecryptUserInfo fetchDecryptUserInfo(String tid) {
        ensureInitialized();
        GetDecryptUserInfoParamDTO param = new GetDecryptUserInfoParamDTO();
        param.setTid(tid);
        GetDecryptUserInfoResultDTO dto = MessageDecrypt.getDecryptUserInfo(param);
        if (dto == null) {
            return null;
        }
        DecryptUserInfo info = new DecryptUserInfo();
        info.tid = dto.getTid();
        info.accessToken = dto.getAccessToken();
        info.decryptionUserType = dto.getDecryptionUserType();
        info.idCardHash = dto.getIdCardHash();
        info.orgCode = dto.getOrgCode();
        return info;
    }

    @Override
    public DecryptEnvelopeResult uploadDecryptEnvelope(String tid, String accessToken, List<DecryptEnvelope> envelopes) {
        ensureInitialized();
        UploadEncryptedKeyDataEnvelopeParamDTO param = new UploadEncryptedKeyDataEnvelopeParamDTO();
        param.setTid(tid);
        param.setAccessToken(accessToken);
        if (envelopes != null && !envelopes.isEmpty()) {
            List<EncryptedKeyDataEnvelopeDTO> list = new ArrayList<>(envelopes.size());
            for (DecryptEnvelope e : envelopes) {
                EncryptedKeyDataEnvelopeDTO dto = new EncryptedKeyDataEnvelopeDTO();
                dto.setEncryptedKeyDataEnvelopeId(e.encryptedKeyDataEnvelopeId);
                dto.setCaOrgCode(e.caOrgCode);
                dto.setEncryptionCertSn(e.encryptionCertSn);
                dto.setEncryptionKey(e.encryptionKey);
                dto.setPublicKeyAlgorithm(e.publicKeyAlgorithm);
                list.add(dto);
            }
            param.setEncryptedKeyDataEnvelopeList(list);
        }
        UploadEncryptedKeyDataEnvelopeResultDTO dto = MessageDecrypt.uploadEncryptedKeyDataEnvelope(param);
        DecryptEnvelopeResult result = new DecryptEnvelopeResult();
        if (dto == null) {
            result.returnCode = "-1";
            result.msg = "组件返回 null";
            return result;
        }
        result.returnCode = dto.getReturnCode();
        result.msg = dto.getMsg();
        return result;
    }

    @Override
    public List<DecryptData> fetchDecryptedData(String tid) {
        ensureInitialized();
        GetDecryptedDataParamDTO param = new GetDecryptedDataParamDTO();
        param.setTid(tid);
        List<GetDecryptedDataResultDTO> list = MessageDecrypt.getDecryptedData(param);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<DecryptData> result = new ArrayList<>(list.size());
        for (GetDecryptedDataResultDTO dto : list) {
            DecryptData data = new DecryptData();
            data.tid = dto.getTid();
            data.decryptStatus = dto.getDecryptStatus();
            data.decryptStatusInfo = dto.getDecryptStatusInfo();
            data.encryptedKeyDataEnvelopeId = dto.getEncryptedKeyDataEnvelopeId();
            data.decryptedKey = dto.getDecryptedKey();
            result.add(data);
        }
        return result;
    }

    /**
     * 同步解密：SDK 解密走扫码授权（解密人 CA 扫码），本方法抛异常提示；
     * 真实开标解密请用三阶段 createDecryptRequest → queryDecryptStatus → fetchDecryptUserInfo
     * → uploadDecryptEnvelope → fetchDecryptedData。
     */
    @Override
    public byte[] decrypt(byte[] cipher, String caCertNo) {
        throw new UnsupportedOperationException(
                "CA 互认 SDK 解密走扫码授权流程（解密人 CA 扫码），同步接口不可用；请用 createDecryptRequest 三阶段实现开标解密");
    }
}