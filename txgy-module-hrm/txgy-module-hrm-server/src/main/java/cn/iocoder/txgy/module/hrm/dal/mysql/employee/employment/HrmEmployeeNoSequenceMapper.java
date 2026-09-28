package cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoSequenceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrmEmployeeNoSequenceMapper extends BaseMapperX<HrmEmployeeNoSequenceDO> {

    /**
     * 加行锁查询序号，用于并发安全取号
     */
    default HrmEmployeeNoSequenceDO selectByRuleIdForUpdate(Long ruleId, String periodKey) {
        return selectOne(new LambdaQueryWrapperX<HrmEmployeeNoSequenceDO>()
                .eq(HrmEmployeeNoSequenceDO::getRuleId, ruleId)
                .eq(HrmEmployeeNoSequenceDO::getPeriodKey, periodKey)
                .last("LIMIT 1 FOR UPDATE"));
    }

}
