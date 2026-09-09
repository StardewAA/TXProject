package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 行政监管事项信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record SuperviseMatterVO(

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
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRADE_STEP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVAL_PROJECT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GOVERNMENT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String AREA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAW,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_PAST,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String APPROVAL_DATE,
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
