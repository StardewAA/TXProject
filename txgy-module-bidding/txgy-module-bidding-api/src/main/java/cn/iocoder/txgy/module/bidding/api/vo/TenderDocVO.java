package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 招标文件/答疑澄清文件——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record TenderDocVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FILENO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DOC_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String VALID_PERIOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_MARGIN,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_PAY_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_AMOUNT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_CURRENCY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_AMOUNT_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_PERCENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EVALUATING_METHOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DOC_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CLARIFY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CLARIFY_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CHANGE_BID_OPENING_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NEW_BID_OPENING_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CLARIFY_NUM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTROL_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTROL_PRICE_CURRENCY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTROL_PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CLARIFY_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBMIT_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT

) {
}
