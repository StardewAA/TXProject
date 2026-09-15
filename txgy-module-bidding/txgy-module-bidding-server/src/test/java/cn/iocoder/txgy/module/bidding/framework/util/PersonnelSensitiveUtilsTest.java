package cn.iocoder.txgy.module.bidding.framework.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 员工敏感信息工具测试（盲索引 / 脱敏）。
 *
 * <p>依据：开发规范 §4.8（L4 只回掩码、日志禁明文）；《个人信息保护法》第二十八条（去标识化）。
 * <p>覆盖：盲索引确定性（同证号必同索引，一人一档判重的前提）、盐敏感（换盐即失效，防彩虹表）、
 * 大小写/空格归一（录入差异不应产生两个"同一人"）、脱敏后不可还原且长度合规。
 */
class PersonnelSensitiveUtilsTest {

    private static final String SALT = "unit-test-salt";
    private static final String ID_CARD = "610102199001011234";

    @Test
    void blindIndex_isDeterministic_forSameInput() {
        // 为什么断言确定性：一人一档的判重完全依赖"同证号→同索引"，若不确定则重复建档拦不住
        assertEquals(PersonnelSensitiveUtils.blindIndex(SALT, ID_CARD),
                PersonnelSensitiveUtils.blindIndex(SALT, ID_CARD));
        assertEquals(64, PersonnelSensitiveUtils.blindIndex(SALT, ID_CARD).length());
    }

    @Test
    void blindIndex_isSaltSensitive() {
        // 为什么断言盐敏感：裸哈希可被彩虹表反查身份证，加盐是安全底线
        assertNotEquals(PersonnelSensitiveUtils.blindIndex(SALT, ID_CARD),
                PersonnelSensitiveUtils.blindIndex(SALT + "-other", ID_CARD));
        // 未配置盐时退回开发默认盐，仍可稳定产出（生产必须配置，见工具类注释）
        assertNotEquals(PersonnelSensitiveUtils.blindIndex(SALT, ID_CARD),
                PersonnelSensitiveUtils.blindIndex("", ID_CARD));
    }

    @Test
    void blindIndex_normalizesCaseAndSpaces() {
        // 为什么归一化：录入时大小写/空格差异不应被当成两个人（用户侧最常见的重复建档原因）
        String upper = PersonnelSensitiveUtils.blindIndex(SALT, "61010219900101123X");
        String lower = PersonnelSensitiveUtils.blindIndex(SALT, " 61010219900101123x ");
        assertEquals(upper, lower);
    }

    @Test
    void blindIndex_returnsNull_onBlank() {
        assertNull(PersonnelSensitiveUtils.blindIndex(SALT, null));
        assertNull(PersonnelSensitiveUtils.blindIndex(SALT, "  "));
    }

    @Test
    void maskIdCard_keepsHead6Tail4() {
        String masked = PersonnelSensitiveUtils.maskIdCard(ID_CARD);
        assertEquals("610102********1234", masked);
        assertTrue(masked.contains("*"));
    }

    @Test
    void maskMobile_keepsHead3Tail4() {
        assertEquals("138****0000", PersonnelSensitiveUtils.maskMobile("13800000000"));
        assertNull(PersonnelSensitiveUtils.maskMobile(null));
    }

    @Test
    void maskName_keepsSurname_andHandlesCompoundSurname() {
        assertEquals("张*", PersonnelSensitiveUtils.maskName("张三"));
        assertEquals("张**", PersonnelSensitiveUtils.maskName("张三丰"));
        // 复姓保留两字，避免"欧阳*"被误当成"欧*"
        assertEquals("欧阳*", PersonnelSensitiveUtils.maskName("欧阳锋"));
        assertEquals("李", PersonnelSensitiveUtils.maskName("李"));
    }
}
