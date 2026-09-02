package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 设定竞价规则（产权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PropertyBiddingRuleVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_ORGANIZER_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHURANGDANWEI,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ORDER_BIDDING_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String STARTING_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_DURATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_UNIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_DELAY_PERIOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String STARTING_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PINGGUJIA,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BUCHANG_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BUCHANG,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_ALLOWING_STARTING_PRICE_BIDDING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_ALLOWING_HIGHEST_LOWEST_PRICE_BIDDING // 规范原文带斜杠，Java 不认，换成下划线,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BUCHANG_TYPE_17,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_INSTRUCTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
