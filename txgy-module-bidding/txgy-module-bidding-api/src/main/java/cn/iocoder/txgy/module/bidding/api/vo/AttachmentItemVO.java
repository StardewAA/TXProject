package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 附件采集项——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record AttachmentItemVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PROJECT_APPROVAL_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_FILE_ATTACHS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_NOTICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FILE_EX_TDOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String INVITE_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUBLICITY_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String WIN_BID_NOTICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTRACT_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFICATION_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PROJECT_SOURCE_PROOF,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String Contract_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_CHANGE_NOTICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFICATION_NOTICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFICATION_CLARIFY_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFICATION_LIST,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_SIGN_LIST,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFICATION_REPORT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFICATION_RESULT_NOTICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NET_QUESTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SURVEY_RECORD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTROL_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String WIN_NOTICE_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String APPROACH_PROVE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_REVIEW_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMMENTS_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUANTITIES_CLEAR_REPORT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String WIN_BID_TENDER_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_REPORT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTRACT_SING_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PERFORMANCE_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String INQUIRY_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OBJECTION_FILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLAIN_FILE

) {
}
