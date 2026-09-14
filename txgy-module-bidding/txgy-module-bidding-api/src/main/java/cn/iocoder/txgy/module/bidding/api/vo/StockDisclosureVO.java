package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 股权类产权交易挂牌披露信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record StockDisclosureVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PROJECT_CLASSIFY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String AUTHORIZE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TOTAL_PERCENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISH_DURATION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISH_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String POST_PONE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRE_EXCHANGE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MANAGEMENT_INTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GIVEUP_PRIORITY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IMPORTANT_INFO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUYER_POSTULATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OTHER_CONDITIONS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
