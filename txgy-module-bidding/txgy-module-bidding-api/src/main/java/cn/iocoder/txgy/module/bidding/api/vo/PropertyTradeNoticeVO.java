package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 交易公告（产权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PropertyTradeNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAMES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_TITLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GONGGAOFROMDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GONGGAOENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGSTARTDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BAOMINGENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZHIFUTYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRANSFER_NOTES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SHOURANG_ZIGE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IMPORTANT_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
