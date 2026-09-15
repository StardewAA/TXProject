package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 资格预审/资格预审澄清数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PrequalifyDocVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUALIFICATION_ELEMENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FILENO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String APPLI_PERIOD,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUAL_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFY_DOC_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FILE_CREATE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALIFY_DOC_MODEL_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBMIT_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ISCLARIFY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CLARIFY_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHANGE_QUALI_AUDIT_OLDTIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHANGE_QUALI_AUDIT_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CLARIFYNUM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DOC_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPLICATION_VALID_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EVALUATION_METHOD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
