package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 招标异常情况报告数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ExceptionReportVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTION_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTION_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTIONER_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTION_BODY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTION_BODY_ROLE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTIONER_TEL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUESTION_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_ANSWER,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ANSWER_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ANSWER_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ANSWER_BODY_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ANSWER_BODY_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ANSWER_BODY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EXCEPTION_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EXCEPTION_INFO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVAL_RESULT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVAL_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXCEPITON_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXCEPITON_INFOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String HANDLE_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_PUBLISH_NOTICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NOTICE_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NEW_TENDER_MODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_COPY_DW,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_NOTICE_DW,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBMIT_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
