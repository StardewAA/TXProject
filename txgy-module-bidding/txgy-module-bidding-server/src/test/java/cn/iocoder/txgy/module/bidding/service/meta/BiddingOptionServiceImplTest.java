package cn.iocoder.txgy.module.bidding.service.meta;

import cn.iocoder.txgy.module.bidding.enums.BiddingOptionTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BiddingOptionServiceImpl} 纯函数单测（不依赖 DB）。
 *
 * <p>为什么只测纯函数：这层的业务风险集中在**判定口径**（哪些状态算"不可选"、上限怎么截、名称全空怎么办），
 * 这些一旦错了会静默影响所有下拉（比如把离职员工放进配证候选池，或反过来把可用证照全禁用）。
 * 数据查询本身由 MyBatis-Plus 与 TenantBaseDO 拦截器保证，另由接口实测覆盖。
 */
class BiddingOptionServiceImplTest {

    @Test
    void parse_shouldIgnoreCaseAndSurroundingBlank() {
        assertSame(BiddingOptionTypeEnum.SUBJECT, BiddingOptionTypeEnum.parse("SUBJECT"));
        assertSame(BiddingOptionTypeEnum.CERTIFICATE, BiddingOptionTypeEnum.parse(" certificate "));
        assertSame(BiddingOptionTypeEnum.SAFETY_PERMIT, BiddingOptionTypeEnum.parse("safety_permit"));
    }

    @Test
    void parse_shouldReturnNullForUnknownOrBlank() {
        // FILE 故意不属于候选类型（fileId 必须来自上传响应，见契约 §1）
        assertNull(BiddingOptionTypeEnum.parse("FILE"));
        assertNull(BiddingOptionTypeEnum.parse(""));
        assertNull(BiddingOptionTypeEnum.parse("   "));
        assertNull(BiddingOptionTypeEnum.parse(null));
    }

    @Test
    void codes_shouldListAllSixTypes() {
        // 2026-09-25 merge 新增 SUBJECT_COMPANY（主体公司类型下拉）后，候选类型由 6 个变 7 个。
        assertEquals("SUBJECT/SUBJECT_COMPANY/PERSONNEL/PROJECT/CERTIFICATE/QUALIFICATION/SAFETY_PERMIT/PERSONNEL_CERT",
                BiddingOptionTypeEnum.codes());
    }

    @Test
    void normalizeLimit_shouldDefaultAndClamp() {
        assertEquals(20, BiddingOptionServiceImpl.normalizeLimit(null));
        assertEquals(20, BiddingOptionServiceImpl.normalizeLimit(0));
        assertEquals(20, BiddingOptionServiceImpl.normalizeLimit(-5));
        assertEquals(5, BiddingOptionServiceImpl.normalizeLimit(5));
        assertEquals(50, BiddingOptionServiceImpl.normalizeLimit(50));
        assertEquals(50, BiddingOptionServiceImpl.normalizeLimit(500));
    }

    @Test
    void firstNonBlank_shouldFallBackToUnnamed() {
        assertEquals("员工甲", BiddingOptionServiceImpl.firstNonBlank(null, "  ", " 员工甲 "));
        assertEquals(BiddingOptionServiceImpl.UNNAMED, BiddingOptionServiceImpl.firstNonBlank(null, " "));
        assertEquals(BiddingOptionServiceImpl.UNNAMED, BiddingOptionServiceImpl.firstNonBlank());
    }

    @Test
    void extraOf_shouldJoinNonBlankAndReturnNullWhenEmpty() {
        assertEquals("A · B", BiddingOptionServiceImpl.extraOf("A", null, " B "));
        assertNull(BiddingOptionServiceImpl.extraOf(null, "  "));
        assertNull(BiddingOptionServiceImpl.extraOf());
    }

    @Test
    void notUsable_shouldOnlyMarkExplicitlyBadStatus() {
        Set<String> usable = Set.of("ACTIVE", "EXPIRING_SOON");
        // 状态为空=存量/未评估：不能因为"没填"就把可用数据挡在外面
        assertFalse(BiddingOptionServiceImpl.notUsable(null, usable));
        assertFalse(BiddingOptionServiceImpl.notUsable("  ", usable));
        assertFalse(BiddingOptionServiceImpl.notUsable("ACTIVE", usable));
        assertFalse(BiddingOptionServiceImpl.notUsable("active", usable));
        assertTrue(BiddingOptionServiceImpl.notUsable("EXPIRED", usable));
        assertTrue(BiddingOptionServiceImpl.notUsable("REVOKED", usable));
    }

    @Test
    void personNotUsable_shouldMirrorPersonnelStatusEnum() {
        assertFalse(BiddingOptionServiceImpl.personNotUsable("ACTIVE"));   // 在职=唯一可用
        assertFalse(BiddingOptionServiceImpl.personNotUsable(null));       // 未填不拦
        assertTrue(BiddingOptionServiceImpl.personNotUsable("LEAVE"));     // 离职退出配证候选池
        assertTrue(BiddingOptionServiceImpl.personNotUsable("SUSPENDED"));
        assertTrue(BiddingOptionServiceImpl.personNotUsable("RETIRED"));
        assertTrue(BiddingOptionServiceImpl.personNotUsable("NOT_A_STATUS")); // 字典外保守禁用
    }
}
