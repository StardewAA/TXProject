package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 评标明细（工程）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record EvalDetailVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PM_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PRICE_SCORE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUM_SCORE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RANKING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CANCEL_TBFILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CANCEL_TBFILE_DETAIL_SITUATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TBFILEURL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP

) {
}
