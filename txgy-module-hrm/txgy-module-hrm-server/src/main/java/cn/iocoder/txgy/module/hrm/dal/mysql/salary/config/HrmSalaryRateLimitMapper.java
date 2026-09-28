package cn.iocoder.txgy.module.hrm.dal.mysql.salary.config;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryRateLimitDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmSalaryRateLimitMapper extends BaseMapperX<HrmSalaryRateLimitDO> {

    default List<HrmSalaryRateLimitDO> selectListByBizType(Integer bizType) {
        return selectList(new LambdaQueryWrapperX<HrmSalaryRateLimitDO>()
                .eqIfPresent(HrmSalaryRateLimitDO::getBizType, bizType)
                .orderByAsc(HrmSalaryRateLimitDO::getBizType)
                .orderByAsc(HrmSalaryRateLimitDO::getId));
    }

    default HrmSalaryRateLimitDO selectByBizTypeAndItemCode(Integer bizType, String itemCode) {
        return selectOne(new LambdaQueryWrapperX<HrmSalaryRateLimitDO>()
                .eq(HrmSalaryRateLimitDO::getBizType, bizType)
                .eq(HrmSalaryRateLimitDO::getItemCode, itemCode));
    }

}
