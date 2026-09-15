package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 主体附件采集项——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record SubjectAttachItemVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String LICENSE_CERT_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ORGAN_CERT_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String TAX_CERT_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String QUAL_CERT_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ID_CARD_DOC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PROFESSIONAL_CERT_DOC

) {
}
