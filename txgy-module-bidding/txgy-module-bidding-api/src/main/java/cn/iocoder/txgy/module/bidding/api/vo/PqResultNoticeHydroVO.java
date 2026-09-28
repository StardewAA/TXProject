package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 代资格预审结果通知书（水利专用）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PqResultNoticeHydroVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ZBFILE_NOMINAL_FEE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ZBFILE_GET_BEGINTIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ZBFILE_GET_ENDTIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ZBFILE_GET_ADDRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TBFILE_REFER_ENDTIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TBFILE_REFER_METHOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_NOMEET_BIDDING,
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
        String SUBMIT_TIMESTAMP

) {
}
