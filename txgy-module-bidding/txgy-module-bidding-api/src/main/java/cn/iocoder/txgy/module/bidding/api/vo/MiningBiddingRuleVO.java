package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 设定竞价规则（矿权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record MiningBiddingRuleVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MINING_AREA_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MINING_AREA_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String YUDING_STARTDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String YUDING_ENDDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String START_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String INQUIRY_PERIOD_LENGTH,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_DELAY_PERIOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String STARTING_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_STEP_VALUE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECERVE_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_UNIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BINDDING_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_ALLOWING_STARTING_PRICE_BIDDING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
