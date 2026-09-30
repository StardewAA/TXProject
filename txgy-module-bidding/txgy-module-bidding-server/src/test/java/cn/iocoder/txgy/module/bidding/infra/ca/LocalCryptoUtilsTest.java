package cn.iocoder.txgy.module.bidding.infra.ca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地国密工具测试（SM3 指纹 = 国密 GB/T 32905，用户裁定；SM4 回环；校验函数）。
 * 本地可跑，不依赖远程 CA 组件/DB。
 */
class LocalCryptoUtilsTest {

    /** SM3 标准测试向量："abc" → 66c7f0f4...（GB/T 32905 附录 A） */
    private static final String SM3_ABC = "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0";

    @Test
    void sm3_knownVectorAbc() {
        String hash = LocalCryptoUtils.sm3Hex("abc");
        assertEquals(SM3_ABC, hash, "SM3(\"abc\") 必须等于国标测试向量");
    }

    @Test
    void sm3_emptyString() {
        // SM3("") = 1ab21d8355cfa17f8e61194831e81a8f22bec8c728fefb747ed035eb5082aa2b（国标向量，仅断言 64 位 hex）
        String hash = LocalCryptoUtils.sm3Hex("");
        assertTrue(LocalCryptoUtils.isSm3Hex(hash), "空串摘要必须是 64 位 hex");
        assertEquals(64, hash.length());
    }

    @Test
    void sm3_sameInputSameHash() {
        String a = LocalCryptoUtils.sm3Hex("招标投标文件内容-加密递交");
        String b = LocalCryptoUtils.sm3Hex("招标投标文件内容-加密递交");
        assertEquals(a, b);
        // 不同内容摘要不同
        assertNotEquals(a, LocalCryptoUtils.sm3Hex("招标投标文件内容-加密递交-改"));
    }

    @Test
    void sm4_encryptDecryptRoundTrip() {
        String keyHex = "0123456789abcdeffedcba9876543210"; // 16 字节
        String ivHex = "00000000000000000000000000000000";  // 16 字节
        String plain = "投标文件SM4加密测试内容";
        String cipherHex = LocalCryptoUtils.sm4EncryptHex(plain, keyHex, ivHex);
        assertNotNull(cipherHex);
        assertNotEquals(plain, cipherHex);
        String decrypted = LocalCryptoUtils.sm4DecryptHex(cipherHex, keyHex, ivHex);
        assertEquals(plain, decrypted, "SM4-CBC 加密-解密必须回环一致");
    }

    @Test
    void sm3Jdk_matchesDirect() throws Exception {
        byte[] data = "国密SM3双实现对照".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(LocalCryptoUtils.sm3Hex(data), LocalCryptoUtils.sm3HexJdk(data));
    }

    @Test
    void isSm3Hex_validatesLengthAndHex() {
        assertTrue(LocalCryptoUtils.isSm3Hex(SM3_ABC));
        assertTrue(LocalCryptoUtils.isSm3Hex(SM3_ABC.toUpperCase()));
        assertFalse(LocalCryptoUtils.isSm3Hex(null));
        assertFalse(LocalCryptoUtils.isSm3Hex(""));
        assertFalse(LocalCryptoUtils.isSm3Hex("zzzz" + SM3_ABC.substring(4)));
        assertFalse(LocalCryptoUtils.isSm3Hex(SM3_ABC.substring(0, 63)));
    }

    // ==================== SM2 真实签名（统一测试账号，用户裁定：本地测试也须真实签名） ====================

    @Test
    void sm2_signAndVerifyRoundTrip() {
        byte[] content = "投标文件内容-本地真实签章".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = LocalCryptoUtils.sm3Hex(content);
        // 对 SM3 摘要签名（与 SDK「对摘要签名」口径一致）→ 必须可自洽验签
        String sig = LocalCryptoUtils.sm2SignDigestHex(digest);
        assertNotNull(sig);
        assertTrue(LocalCryptoUtils.sm2VerifyDigestHex(digest, sig), "真实 SM2 签名必须能用统一测试公钥验签");
        // 篡改摘要后验签必须失败
        assertFalse(LocalCryptoUtils.sm2VerifyDigestHex(LocalCryptoUtils.sm3Hex("被篡改的内容"), sig));
        // 空/非法签名
        assertFalse(LocalCryptoUtils.sm2VerifyDigestHex(digest, null));
        assertFalse(LocalCryptoUtils.sm2VerifyDigestHex(digest, "SIG:MOCK"));
    }

    @Test
    void sm2_publicKeyPemPresent() {
        String pem = LocalCryptoUtils.localTestPublicKeyPem();
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"), "统一测试公钥必须可导出 PEM");
        assertTrue(pem.endsWith("-----END PUBLIC KEY-----"));
    }

    @Test
    void sm2_unifiedAccountConstants() {
        assertEquals("SN-LOCAL-CA-TEST-0001", LocalCryptoUtils.LOCAL_TEST_CERT_SN);
        assertNotNull(LocalCryptoUtils.LOCAL_TEST_ORG_NAME);
        assertNotNull(LocalCryptoUtils.LOCAL_TEST_ORG_CODE);
    }
}
