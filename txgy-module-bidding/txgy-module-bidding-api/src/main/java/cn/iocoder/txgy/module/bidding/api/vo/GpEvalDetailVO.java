package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 评标明细（政采）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record GpEvalDetailVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CANCEL_TBFILE_BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CANCEL_TBFILE_BIDDER_LEGALCODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_CONTACTER,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_CONTACTER_PHONE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ENROLL_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_FAIL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CANCEL_TBFILE_BIDDER_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PRICE_SCORE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SYNTHESIZE_SCORE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUM_SCORE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RANKING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
