package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 矿区报名查看——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record MiningEnrollVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MINING_AREA_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MINING_AREA_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BAOMINGSTARTDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BAOMINGENDDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BAOZHENGJIN_JIEZHITIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JINGMAI_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTACTOR_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTACT_TEL,
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
