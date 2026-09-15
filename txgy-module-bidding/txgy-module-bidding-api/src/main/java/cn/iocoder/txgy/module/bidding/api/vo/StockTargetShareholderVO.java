package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 股权类产权交易转让标的企业股东信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record StockTargetShareholderVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBJECT_COMPANY_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SUBJECT_COMPANY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TOP10_HOLDER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TOP10_HOLDER_NUMBER,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TOP10_HOLDER_PERCENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
