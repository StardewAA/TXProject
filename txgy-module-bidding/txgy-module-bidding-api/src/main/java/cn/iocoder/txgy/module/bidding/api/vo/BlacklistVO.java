package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 黑名单信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record BlacklistVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BLACKLIST_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BODY_ROLE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECORD_REASON,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String RECORD_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String GOVERNMENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DOCUMENT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REVOCATION_STATUS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONFINED_ACTION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SCOPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
