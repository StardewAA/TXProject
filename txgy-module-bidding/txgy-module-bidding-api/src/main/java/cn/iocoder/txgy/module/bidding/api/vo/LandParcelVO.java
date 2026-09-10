package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 地块信息——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record LandParcelVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_LOCATION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PLANNING_USAGE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEND_AGE_LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRADE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PLOT_RATIO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GREENING_RATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUILDING_DENSITY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUILDING_COEFFCLIENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUILDING_LIMITHIGHT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MONEY_TOUZI,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MONEY_DIJIA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDING_MARGIN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ISALLOWED_WAIZI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIST_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_INCREASE_RANGE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IS_ALLOWING_STARTING_PRICE_BIDDING,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JINGJIA_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PERFOMANCE_BOND_FORM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GONGGAOFROMDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GONGGAOENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGSTARTDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIST_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIST_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_PAY_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String FORMERYANSHI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String FORMERXUNWEN,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PAIMAI_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_CONDITION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SPECIAl_REMAEK,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_BZ,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTACT_PERSON,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTACT_NUMBER,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ADDRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
