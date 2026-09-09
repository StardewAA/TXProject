package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 投标邀请书数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record BidInviteVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_INVITE_ISSUE_NATURE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_INVITE_ISSUE_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DOC_GET_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DOC_GET_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_DOC_GET_METHOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REPLY_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_DOC_REFER_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_DOC_REFER_METHOD,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BULLETIN_DUTY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPANY_QUALI_DEMAND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PM_QUALI_DEMAND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_OTHER_CONDITION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SYNDICATED_FLAG,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONSORTIUM_REQUIREMENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NOMINAL_FEE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_INVITE_ISSUE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT

) {
}
