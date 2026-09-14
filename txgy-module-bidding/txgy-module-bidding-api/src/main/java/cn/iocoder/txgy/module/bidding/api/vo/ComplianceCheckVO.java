package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 符合性检查结果——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ComplianceCheckVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIT_PROJECT_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIT_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDDER_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPLIANCEINSPECTIONTYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ERRORTYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHECKITEMNAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ERRORDESCRIPTION

) {
}
