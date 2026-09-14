package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 矿权附件采集项——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record MiningAttachItemVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPLORATION_TRANSFER_ANNOUNCEMENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MINING_TRANSFER_ANNOUNCEMENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPLORATION_TRANSFER_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MINING_TRANSFER_RESULT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_EXPLORATION_PUBLIC_INFOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_MINING_PUBLIC_INFOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_RANGE_PUBLIC_INFOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NONOIL_EXPLORATION_REGISTRATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String NONOIL_MINING_REGISTRATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OIL_EXPLORATION_ANNOUNCEMENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OIL_MINING_ANNOUNCEMENT

) {
}
