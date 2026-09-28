package cn.iocoder.txgy.module.bidding.service.certificate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CertHashChain 金标向量测试 —— 防止「写入方/回验方公式漂移」再次发生。
 *
 * <p>为什么必须有：2026-09-29 定位到历史缺陷 —— 写入方把 null 预归一成空串，而 recordHash 直接拼接把
 * null 变成字面量 null，同一公式两处各写一半，导致回验永远对不上（实测全部证照首行即断链），
 * 把「链到底有没有被改」这个真问题掩盖了。
 * <p>这些向量一旦变化，说明公式被人改过 —— 必须同时提供重新基线方案，否则已有链会静默失效。
 */
class CertHashChainTest {

    /** 向量 1：CREATE 建档行（oldValue / opIp / batchId 全为 null → 归一为空串）。对应库中真实行。 */
    @Test
    void recordHash_createRow_nullsAsEmpty() {
        assertEquals("1063b6c90c3c7e872a9ad213bd34bfa3a0021e3f394b604058ad4b6fe8603cc8",
                CertHashChain.recordHash(20L, "cert_no", null, "91330100CAS22960",
                        "CREATE", "MANUAL", null, null, CertHashChain.HASH_CHAIN_ROOT));
    }

    /** 向量 2：UPDATE 行（含中文值与非空 opIp/batchId）。 */
    @Test
    void recordHash_updateRow_withIpAndBatch() {
        assertEquals("ef7b105a7565cbdf50a2ae8590b1c4c641b62153b419a37aef6a2e086c412473",
                CertHashChain.recordHash(7L, "holder_name", "旧公司", "新公司",
                        "UPDATE", "MANUAL", "10.0.0.1", "B2026", "abc"));
    }

    /** 归一自证：null 与空串必须产生同一哈希（这正是历史漂移点，必须锁死）。 */
    @Test
    void recordHash_nullAndEmptyAreEquivalent() {
        assertEquals(
                CertHashChain.recordHash(1L, "f", null, "v", "UPDATE", "MANUAL", null, null, "ROOT"),
                CertHashChain.recordHash(1L, "f", "", "v", "UPDATE", "MANUAL", "", "", "ROOT"));
    }
}