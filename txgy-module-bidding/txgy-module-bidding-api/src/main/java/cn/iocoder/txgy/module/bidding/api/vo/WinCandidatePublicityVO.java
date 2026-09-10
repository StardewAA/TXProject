package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 中标候选人公示数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record WinCandidatePublicityVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLICITY_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLICITY_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_MEDIA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLICITY_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLICITY_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLICITY_END_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CHANGE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_FIRST,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_FIRST_LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_FIRST_LEGAL_PM_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_FIRST_LEGAL_PM_CERTIFICATES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_FIRST_WIN_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_FIRST_AWARD,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_SECOND,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_SECOND_LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_SECOND_LEGAL_PM_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_SECOND_LEGAL_PM_CERTIFICATES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_SECOND_WIN_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_SECOND_AWARD,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_THIRED,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_THIRED_EGAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_THIRED_EGAL_PM_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_THIRED_EGAL_PM_CERTIFICATES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_THIRED_WIN_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RECOMMEND_RANKING_THIRED_AWARD,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OBJECTION_METHOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BULLETIN_DUTY,
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
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
