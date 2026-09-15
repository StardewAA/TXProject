package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 新增矿区（矿权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record MiningAreaVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRADE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_ARA_LOCATION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JIAOYITYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SERVICE_AGE_LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_AREAS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_MINERALS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CLIENT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ENTRUSTING_ORGAN_AND_DOCUMENT_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINNG_GIGHT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINNG_GIGHT_FROM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINNG_GIGHT_TO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_MODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZITYUAN_RESERVES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGSTARTDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIST_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIST_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_PAY_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JINGJIA_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JIAONAFANGSHI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIST_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_INCREASE_RANGE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String INQUIRY_PERIOD_LENGTH,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IS_SETLOWPRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JINGJIA_FANGSHI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JINGJIA_DANWEI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IS_ALLOWSTARTPRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String YANSHI_LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDING_DELAY_PERIOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REMARK,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTACTOR_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTACT_TEL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String COORDINATE_SEQUENCE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHURANG_DANWEI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHURANG_ADDRESS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEND_AGE_LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROSPECTION_MINERALS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_KUANGCHANKAIFALIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_TUDIKAIFALIMIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QITA_REMARK,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LISK_REMARK,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_APP_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISHING_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_APPLY_PERSON,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPLY_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPLY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPLY_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_PERAMBULATE_COMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_GOV_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IN_MINE_COME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DT_RECEIVE_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
