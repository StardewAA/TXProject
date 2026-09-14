package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 资格预审公告（政采）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record GpPqNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_BID_NUM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_TITLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_MEDIA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String URL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CLOSING_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
