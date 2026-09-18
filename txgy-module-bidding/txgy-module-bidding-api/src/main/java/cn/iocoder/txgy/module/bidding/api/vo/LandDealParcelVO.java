package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 成交宗地信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record LandDealParcelVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DEAL_LAND_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_TITLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISHING_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_CONNECT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHENGJIA_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZHONGBIAO_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DEAL_ANNOUNCEMENT_GUID,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RELATE_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String INVEST_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_AREA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_USAGE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEND_AGE_LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_POSITION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DEAL_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LENDING_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LENDING_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LENDING_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_BZ,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
