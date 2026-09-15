package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 场地预约/场地变更——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record GpVenueBookVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_SECTION_NAMES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MTR_APPT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_REMOTE_BID_EVALUATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_START_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BID_END_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIDDING_ROOM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_START_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_END_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EVALUATION_ROOM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MTR_APPT_NAME_12,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MTR_APPT_TEL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_CHANGE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHANGE_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
