package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 项目主体信息扩展——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ProjectSubjectExtVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String UNIT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CONTACTOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTACT_INFORMATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DANWEI_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDERER_ADDRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LICENSE_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LICENSE_END_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TAX_CERT_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TAX_CERT_END_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LOCAL_TAX_CERT_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LOCAL_TAX_CERT_END_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_REPRESENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_INDUSTRY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String COUNTRY_REGION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REG_CAPITAL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REG_CAP_CURRENCY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REG_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_CONTACT_PHONE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CUSTOM_DEFINITION_DATAITEM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUAL_LEVEL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUAL_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUAL_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUAL_CERT_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QUAL_CERT_END_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LEGAL_CONTACT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
