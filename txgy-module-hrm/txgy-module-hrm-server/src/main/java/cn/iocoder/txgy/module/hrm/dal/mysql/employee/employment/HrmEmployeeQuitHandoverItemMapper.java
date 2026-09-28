package cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeQuitHandoverItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmEmployeeQuitHandoverItemMapper extends BaseMapperX<HrmEmployeeQuitHandoverItemDO> {

    default List<HrmEmployeeQuitHandoverItemDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeQuitHandoverItemDO>()
                .eq(HrmEmployeeQuitHandoverItemDO::getEmployeeId, employeeId)
                .orderByAsc(HrmEmployeeQuitHandoverItemDO::getId));
    }

    default List<HrmEmployeeQuitHandoverItemDO> selectListByEmployeeIds(Collection<Long> employeeIds) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeQuitHandoverItemDO>()
                .in(HrmEmployeeQuitHandoverItemDO::getEmployeeId, employeeIds)
                .orderByAsc(HrmEmployeeQuitHandoverItemDO::getId));
    }

    default void deleteByEmployeeId(Long employeeId) {
        delete(HrmEmployeeQuitHandoverItemDO::getEmployeeId, employeeId);
    }

}
