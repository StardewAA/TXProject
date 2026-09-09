package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 合同履约（非必须环节）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ContractPerformVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTRACT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTRACT_SETTLEMENT_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String HANDLE_FILE_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPTANCE_STANDARD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLIANCE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPTANCE_OU,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPTANCE_MANAGER,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACTUAL_KEEP_PROMISE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACTUAL_PERFORMANCE_START_TIME_CONTRACT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACTUAL_PERFORMANCE_END_TIME_CONTRACT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String KEEP_PROMISE_INFO_REFER_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String KEEP_PROMISE_CHANGE_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP

) {
}
