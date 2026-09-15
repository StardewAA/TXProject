package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 挂牌底价（矿权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record MiningFloorPriceVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String GUAPAIKAISHIDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String GUAPAIENDDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DIJIA,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CAPITAL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMMIT_PEOPLE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SHENHE_XIAQU,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TIJIAOTIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
