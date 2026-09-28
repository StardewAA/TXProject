package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 股权类产权交易转让标的企业信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record StockTargetCompanyVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBJECT_COMPANY_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELL_PERCENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBJECT_COMPANY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZONE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String INDUSTRY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ECONOMY_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String EMPLOYEE_QUANTITY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String HAS_CONTAIN_GROUND,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String LAST_AUDIT_YEAR,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAST_AUDIT_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAST_AUDIT_ASSET,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAST_AUDIT_DEBT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAST_AUDIT_EQUITY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAST_AUDIT_EARNING,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAST_AUDIT_PROFIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAST_AUDIT_NETPROFIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String FINANCE_REPORT_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String FINANCE_REPORT_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TOTAL_CAPITAL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TOTAL_DEBT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OWNERSHIP_INTEREST,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUSINESS_INCOME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUSINESS_PROFIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NET_PROFIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
