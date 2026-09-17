package cn.iocoder.txgy.module.crm.framework.operatelog.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.crm.enums.contract.CrmContractTemplateTypeEnum;
import com.mzt.logapi.service.IParseFunction;
import org.springframework.stereotype.Component;

/**
 * CRM 合同模板类型的 {@link IParseFunction} 实现类
 *
 * @author dhb52
 */
@Component
public class CrmContractTemplateTypeParseFunction implements IParseFunction {

    public static final String NAME = "getContractTemplateType";

    @Override
    public boolean executeBefore() {
        return true; // 先转换值后对比
    }

    @Override
    public String functionName() {
        return NAME;
    }

    @Override
    public String apply(Object value) {
        if (StrUtil.isEmptyIfStr(value)) {
            return "";
        }
        String name = CrmContractTemplateTypeEnum.getNameByType(Integer.parseInt(value.toString()));
        return name == null ? "" : name;
    }

}
