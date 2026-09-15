package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 中标结果公示数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record WinResultPublicityVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_MEDIA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_NATURE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BIDDER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_AMOUNT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CURRENCY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OTHER_BID_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TIME_LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUALITY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_INSTRUCTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CHANGE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHANGE_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String UNION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String UNION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BULLETIN_DUTY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
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
