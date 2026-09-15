package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 组建评标/资审委员会抽取条件——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ExpertExtractConditionVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATING_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXTRACT_XIAQU,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXTRACT_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_EMERGENCY_EXPERT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATING_MAJOR_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATING_MAJOR_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String INDUSTRY_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXTRACT_TYPE_11,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NEED_EXPERT_NUM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NEED_EXTRACT_EXPERT_NUM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_SET_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXTRACTION_CONDITION_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_OTHER_EXPERT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_SUP_NUM

) {
}
