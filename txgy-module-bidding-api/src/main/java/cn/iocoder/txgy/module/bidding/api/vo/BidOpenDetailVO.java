package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 开标明细数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record BidOpenDetailVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_ORG_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_MANAGER,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IS_COMMIT_MARGIN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BZJ_MONEY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MARGIN_PAY_FORM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_CURRENCY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OTHER_BID_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TIME_LIMIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHECKIN_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALITY_DEMAND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TBFILE_SEAL_SITUATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_OPEN_BID_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MAC_ADDRESS_OF_BIDDER,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_S_IP_ADDRESS // 规范原文带单引号，Java 不认，换成下划线,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String macaddress,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String Createguid,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IsJieMi,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JMip,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_DUTY_Name,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_DUTY_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDER_DUTY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TBFILE_DOWN_URL,
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
