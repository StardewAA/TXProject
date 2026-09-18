package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 实物项目信息（产权挂牌披露）——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PropertyProjectVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PROJECT_CLASSIFY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String YEWU_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRE_EXCHANGE_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DESIGNATED_AGENCEY,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECTDETAIL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZHUANRANGDANWEI,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PIZHUNWENJIANNO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JIANGUANDANWEINAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZHUANRANGFANGLIANREN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ZHUANRANGFANGTEL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BUYER_CONTACTOR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CONTACT_TEL,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JINJITYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String DANWEI_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZIBEN,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JINGYINGGUIMO,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String FAREN,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZHUANRANGFANGORGNUM,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String JUECEQINGKUANG,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String ZONE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String BID_NAME,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_TYPE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String SELLER_ROLE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_PRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CURRENCY_CODE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PRICE_UNIT,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISH_DURATION,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PUBLISH_DATE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String POST_PONE,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String TRANSFER_NOTES,
        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_URL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE

) {
}
