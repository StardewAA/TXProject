package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 中标候选人数据集（模块化）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record WinCandidateVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_CANDIDATE_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_CANDIDATE_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_CANDIDATE_ORDER,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_FORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PRICE_CURRENCY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PRICE_UINT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OTHER_BID_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String WIN_CANDIDATE_QUALIFICATION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PM_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PM_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PM_CERTIFICATES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REPORT_DUTY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REPORT_COMPANY_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REPORT_COMPANY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE_21,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_RESULT,
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
