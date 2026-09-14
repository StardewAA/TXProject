package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 信用信息附件采集项——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record CreditAttachItemVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ILLEGAL_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUNISHMENT_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BLACKLIST_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNDO_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REWARD_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PERFORMANCE_DOC

) {
}
