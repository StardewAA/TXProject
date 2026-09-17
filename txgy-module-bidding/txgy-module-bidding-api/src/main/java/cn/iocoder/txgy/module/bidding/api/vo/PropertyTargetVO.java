package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 标的信息（产权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PropertyTargetVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OBJECT_EVALUATE_VALUE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JINGJIA_STARTDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JINGJIA_ENDDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MARGIN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MARGIN_PAY_END_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JIAONAFANGSHI,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_ALLOWING_STARTING_PRICE_BIDDING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REMARK,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
