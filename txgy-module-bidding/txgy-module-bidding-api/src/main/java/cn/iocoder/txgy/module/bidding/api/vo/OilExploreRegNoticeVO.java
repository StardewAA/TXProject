package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 油气探矿权登记公告信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record OilExploreRegNoticeVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_ITEM_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISHING_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ID_LICENCEID,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String IN_ITEM_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_APPLY_PERSON,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPLY_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPLY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPLY_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_JZZB,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DT_USEFUL_LIFE_START,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DT_USEFUL_LIFE_END,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String QT_TOTAL_AREA,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String NA_GEOGRAPHY_POSITION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String APPROVE_ORG,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
