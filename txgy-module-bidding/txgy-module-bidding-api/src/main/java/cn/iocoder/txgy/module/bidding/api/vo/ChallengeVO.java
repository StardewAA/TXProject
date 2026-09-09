package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 质疑提出及答复——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ChallengeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPTOR_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPTOR_GUID,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPTOR_UNIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ACCEPTION_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FEEDBACK_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENT_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBMIT_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENT_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENT_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BASIS_AND_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String HANDLE_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENTOR_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENTOR_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENTOR_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENTOR_PHONE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENTOR_MAIL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENTOR_ADDRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISSENTEDOR_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
