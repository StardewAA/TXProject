package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 投诉信息数据集（陕西1.7.3口径）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ComplaintVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAIN_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAIN_UNIT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAIN_LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAINANT_TEL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAINANT_EMAIL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RESPONDENT_UNIT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RESPONDENT_LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAIN_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAIN_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAIN_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ACCEPT_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FEEDBACK_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DEAL_STEP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String HANDLE_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_OBJECTED,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENT_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String COMPLAINTED_BODY_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String HANDLE_BASIS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String HANDLE_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONFINED_ACTION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String START_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String END_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DEALING_UNIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ENTER_CREDIT_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SIGNATURE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAINT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAINT_PHONE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAINT_MAIL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAINTED_BODY_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAINTED_BODY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENT_REASON,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String COMPLAINT_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String COMPLAINT_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
