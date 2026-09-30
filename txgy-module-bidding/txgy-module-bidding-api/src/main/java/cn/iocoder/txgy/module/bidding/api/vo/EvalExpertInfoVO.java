package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 评标专家信息数据集——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record EvalExpertInfoVO(

        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ID_CARD,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ID_CARD_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EXPERT_NAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SEX,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIRTHDAY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DUTY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String EDUCATION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PROFESSIONAL,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ONWORK,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String COMPANY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String POST_ADDRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String POSTCODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String INDUSTRY,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String AREA,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String WORK_YEAR,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PHONE_NUMBER,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String OTHER_NUMBER,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DISMISSION,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String ENTER_DATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BASE_TYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String MAJOR_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String IS_MAIN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PLATFORM_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String PUB_SERVICE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
