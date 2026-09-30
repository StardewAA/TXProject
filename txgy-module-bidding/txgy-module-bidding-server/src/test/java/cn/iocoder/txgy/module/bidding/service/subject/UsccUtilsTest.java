package cn.iocoder.txgy.module.bidding.service.subject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一社会信用代码校验测试（GB 32100-2015）。
 *
 * <p>存在意义（2026-09-21 事故）：{@code UsccUtils} 原实现把「校验码 = 31 - (Σ mod 31)」错写成
 * 「校验码 = 字符集[Σ mod 31]」（少了「31 −」这一步），导致**合法代码全被拒**，只有反向凑出来的值能过；
 * 用户拿 GB 32100 官方示例 91350100M000100Y43 建档被拒才发现。此测试用真实/官方样例把这条钉死。
 */
class UsccUtilsTest {

    /** GB 32100-2015 官方示例 */
    private static final String OFFICIAL_SAMPLE = "91350100M000100Y43";

    @Test
    void isValid_officialSample() {
        assertTrue(UsccUtils.isValid(OFFICIAL_SAMPLE), "GB 32100 官方示例必须是合法代码");
    }

    @Test
    void isValid_realCodesFromPublicRegistry() {
        // 公开工商信息里的真实代码（各自校验位均可被标准公式复算）
        assertTrue(UsccUtils.isValid("914403001922038216"), "华为技术有限公司");
        assertTrue(UsccUtils.isValid("91110000802100433B"), "百度在线网络技术（北京）有限公司");
        assertTrue(UsccUtils.isValid("91330100799655058B"), "阿里巴巴（中国）有限公司");
    }

    @Test
    void isValid_lowercaseAccepted() {
        assertTrue(UsccUtils.isValid(OFFICIAL_SAMPLE.toLowerCase()), "小写应归一化后仍合法");
    }

    @Test
    void isInvalid_wrongCheckDigit() {
        // 同一前缀，校验位按标准应为 3：改成别的必须拒
        assertFalse(UsccUtils.isValid("91350100M000100Y44"), "换成 4");
        // 旧错误算法对该前缀算出的是 W，用它当校验位也必须拒 —— 这正是回归点
        assertFalse(UsccUtils.isValid("91350100M000100Y4W"), "旧 bug 的产物不得再被判为合法");
    }

    @Test
    void isInvalid_demoValueAdaptedToOldBug() {
        // 库里为迁就旧算法把演示档案改成 ...HJX（旧算法算出 X）；按标准它不合法（正确校验位是 2）
        assertFalse(UsccUtils.isValid("91610100MAM94DYHJX"));
    }

    @Test
    void isInvalid_formatProblems() {
        assertFalse(UsccUtils.isValid(null), "null");
        assertFalse(UsccUtils.isValid(""), "空串");
        assertFalse(UsccUtils.isValid("91350100M000100Y4"), "17 位");
        assertFalse(UsccUtils.isValid("91350100M000100Y433"), "19 位");
        assertFalse(UsccUtils.isValid("91350100M000100YI3"), "含 GB32100 字符集外的 I");
        assertFalse(UsccUtils.isValid("91350100M000100YO3"), "含 O");
        assertFalse(UsccUtils.isValid("91350100M000100YS3"), "含 S");
        assertFalse(UsccUtils.isValid("91350100M000100YV3"), "含 V");
        assertFalse(UsccUtils.isValid("91350100M000100YZ3"), "含 Z");
    }
}
