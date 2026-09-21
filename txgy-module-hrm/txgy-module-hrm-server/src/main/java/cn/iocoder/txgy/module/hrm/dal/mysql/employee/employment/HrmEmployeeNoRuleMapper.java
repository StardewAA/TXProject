package cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoRuleDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrmEmployeeNoRuleMapper extends BaseMapperX<HrmEmployeeNoRuleDO> {

    /**
     * 获得当前租户启用的工号规则（按编号升序取第一条）
     */
    default HrmEmployeeNoRuleDO selectEnabledRule() {
        return selectOne(new LambdaQueryWrapperX<HrmEmployeeNoRuleDO>()
                .eq(HrmEmployeeNoRuleDO::getStatus, 0)
                .orderByAsc(HrmEmployeeNoRuleDO::getId)
                .last("LIMIT 1"));
    }

}
