package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 评标报告数据集（含陕西评标情况）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record EvaluationSituationVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_BEGINTIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_ENDTIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_FIRST,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_FIRST_LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_SECOND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_SECOND_LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_THIRED,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_THIRED_EGAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EVALUATION_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ISBACK,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BACKREASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JZPrice,
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
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String END_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
