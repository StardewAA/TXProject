package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 开标明细——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PurchaseBidOpenDetailVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_IDCARD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_CONTACTER,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MARGIN_PAY_AMOUNT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_METHOD,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OTHER_BID_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_S_IP_ADDRESS, // 规范原文带单引号，Java 不认，换成下划线,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHECKIN_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PERSON_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PERSON_PHONE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DownLoadDate,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JMip,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String Createguid,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String macaddress,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IsJieMi,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
