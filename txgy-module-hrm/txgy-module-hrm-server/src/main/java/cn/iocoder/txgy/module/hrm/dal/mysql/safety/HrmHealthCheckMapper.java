package cn.iocoder.txgy.module.hrm.dal.mysql.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmHealthCheckDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmHealthCheckMapper extends BaseMapperX<HrmHealthCheckDO> {

    default PageResult<HrmHealthCheckDO> selectPage(HrmHealthCheckPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmHealthCheckDO>()
                .eqIfPresent(HrmHealthCheckDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmHealthCheckDO::getCheckType, reqVO.getCheckType())
                .eqIfPresent(HrmHealthCheckDO::getResult, reqVO.getResult())
                .orderByDesc(HrmHealthCheckDO::getCheckDate)
                .orderByDesc(HrmHealthCheckDO::getId));
    }

    default List<HrmHealthCheckDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmHealthCheckDO>()
                .eq(HrmHealthCheckDO::getEmployeeId, employeeId)
                .orderByDesc(HrmHealthCheckDO::getCheckDate));
    }

}
