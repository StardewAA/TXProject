package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 中标（结果）通知书——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record WinNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BIDDER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_AMOUNT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CURRENCY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CHANGE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_WIN_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TIME_LIMIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALITY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BULLETIN_DUTY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP

) {
}
