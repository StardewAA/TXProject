package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 流拍流挂公告（产权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PropertyFailNoticeVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE_3,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRE_EXCHANGE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHANQUAN_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_PRICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OBJECT_EVALUATE_VALUE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NOTICE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GGFROM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GGTO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String GONGGAOCONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
