package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 变更公告（工程）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record TenderChangeNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_BID_NUM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_SEND_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_MEDIA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String URL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IS_POSTPONE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_NOMEET_BIDDING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_END_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHANGE_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BULLETIN_DUTY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP

) {
}
