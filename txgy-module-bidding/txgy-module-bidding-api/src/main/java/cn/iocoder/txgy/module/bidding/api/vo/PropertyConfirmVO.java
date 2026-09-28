package cn.iocoder.txgy.module.bidding.api.vo;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

/**
 * 交易确认书——规范全量 VO：字段名照录规范“英文名称”列（含原文拼写瑕疵，勿改）；
 * 供上报 / 第三方对接按字段名与公开范围对齐，页面与内部接口请另建 VO（见开发规范第三章）。
 */
public record PropertyConfirmVO(

        @PublicLevel(PublicLevelEnum.SOCIAL)
        String PROJECT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String UNIFIED_DEAL_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FAZHENGDANWEINAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FAZHENGDANWEINAMEYW,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FAZHENGDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHURANGFANGNAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHURANGFANGADDRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHURANGFANGFAREN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHURNAGZIBEN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String CHURNGFANGWENJIAN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SHOURNGFANGDANWEINAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SHOURNGFANGADRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SHOURANGFANGFAREN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SHOURANGFANGZIBEN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String SHOURANGFANGYIJIAN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIYEDANWEINAME,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIYEADRESS,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIYEFAREN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIYESHENGFENNO,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIZIBEN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIORGNUM,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIHZWENJIAN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIZICHAN,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIFUZHAI,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODIJZC,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BIAODI,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String GONGGAOSTARTDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String GONGGAOSENDDATE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JIAOYITYPE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JIAOYIPRICE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String JIAOYINEIRONG,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String BEIZHU,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String FROM_TRADE_PLAT_CODE,
        @PublicLevel(PublicLevelEnum.GOVERNMENT)
        String DATA_TIMESTAMP

) {
}
