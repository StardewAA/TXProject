package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 采购合同数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PurchaseContractVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTRACT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTRACT_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUPPLIER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUPPLIER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTRACT_AMOUNT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_BID_RATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTRACT_TERM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SIGNING_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
