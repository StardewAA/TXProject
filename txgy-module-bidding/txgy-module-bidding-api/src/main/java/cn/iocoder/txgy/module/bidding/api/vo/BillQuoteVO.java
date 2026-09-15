package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 清单报价——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record BillQuoteVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String Bill_No,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUANTITY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SYNTHETICAL_UNIT_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SYNTHETICAL_TOTAL_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIT_PROJECT_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIT_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_NAME

) {
}
