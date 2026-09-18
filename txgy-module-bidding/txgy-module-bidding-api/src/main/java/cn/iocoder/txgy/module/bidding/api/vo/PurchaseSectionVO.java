package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 采购标段信息——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PurchaseSectionVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PURCHASE_SECTION_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String REGION_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODUAN_MULBCODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_NUM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_BUDGET,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_WEB_BIDDING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_NOMEET_BIDDING,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALI_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BIDCONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String REMARK,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
