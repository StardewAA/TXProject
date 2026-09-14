package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 项目报警信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record ProjectAlarmVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TENDER_PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRADE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUPERVISOR_UNIT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUPERVISOR_UNIT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUPERVISORY_NODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ALERT_LEVEL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ALERT_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ALERT_INFO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String HANDLED,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String HANDLE_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUBLISH_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONFINED_ACTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String START_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String END_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OPERATOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ENTER_CREDIT_PLAT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SIGNATURE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
