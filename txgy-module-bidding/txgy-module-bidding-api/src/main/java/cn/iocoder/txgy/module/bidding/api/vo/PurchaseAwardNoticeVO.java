package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 中标（成交）结果公告数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PurchaseAwardNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BID_BULLETIN_TITLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BID_BULLETIN_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BID_BULLETIN_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String URL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WINNINGBIDDER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WINNINGBIDDER_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WINNINGBIDDER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BID_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BID_RATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IS_SEVERAL_DEAL_BIDDERS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DEAL_INSTRUCTION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISH_MEDIA,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DEAL_BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DEAL_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DEAL_PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_START_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_INSTRUCTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CREATOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
