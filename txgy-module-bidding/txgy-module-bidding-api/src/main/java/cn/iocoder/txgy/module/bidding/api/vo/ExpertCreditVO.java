package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 专家信用记录数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ExpertCreditVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ID_CARD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REWARD_PUNISHMENT_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String APPROVE_DEPT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVE_DEPT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String VIOLATING_BASIS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String VIOLATING_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUBLISH_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONFINED_ACTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String START_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String END_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OPERATOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OPERATE_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
