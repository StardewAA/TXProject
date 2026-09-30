package cn.iocoder.txgy.module.bidding.infra.ca;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Base64;

/**
 * 本地国密工具（SM2/SM3/SM4，国密 GB/T 32918/32905/32907）。
 *
 * <p>定位：外部 CA SDK（cebpubservice）的验签/认证走远程共享组件；但**指纹与对称加解密可以在本地完成**——
 * ① 投标文件 SM3 指纹（document_hash，用户裁定指纹 = SM3）：本地对文件/字节流计算，不依赖远程；
 * ② SM4 对称加解密：投标文件加密递交 / 开标解密前的本地处理（密钥信封由 CA 平台托管，本地只管算法）；
 * ③ SM2 密钥对：本地生成测试密钥（正式环境证书/私钥由 CA 机构签发，本类仅测试与占位阶段用）。
 *
 * <p>实现：基于 BouncyCastle（bcprov-jdk18on，项目已显式依赖 1.84），不依赖外部 SDK。
 */
public final class LocalCryptoUtils {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private LocalCryptoUtils() {
    }

    // ==================== SM3 指纹（国密 GB/T 32905） ====================

    /**
     * 计算字节数据的 SM3 摘要（64 位小写十六进制，即 GB/T 32905 指纹）。
     */
    public static String sm3Hex(byte[] data) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return HexUtil.encodeHexStr(out);
    }

    /**
     * 计算字符串的 SM3 摘要（UTF-8 编码后）。
     */
    public static String sm3Hex(String text) {
        return sm3Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 使用 JDK MessageDigest + BC Provider 计算 SM3（与 {@link #sm3Hex(byte[])} 等价，留作对照）。
     */
    public static String sm3HexJdk(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME);
        return HexUtil.encodeHexStr(digest.digest(data));
    }

    // ==================== SM4 对称加解密（国密 GB/T 32907） ====================

    /**
     * SM4-CBC 加密。
     *
     * @param data    明文
     * @param keyBytes 密钥（16 字节）
     * @param ivBytes  初始向量（16 字节）
     * @return 密文
     */
    public static byte[] sm4Encrypt(byte[] data, byte[] keyBytes, byte[] ivBytes) {
        return sm4(data, keyBytes, ivBytes, true);
    }

    /**
     * SM4-CBC 解密。
     */
    public static byte[] sm4Decrypt(byte[] data, byte[] keyBytes, byte[] ivBytes) {
        return sm4(data, keyBytes, ivBytes, false);
    }

    private static byte[] sm4(byte[] data, byte[] keyBytes, byte[] ivBytes, boolean encrypt) {
        CBCBlockCipher cipher = new CBCBlockCipher(new SM4Engine());
        ParametersWithIV params = new ParametersWithIV(new KeyParameter(keyBytes), ivBytes);
        cipher.init(encrypt, params);
        int blockSize = cipher.getBlockSize();
        if (encrypt) {
            // 标准 PKCS7：总是补一块（pad 1..16），保证可逆
            int pad = blockSize - (data.length % blockSize);
            byte[] padded = new byte[data.length + pad];
            System.arraycopy(data, 0, padded, 0, data.length);
            for (int i = data.length; i < padded.length; i++) {
                padded[i] = (byte) pad;
            }
            data = padded;
        }
        int outLen = data.length;
        byte[] out = new byte[outLen];
        byte[] block = new byte[blockSize];
        for (int i = 0; i < data.length; i += blockSize) {
            System.arraycopy(data, i, block, 0, blockSize);
            cipher.processBlock(block, 0, out, i);
        }
        if (!encrypt) {
            // PKCS7 去填充：末字节即 pad 值（1..16）
            int pad = out[outLen - 1] & 0xFF;
            if (pad > 0 && pad <= blockSize) {
                int newLen = outLen - pad;
                byte[] trimmed = new byte[newLen];
                System.arraycopy(out, 0, trimmed, 0, newLen);
                return trimmed;
            }
        }
        return out;
    }

    /**
     * 便捷：16 字节密钥 + 16 字节 IV 的 SM4-CBC 加密，输出 hex（测试/演示用）。
     */
    public static String sm4EncryptHex(String plainText, String keyHex, String ivHex) {
        byte[] key = Hex.decode(keyHex);
        byte[] iv = Hex.decode(ivHex);
        return HexUtil.encodeHexStr(sm4Encrypt(plainText.getBytes(StandardCharsets.UTF_8), key, iv));
    }

    /**
     * 便捷：SM4-CBC 解密 hex 密文返回字符串（测试/演示用）。
     */
    public static String sm4DecryptHex(String cipherHex, String keyHex, String ivHex) {
        byte[] key = Hex.decode(keyHex);
        byte[] iv = Hex.decode(ivHex);
        byte[] plain = sm4Decrypt(HexUtil.decodeHex(cipherHex), key, iv);
        return new String(plain, StandardCharsets.UTF_8).replaceAll("\u0000+$", "");
    }

    /**
     * 判断字符串是否为合法的 SM3 指纹（64 位十六进制，小写或大写）。
     */
    public static boolean isSm3Hex(String hash) {
        if (StrUtil.isBlank(hash)) {
            return false;
        }
        String h = hash.trim();
        return h.length() == 64 && h.matches("[0-9a-fA-F]{64}");
    }

    // ==================== SM2 签名/验签（国密 GB/T 32918，本地统一测试账号） ====================
    // 定位：正式环境的证书/私钥由 CA 机构签发（平台侧不落私钥）；测试/占位阶段用本类内置的
    // 「统一测试账号」密钥对做**真实 SM2 签名**（可自洽验签），拉高联调完成度——签名是密码学
    // 真实产生的，仅证书未获官方认可（用户裁定：所有用户认证/登录/签名共用一个测试账号即可）。

    /** 统一测试账号 CA 证书序列号（本地真实签名；正式环境由 CA 机构签发） */
    public static final String LOCAL_TEST_CERT_SN = "SN-LOCAL-CA-TEST-0001";
    /** 统一测试账号单位名称 */
    public static final String LOCAL_TEST_ORG_NAME = "本地CA测试单位";
    /** 统一测试账号统一社会信用代码 */
    public static final String LOCAL_TEST_ORG_CODE = "91110000LOCALCA0001";

    /** 统一测试账号 SM2 私钥 d（固定值，跨重启稳定；仅测试用，勿用于生产） */
    private static final String LOCAL_TEST_PRIVATE_KEY_HEX =
            "0E6A1B2C3D4E5F60718293A4B5C6D7E8F9A0B1C2D3E4F5061728394A5B6C7D8E9";

    private static final ECDomainParameters SM2_DOMAIN = buildSm2Domain();
    private static final ECPrivateKeyParameters LOCAL_TEST_PRIVATE_KEY =
            new ECPrivateKeyParameters(new BigInteger(LOCAL_TEST_PRIVATE_KEY_HEX, 16), SM2_DOMAIN);
    private static final ECPublicKeyParameters LOCAL_TEST_PUBLIC_KEY =
            new ECPublicKeyParameters(SM2_DOMAIN.getG().multiply(LOCAL_TEST_PRIVATE_KEY.getD()).normalize(), SM2_DOMAIN);

    private static ECDomainParameters buildSm2Domain() {
        X9ECParameters x9 = GMNamedCurves.getByName("sm2p256v1");
        return new ECDomainParameters(x9.getCurve(), x9.getG(), x9.getN(), x9.getH());
    }

    /**
     * 统一测试账号公钥（SM2，X.509 PEM 格式；落库 ca_public_key 验签用）。
     */
    public static String localTestPublicKeyPem() {
        try {
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(LOCAL_TEST_PUBLIC_KEY);
            String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(spki.getEncoded());
            return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException("生成测试公钥 PEM 失败", e);
        }
    }

    /**
     * 统一测试账号 SM2 签名：对输入数据（如 SM3 摘要字节）做真实签名。
     *
     * @return 签名值（base64，DER 编码）
     */
    public static String sm2SignBase64(byte[] data) {
        SM2Signer signer = new SM2Signer();
        signer.init(true, LOCAL_TEST_PRIVATE_KEY);
        signer.update(data, 0, data.length);
        try {
            return Base64.getEncoder().encodeToString(signer.generateSignature());
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    /**
     * 统一测试账号 SM2 验签。
     *
     * @param data            原文（与签名时一致）
     * @param signatureBase64 签名值（base64，DER）
     */
    public static boolean sm2VerifyBase64(byte[] data, String signatureBase64) {
        if (StrUtil.isBlank(signatureBase64)) {
            return false;
        }
        try {
            SM2Signer signer = new SM2Signer();
            signer.init(false, LOCAL_TEST_PUBLIC_KEY);
            signer.update(data, 0, data.length);
            return signer.verifySignature(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 便捷：对 SM3 摘要（hex）做 SM2 签名，返回 base64（与 SDK「对摘要签名」口径一致）。
     */
    public static String sm2SignDigestHex(String digestHex) {
        return sm2SignBase64(Hex.decode(digestHex));
    }

    /**
     * 便捷：对 SM3 摘要（hex）验签。
     */
    public static boolean sm2VerifyDigestHex(String digestHex, String signatureBase64) {
        return sm2VerifyBase64(Hex.decode(digestHex), signatureBase64);
    }
}
