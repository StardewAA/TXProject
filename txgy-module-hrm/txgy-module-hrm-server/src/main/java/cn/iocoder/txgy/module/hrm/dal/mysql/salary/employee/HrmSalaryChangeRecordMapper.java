package cn.iocoder.txgy.module.hrm.dal.mysql.salary.employee;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.employee.HrmSalaryChangeRecordDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface HrmSalaryChangeRecordMapper extends BaseMapperX<HrmSalaryChangeRecordDO> {

    default List<HrmSalaryChangeRecordDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmSalaryChangeRecordDO>()
                .eq(HrmSalaryChangeRecordDO::getEmployeeId, employeeId)
                .orderByDesc(HrmSalaryChangeRecordDO::getEffectTime)
                .orderByDesc(HrmSalaryChangeRecordDO::getId));
    }

    default HrmSalaryChangeRecordDO selectByEmployeeIdAndType(Long employeeId, Integer type) {
        // 取最新一条：改为数据库排序 + LIMIT 1，避免 selectLastOne 全量加载后在内存取最后一条（M-12）
        return selectOne(new LambdaQueryWrapperX<HrmSalaryChangeRecordDO>()
                .eq(HrmSalaryChangeRecordDO::getEmployeeId, employeeId)
                .eq(HrmSalaryChangeRecordDO::getType, type)
                .orderByDesc(HrmSalaryChangeRecordDO::getId)
                .last("LIMIT 1"));
    }

    default Long selectCountByEmployeeIdAndStatus(Long employeeId, Integer status, Long excludeId) {
        return selectCount(new LambdaQueryWrapperX<HrmSalaryChangeRecordDO>()
                .eq(HrmSalaryChangeRecordDO::getEmployeeId, employeeId)
                .eq(HrmSalaryChangeRecordDO::getStatus, status)
                .neIfPresent(HrmSalaryChangeRecordDO::getId, excludeId));
    }

    default Long selectCountByEmployeeIdAndTypeAndStatusNot(
            Long employeeId, Integer type, Integer status) {
        return selectCount(new LambdaQueryWrapperX<HrmSalaryChangeRecordDO>()
                .eq(HrmSalaryChangeRecordDO::getEmployeeId, employeeId)
                .eq(HrmSalaryChangeRecordDO::getType, type)
                .ne(HrmSalaryChangeRecordDO::getStatus, status));
    }

    default List<HrmSalaryChangeRecordDO> selectListByStatusAndEffectTimeBeforeOrEqual(
            Integer status, LocalDateTime endTime) {
        return selectList(new LambdaQueryWrapperX<HrmSalaryChangeRecordDO>()
                .eq(HrmSalaryChangeRecordDO::getStatus, status)
                .le(HrmSalaryChangeRecordDO::getEffectTime, endTime)
                .orderByAsc(HrmSalaryChangeRecordDO::getEffectTime)
                .orderByAsc(HrmSalaryChangeRecordDO::getId));
    }

    default Long selectCountByStatusAndEffectTimeBeforeOrEqual(Integer status, LocalDateTime endTime) {
        return selectCount(new LambdaQueryWrapperX<HrmSalaryChangeRecordDO>()
                .eq(HrmSalaryChangeRecordDO::getStatus, status)
                .le(HrmSalaryChangeRecordDO::getEffectTime, endTime));
    }

    default int updateByIdAndStatus(HrmSalaryChangeRecordDO updateObj, Integer status) {
        return update(updateObj,
                new LambdaUpdateWrapper<HrmSalaryChangeRecordDO>()
                        .eq(HrmSalaryChangeRecordDO::getId, updateObj.getId())
                        .eq(HrmSalaryChangeRecordDO::getStatus, status));
    }

}
