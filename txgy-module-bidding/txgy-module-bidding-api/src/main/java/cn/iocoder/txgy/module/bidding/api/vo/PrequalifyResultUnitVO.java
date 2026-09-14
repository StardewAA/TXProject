package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 资格预审结果数据集（单位信息）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PrequalifyResultUnitVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OU_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PM_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALI_AUDIT_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ADOPT_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REJECT_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String APPLY_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PASSED_APPLICANT_LIST,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RESULT_CREATE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
