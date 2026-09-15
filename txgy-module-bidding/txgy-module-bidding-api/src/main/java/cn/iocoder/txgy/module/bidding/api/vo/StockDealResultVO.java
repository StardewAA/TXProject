package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 股权类产权交易结果信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record StockDealResultVO(

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
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EXCHANGE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OBJECT_EVALUATE_VALUE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRADE_VALUE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRADE_DATE,
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
