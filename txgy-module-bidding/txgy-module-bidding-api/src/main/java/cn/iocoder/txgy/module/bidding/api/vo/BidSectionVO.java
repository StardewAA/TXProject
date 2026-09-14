package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 标段（包）数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record BidSectionVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_NOMEET_BIDDING,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_ECTION_NUM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_TENDER_MODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRA_PLACE_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_SECTION_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTRACT_RECKON_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CREATE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_WEB_BIDDING,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TIME_LIMIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REMARK,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CLASSIFY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_QUALIFICATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
