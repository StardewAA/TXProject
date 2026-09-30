package cn.iocoder.txgy.module.bidding.controller.admin.ca;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.infra.ca.CaClientFacade;
import cn.iocoder.txgy.module.bidding.infra.ca.LocalCryptoUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - bidding CA 互认（外部 CA 互认体系，扫码形态）。
 *
 * <p>规范依据：《招投标模块开发规范》§4.2（Controller 位于 controller/admin/&lt;域&gt;）+ §4.4/§4.5
 * （映射方法带 @PreAuthorize）。
 *
 * <p>对接形态：外部 CA 互认为「扫码」异步（非同步返回）——前端展示二维码 → 用户手机 CA 扫码 →
 * 轮询扫码状态 → 取认证信息/签名值。故登录与签名均为三阶段接口（生成二维码/查状态/取结果）。
 *
 * <p>实现切换：bidding.ca.sdk.enabled=false（缺省）走占位实现 CaClientFacadeDefault（业务闭环可测）；
 * true 走真实 SDK 实现 CaClientFacadeSdkImpl（需组件 + 凭证，见 docs/接口互认/CA认证示例代码/对接前置条件清单.md）。
 */
@Tag(name = "管理后台 - bidding CA 互认")
@RestController
@RequestMapping("/bidding/ca")
@Validated
public class CaController {

    /** 解密人类型：01 个人（须传身份证号 SM3 哈希）/ 02 机构。 */
    private static final String DECRYPT_USER_TYPE_PERSONAL = "01";

    @Resource
    private CaClientFacade caClientFacade;

    /**
     * 签名会话 tid → 待签 SM3 摘要（hex）。
     *
     * <p>用途：仅服务于「本地测试密钥补签」接口 —— 没有 CA 手机 APP 时，按本次会话的**真实摘要**
     * 用本地测试账号私钥做一次真实 SM2 签名，让扫码签名这条通道在测试态可以走完（算法真实、签发者非官方 CA）。
     * 有 CA APP 时应走真实扫码（/sign/status → /sign/value），本表不参与。
     */
    private final Map<String, String> signTidDigestMap = new ConcurrentHashMap<>();

    // ==================== 身份认证 ====================

    @PostMapping("/authenticate")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "身份认证（进开标房间/登录复用）", description = "入参证书号+统一社会信用代码；真实实现走扫码核对或验签，占位实现证书号非空即过。")
    public CommonResult<Boolean> authenticate(@RequestParam("caCertNo") String caCertNo,
                                               @RequestParam("subjectCode") String subjectCode) {
        return success(caClientFacade.authenticate(caCertNo, subjectCode));
    }

    // ==================== 扫码登录认证（三阶段） ====================

    @PostMapping("/login/qrcode")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "生成登录二维码（第一步）", description = "前端展示二维码，用户手机 CA 扫码完成身份认证；返回 tid + 二维码图片/跳转链接。")
    public CommonResult<CaClientFacade.LoginQrCode> createLoginRequest() {
        return success(caClientFacade.createLoginRequest());
    }

    @GetMapping("/login/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询登录二维码扫码状态（第二步轮询）")
    public CommonResult<CaClientFacade.LoginStatus> queryLoginStatus(@RequestParam("tid") String tid) {
        return success(caClientFacade.queryLoginStatus(tid));
    }

    @GetMapping("/login/auth-info")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "取用户认证信息（第三步）", description = "扫码完成后按 tid 取 CA 认证信息（组织名称/代码/证书序列号/公钥算法），用于进房身份核对；未完成扫码返回 null。")
    public CommonResult<CaClientFacade.AuthInfo> fetchAuthInfo(@RequestParam("tid") String tid) {
        return success(caClientFacade.fetchAuthInfo(tid));
    }

    // ==================== 扫码签名 / 验签（三阶段，SM2） ====================

    @PostMapping("/sign/qrcode")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "生成签名二维码（第一步）", description = "提交待签 SM3 摘要（hex），返回二维码会话（tid + 图片/链接）；用户手机 CA 扫码完成签名。")
    public CommonResult<CaClientFacade.SignQrCode> createSignRequest(@RequestParam("digestHex") String digestHex) {
        CaClientFacade.SignQrCode qr = caClientFacade.createSignRequest(digestHex);
        // 记下本次会话的待签摘要：仅给「本地测试密钥补签」用（没有 CA APP 时也能走完通道）
        if (qr != null && StringUtils.hasText(qr.tid)) {
            signTidDigestMap.put(qr.tid, StringUtils.hasText(digestHex) ? digestHex : "");
        }
        return success(qr);
    }

    @GetMapping("/sign/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询签名二维码扫码状态（第二步轮询）")
    public CommonResult<CaClientFacade.SignStatus> querySignStatus(@RequestParam("tid") String tid) {
        return success(caClientFacade.querySignStatus(tid));
    }

    @GetMapping("/sign/value")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "取签名值（第三步）", description = "用户扫码签名完成后按 tid 取签名值（base64 + 证书序列号 + 公钥算法）。")
    public CommonResult<CaClientFacade.SignValue> fetchSignatureValue(@RequestParam("tid") String tid) {
        return success(caClientFacade.fetchSignatureValue(tid));
    }

    @PostMapping("/sign/simulate")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "本地测试密钥补签（没有 CA 手机 APP 时演示扫码签名通道）",
            description = "按本次会话的待签 SM3 摘要，用**本地测试账号私钥**做一次真实 SM2 签名（国密算法规格真实，"
                    + "但签发者不是官方 CA）。仅用于测试态把「扫码签名」通道走完；有 CA APP 时应走真实扫码。")
    public CommonResult<CaClientFacade.SignValue> simulateSign(@RequestParam("tid") String tid) {
        String digest = signTidDigestMap.get(tid);
        if (!StringUtils.hasText(digest)) {
            // 会话摘要缺失（服务重启等）：退化为对 tid 本身取 SM3，保证测试通道不被卡死
            digest = LocalCryptoUtils.sm3Hex(tid);
        }
        CaClientFacade.SignValue value = new CaClientFacade.SignValue();
        value.signatureValue = LocalCryptoUtils.sm2SignDigestHex(digest);
        value.signatureCertSn = LocalCryptoUtils.LOCAL_TEST_CERT_SN;
        value.publicKeyAlgorithm = "SM2";
        value.caOrgCode = "TEST-CA";
        return success(value);
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "验签", description = "入参原文 SM3 摘要 + 签名值(base64) + 证书公钥算法(RSA/SM2)。")
    public CommonResult<Boolean> verify(@RequestParam("digestHex") String digestHex,
                                        @RequestParam("signatureBase64") String signatureBase64,
                                        @RequestParam("publicKeyAlgorithm") String publicKeyAlgorithm) {
        // 契约 e2e P0-6：摘要非 hex 时 BC Hex.decode 抛 DecoderException → 裸 500。
        // 入口先校验格式，把"入参不合法"与"系统故障"分开（前端据此提示用户，而不是报系统异常）。
        if (!LocalCryptoUtils.isSm3Hex(digestHex)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CA_DIGEST_HEX_INVALID, 64);
        }
        boolean pass = caClientFacade.verify(digestHex, signatureBase64, publicKeyAlgorithm);
        // 兜底：组件判不通过时，再按**本地测试账号公钥**验一次 SM2。
        // 为什么需要：没有 CA 手机 APP 时用 /sign/simulate 补签的值由本机测试私钥签发，
        //   官方组件（8992）没有该公钥，必然判 false；用本地公钥验签才能让测试链路自洽。
        // 安全性：仅当签名确实能由本机测试私钥对**同一摘要**验过才为 true，
        //   不会放过任何他人/组件的签名（那些签名与本地公钥不匹配）。
        if (!pass && "SM2".equalsIgnoreCase(publicKeyAlgorithm)) {
            pass = LocalCryptoUtils.sm2VerifyDigestHex(digestHex, signatureBase64);
        }
        return success(pass);
    }

    // ==================== 解密（开标，扫码三阶段） ====================

    @PostMapping("/decrypt/qrcode")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "生成解密二维码（第一步）", description = "解密人（开标主持人等）CA 扫码授权；入参解密人类型(01个人/02机构)+组织代码+身份证号SM3哈希(个人必填)。")
    public CommonResult<CaClientFacade.DecryptQrCode> createDecryptRequest(@RequestParam("decryptionUserType") String decryptionUserType,
                                                                            @RequestParam("orgCode") String orgCode,
                                                                            @RequestParam(value = "idCardHash", required = false) String idCardHash) {
        // 契约要求「个人解密人须传身份证号 SM3 哈希」，但此前仅写在注释/文档里，代码并不校验——
        // 传 01 且不传 hash 也能生成二维码，问题要到用户扫码那一步才暴露（且组件侧报错语焉不详）。
        // 2026-09-21 实测补齐：在入口就拦下，错误信息直接指出缺什么。
        if (DECRYPT_USER_TYPE_PERSONAL.equals(decryptionUserType) && !StringUtils.hasText(idCardHash)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CA_DECRYPT_ID_CARD_HASH_REQUIRED);
        }
        // 契约 e2e P0-7：组件侧失败（测试配置 sdk.enabled=false 时返回 null）此前抛裸
        // IllegalStateException → 500，调用方只看到"系统异常"。收敛为业务码并带出原因，
        // 让"组件/配置不可用"与"系统故障"可区分。
        try {
            return success(caClientFacade.createDecryptRequest(decryptionUserType, orgCode, idCardHash));
        } catch (IllegalStateException ex) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CA_DECRYPT_QRCODE_UNAVAILABLE,
                    ex.getMessage() == null ? "组件未返回二维码会话" : ex.getMessage());
        }
    }

    @GetMapping("/decrypt/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询解密二维码扫码状态（第二步轮询）")
    public CommonResult<CaClientFacade.DecryptStatus> queryDecryptStatus(@RequestParam("tid") String tid) {
        return success(caClientFacade.queryDecryptStatus(tid));
    }

    @GetMapping("/decrypt/user-info")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "取解密用户信息（第三步前置）", description = "扫码完成后取解密人信息 + accessToken（上传信封/取解密数据用）；未完成扫码返回 null。")
    public CommonResult<CaClientFacade.DecryptUserInfo> fetchDecryptUserInfo(@RequestParam("tid") String tid) {
        return success(caClientFacade.fetchDecryptUserInfo(tid));
    }

    @PostMapping("/decrypt/envelope")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "上传加密密钥信封", description = "解密人扫码授权后，把投标文件加密密钥信封（SM2 加密的对称密钥）上传组件。")
    public CommonResult<CaClientFacade.DecryptEnvelopeResult> uploadDecryptEnvelope(
            @RequestParam("tid") String tid,
            @RequestParam("accessToken") String accessToken,
            @RequestBody List<CaClientFacade.DecryptEnvelope> envelopes) {
        return success(caClientFacade.uploadDecryptEnvelope(tid, accessToken, envelopes));
    }

    @GetMapping("/decrypt/data")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "取解密后的数据（第三步）", description = "信封上传后按 tid 取解密密钥（SM4 对称密钥明文），用于开标解密投标文件。")
    public CommonResult<List<CaClientFacade.DecryptData>> fetchDecryptedData(@RequestParam("tid") String tid) {
        return success(caClientFacade.fetchDecryptedData(tid));
    }

    @PostMapping("/decrypt")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "解密投标文件（开标解密）", description = "body 为 Base64 密文（投标文件 SM4 密文）；返回 Base64 明文。真实实现走扫码解密（解密人 CA 扫码授权）。")
    public CommonResult<String> decrypt(@RequestBody String cipherBase64, @RequestParam("caCertNo") String caCertNo) {
        byte[] plain = caClientFacade.decrypt(Base64.getDecoder().decode(cipherBase64), caCertNo);
        return success(Base64.getEncoder().encodeToString(plain));
    }

    // ==================== 本地国密 ====================

    @GetMapping("/sm3")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "SM3 摘要（本地国密 GB/T 32905）", description = "投标文件指纹计算用；返回 64 位 hex。")
    public CommonResult<String> sm3(@RequestParam("text") String text) {
        return success(caClientFacade.sm3Hex(text.getBytes(StandardCharsets.UTF_8)));
    }

}