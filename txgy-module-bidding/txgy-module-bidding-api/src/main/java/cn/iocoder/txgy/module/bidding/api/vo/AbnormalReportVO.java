package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 异常情况报告（政采）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record AbnormalReportVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TENDER_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXCEPITON_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXCEPITON_INFOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String HANDLE_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_PUBLISH_NOTICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NOTICE_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE_CONTENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBMIT_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
