package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 项目数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ProjectVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String RELATE_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String INVEST_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ADDRESS,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_PERSON,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String INDUSTRIES_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String FUND_SOURCE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTRIBUTION_SCALE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_SCALE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVAL_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVAL_NUMBER,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVAL_AUTHORITY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CREATE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PROJECT_TRADE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ORGANIZE_FORM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_SITUATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String INVEST_RECKON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_OWNED_FUND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_OWNED_FUND_RATIO // 规范原文字段名中间带空格，去掉空格照录,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PRIVATE_FUND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PRIVATE_FUND_RATIO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FOREIGN_FUND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FOREIGN_FUND_RATIO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FOREIGN_PRIVATE_FUND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FOREIGN_PRIVATE_FUND_RATIO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BUIDING_AREA,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_INSTRUCTIONS,
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
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTACTOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTACT_INFORMATION,
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
