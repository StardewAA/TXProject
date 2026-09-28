package cn.iocoder.txgy.module.hrm.dal.mysql.salary.employee;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.employeeinfo.HrmSalaryEmployeeInfoPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.employee.HrmSalaryEmployeeInfoDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;

@Mapper
public interface HrmSalaryEmployeeInfoMapper extends BaseMapperX<HrmSalaryEmployeeInfoDO> {

    default HrmSalaryEmployeeInfoDO selectByEmployeeId(Long employeeId) {
        // 取最新一条：数据库排序 + LIMIT 1，避免全量加载（M-12）
        return selectOne(new LambdaQueryWrapperX<HrmSalaryEmployeeInfoDO>()
                .eq(HrmSalaryEmployeeInfoDO::getEmployeeId, employeeId)
                .orderByDesc(HrmSalaryEmployeeInfoDO::getId)
                .last("LIMIT 1"));
    }

    default PageResult<HrmSalaryEmployeeInfoDO> selectPage(HrmSalaryEmployeeInfoPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmSalaryEmployeeInfoDO>()
                .eqIfPresent(HrmSalaryEmployeeInfoDO::getEmployeeId, reqVO.getEmployeeId())
                .inIfPresent(HrmSalaryEmployeeInfoDO::getEmployeeId, reqVO.getEmployeeIds())
                .eqIfPresent(HrmSalaryEmployeeInfoDO::getChangeType, reqVO.getChangeType())
                .orderByDesc(HrmSalaryEmployeeInfoDO::getId));
    }

    default List<HrmSalaryEmployeeInfoDO> selectListByEmployeeIds(List<Long> employeeIds) {
        return selectList(new LambdaQueryWrapperX<HrmSalaryEmployeeInfoDO>()
                .in(HrmSalaryEmployeeInfoDO::getEmployeeId, employeeIds)
                .orderByDesc(HrmSalaryEmployeeInfoDO::getId));
    }

    default List<Long> selectEmployeeIdListByChangeType(Integer changeType) {
        LambdaQueryWrapperX<HrmSalaryEmployeeInfoDO> query = new LambdaQueryWrapperX<>();
        query.select(HrmSalaryEmployeeInfoDO::getEmployeeId);
        query.eqIfPresent(HrmSalaryEmployeeInfoDO::getChangeType, changeType);
        return convertList(selectList(query),
                HrmSalaryEmployeeInfoDO::getEmployeeId);
    }

}
