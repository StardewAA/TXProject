package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 违法违规信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record IllegalInfoVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String VIOLATING_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String VIOLATING_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String VIOLATING_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String VIOLATING_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_ROLE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REWARD_PUNISHMENT_BODY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String VIOLATING_INSTRUCTIONS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String GOVERNMENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DOCUMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUNISHMENT_BASIS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUNISHMENT_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONFINED_ACTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONFINED_START,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONFINED_END,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OPERATE_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SCOPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
