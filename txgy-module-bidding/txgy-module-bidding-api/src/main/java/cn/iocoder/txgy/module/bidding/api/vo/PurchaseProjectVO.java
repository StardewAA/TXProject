package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 采购项目数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PurchaseProjectVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RELATE_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String INVEST_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_TRADE_CLASSIFY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_ROLE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASER_CONTACTER,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASER_INFORMATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASER_ADRESS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROGRAM_BUDGET,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUDGET_CURRENCY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String UNIT_BUDGET,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_MODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_AGENCY_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_AGENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_AGENCY_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_AGENCY_CONNECTOR,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASER_AGENCY_INFORMATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRA_PLACE_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CREATE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String WEITUO_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IS_PROJECT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASER_ORGANIZE_FORM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
