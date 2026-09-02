package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 组建评标/资审委员会——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record EvalCommitteeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATING_DUE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_ROOM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATING_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_QUALI_AUDIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_OTHER_EXPERT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_SUP_NUM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ID_CARD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ID_CARD_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CHAIR_MAN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT

) {
}
