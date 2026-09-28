package cn.iocoder.txgy.module.mes.service.qc.sample;

import cn.iocoder.txgy.module.mes.dal.dataobject.qc.template.MesQcTemplateItemDO;

import java.math.BigDecimal;

/**
 * MES 检验抽样方案 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesQcSamplePlanService {

    /**
     * 计算抽样数量，并生成抽样方案快照
     *
     * @param templateItem 检验模板-产品关联（承载抽样方案配置，可为空）
     * @param baseQuantity 抽样基数（来源单据数量）
     * @return 抽样方案快照（含最终抽样数量）
     */
    MesQcSamplePlanSnapshot calculate(MesQcTemplateItemDO templateItem, BigDecimal baseQuantity);

    /**
     * 抽样方案快照序列化为 JSON
     *
     * @param snapshot 抽样方案快照
     * @return JSON 字符串
     */
    String toJson(MesQcSamplePlanSnapshot snapshot);

}
