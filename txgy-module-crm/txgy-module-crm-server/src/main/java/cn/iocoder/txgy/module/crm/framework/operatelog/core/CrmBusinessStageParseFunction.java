package cn.iocoder.txgy.module.crm.framework.operatelog.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStageEnum;
import com.mzt.logapi.service.IParseFunction;
import org.springframework.stereotype.Component;

/**
 * CRM 商机阶段的 {@link IParseFunction} 实现类
 *
 * @author 防腐保温项目组
 */
@Component
public class CrmBusinessStageParseFunction implements IParseFunction {

    public static final String NAME = "getBusinessStage";

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
        String name = CrmBusinessStageEnum.getNameByStage(Integer.parseInt(value.toString()));
        return name == null ? "" : name;
    }

}
