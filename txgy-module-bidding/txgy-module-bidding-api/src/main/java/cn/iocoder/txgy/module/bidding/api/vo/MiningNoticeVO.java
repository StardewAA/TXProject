package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 交易公告（矿权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record MiningNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_PAY_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGSTARTDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OTHER_CONDITION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZIGE_CONDTION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IMPPRMANT_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_APP_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISHING_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IN_BUY_KIND,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IN_MINE_COME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QT_PLAN_YEARS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QT_TOTAL_AREA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_GEOGRAPHY_POSITION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_AREA_COORDINATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
