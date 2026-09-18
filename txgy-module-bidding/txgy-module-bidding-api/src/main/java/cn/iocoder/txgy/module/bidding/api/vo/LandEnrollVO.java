package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 地块报名查看——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record LandEnrollVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String LAND_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String LAND_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JINGMAI_NUM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ENROLL_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BZJSTATUS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
