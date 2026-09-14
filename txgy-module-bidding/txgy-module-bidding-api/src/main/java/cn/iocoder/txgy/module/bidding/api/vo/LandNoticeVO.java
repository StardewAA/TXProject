package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 交易公告（土地）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record LandNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_TITLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_DEADLINE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CREATE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_MEANWHILE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_CONNECT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_DISTRICT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String URL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String END_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISHING_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUPPLY_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHANGE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RETREAT_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String FIELD_NUM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LISTING_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LISTING_DEADLINE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LISTING_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ANNOUNCEMENT_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LIAISON_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String UNIT_ADDRESS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZIP_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTACT_PERSON,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTACT_NUMBER,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EMAIL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CANCEL_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
