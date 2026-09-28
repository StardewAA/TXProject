package cn.iocoder.txgy.module.bidding.service.biznum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BiddingBizNoGeneratorImpl} 纯函数单测（不依赖 DB）。
 *
 * <p>为什么只测纯函数：编号规则一旦错，会污染所有内部对象的对外标识且难以事后修复
 * （公告/回执/项目/招标项目/标段一旦带上错误前缀或位数，追溯与报送都会对不上）。
 * 规则拼装（前缀/区划/年度/36进制序列/越界/层级追加）全部在此锁定；DB 原子递增由接口实测覆盖。
 */
class BiddingBizNoGeneratorImplTest {

    @Test
    void normalizeRegion_shouldDefaultWhenAbsentOrIllegal() {
        assertEquals(BiddingBizNoGeneratorImpl.DEFAULT_REGION, BiddingBizNoGeneratorImpl.normalizeRegion(null));
        assertEquals(BiddingBizNoGeneratorImpl.DEFAULT_REGION, BiddingBizNoGeneratorImpl.normalizeRegion("  "));
        assertEquals("610100", BiddingBizNoGeneratorImpl.normalizeRegion(" 610100 "));
        assertEquals(BiddingBizNoGeneratorImpl.DEFAULT_REGION, BiddingBizNoGeneratorImpl.normalizeRegion("61"));
        assertEquals(BiddingBizNoGeneratorImpl.DEFAULT_REGION, BiddingBizNoGeneratorImpl.normalizeRegion("61a100"));
    }

    @Test
    void buildNo_shouldComposeSeventeenDigitsWithZeroPaddedSeq() {
        // 前缀B + 区划610000 + 年度 + 36进制序列（1 -> 000001）
        String no = BiddingBizNoGeneratorImpl.buildNo("B", "610000", "2026", 1L);
        assertEquals(17, no.length());
        assertEquals("B6100002026000001", no);
    }

    @Test
    void buildNo_shouldUseBase36ForHighSeq() {
        // 36 进制：35 -> "z"；36 -> "10"
        assertEquals("B610000202600000z", BiddingBizNoGeneratorImpl.buildNo("B", "610000", "2026", 35L));
        assertEquals("B6100002026000010", BiddingBizNoGeneratorImpl.buildNo("B", "610000", "2026", 36L));
    }

    @Test
    void buildNo_shouldRejectSeqOutOfRange() {
        assertThrows(Exception.class, () -> BiddingBizNoGeneratorImpl.buildNo("B", "610000", "2026", 0L));
        assertThrows(Exception.class, () -> BiddingBizNoGeneratorImpl.buildNo("B", "610000", "2026", BiddingBizNoGeneratorImpl.MAX_SEQ + 1));
    }

    @Test
    void generate_shouldKeepPrefixPerType() {
        assertEquals("B", BiddingBizNoTypeEnum.NOTICE.getPrefix());
        assertEquals("F", BiddingBizNoTypeEnum.FILING.getPrefix());
        assertEquals("I", BiddingBizNoTypeEnum.PROJECT.getPrefix());
    }

    @Test
    void buildChildNo_shouldAppendThreeDigitSeqToParent() {
        // 招标项目编号：17 位父编号 + 3 位序列 = 20 位
        String tp = BiddingBizNoGeneratorImpl.buildChildNo("I6101002026000001", 1L);
        assertEquals(20, tp.length());
        assertEquals("I6101002026000001001", tp);
        // 标段编号：20 位父编号 + 3 位序列 = 23 位
        String bs = BiddingBizNoGeneratorImpl.buildChildNo(tp, 1L);
        assertEquals(23, bs.length());
        assertEquals("I6101002026000001001001", bs);
    }

    @Test
    void buildChildNo_shouldUseBase36WithZeroPadding() {
        // 3 位 36 进制：35 -> "00z"；36 -> "010"
        assertEquals("I610100202600000100z", BiddingBizNoGeneratorImpl.buildChildNo("I6101002026000001", 35L));
        assertEquals("I6101002026000001010", BiddingBizNoGeneratorImpl.buildChildNo("I6101002026000001", 36L));
    }

    @Test
    void buildChildNo_shouldRejectSeqOutOfRange() {
        assertThrows(Exception.class, () -> BiddingBizNoGeneratorImpl.buildChildNo("P", 0L));
        assertThrows(Exception.class, () -> BiddingBizNoGeneratorImpl.buildChildNo("P", BiddingBizNoGeneratorImpl.MAX_CHILD_SEQ + 1));
    }

    @Test
    void enum_shouldMarkHierarchicalTypes() {
        assertTrue(!BiddingBizNoTypeEnum.PROJECT.isChild());
        assertTrue(BiddingBizNoTypeEnum.TENDER_PROJECT.isChild());
        assertTrue(BiddingBizNoTypeEnum.BID_SECTION.isChild());
    }
}
