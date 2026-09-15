package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 评标情况（政采）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record GpEvalVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_START_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_END_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_SITUATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_FIRST,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_FIRST_LEGALCODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_SECOND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_SECOND_LEGALCODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_THIRD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECOMMEND_RANKING_THIRD_LEGALCODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
