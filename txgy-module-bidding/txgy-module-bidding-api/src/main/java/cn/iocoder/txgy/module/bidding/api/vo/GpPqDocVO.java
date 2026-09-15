package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 资格预审文件/资格预审澄清文件——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record GpPqDocVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_SECTION_CODES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PURCHASE_SECTION_NAMES,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALI_FILE_NO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALI_FILE_TITLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CLARIFY_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CLARIFY_NUM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String CLARIFY_CONTENT,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FILE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALI_FILE_SALE_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALI_FILE_SALEEND_TIME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUALI_FILE_REMARK,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ATTACHMENT_CODE

) {
}
