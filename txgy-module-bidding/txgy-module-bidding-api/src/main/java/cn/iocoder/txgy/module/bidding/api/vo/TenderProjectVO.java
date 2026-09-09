package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 招标项目数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record TenderProjectVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDERER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDERER_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDERER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_AGENCY_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_AGENCY_CODE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_AGENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_MODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QULIFICATION_CHECK_MODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_ORGANIZE_FORM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CREATE_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUPERVISE_DEPT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OWNER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUPERVISE_DEPT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVE_DEPT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVE_DEPT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TRADE_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT

) {
}
