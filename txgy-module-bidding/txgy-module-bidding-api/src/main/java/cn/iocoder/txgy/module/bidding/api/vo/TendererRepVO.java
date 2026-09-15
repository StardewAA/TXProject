package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 招标人代表-人员——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record TendererRepVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_QUALI_AUDIT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_IDCARD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_JOB,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_OU,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_MOBILE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_MAJOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JURY_JOB,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SUBMIT_TIMESTAMP

) {
}
