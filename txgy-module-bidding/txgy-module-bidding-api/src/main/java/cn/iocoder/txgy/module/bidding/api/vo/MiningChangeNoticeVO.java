package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 变更公告（矿权）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record MiningChangeNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_NO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String MINING_AREA_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHANGE_NOTICE_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OLD_GONGGAO_REALFROM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OLD_GONGGAO_REALTO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OLD_BAOMING_FROM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OLD_BAOMING_TO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OLD_BZJENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OLD_GUAPAIDATEFROM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String OLD_GUAPAIDATETO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHANGE_NOTICE_START_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHANGE_NOTICE_END_TIME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NEW_BAOMING_FROM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NEW_BAOMING_TO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NEW_BZJENDDATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NEW_GUAPAIDATEFROM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NEW_GUAPAIDATETO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CHANGE_NOTICE_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
