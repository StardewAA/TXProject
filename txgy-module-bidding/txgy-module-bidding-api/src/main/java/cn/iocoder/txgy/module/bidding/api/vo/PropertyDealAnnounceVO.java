package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 成交公告——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PropertyDealAnnounceVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_URL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_NOTICE_START_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WEN_NOTICE_END_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JIOAYIFANGSHI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GUAPAIPRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PINGGUJIA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GONGGAOCONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
