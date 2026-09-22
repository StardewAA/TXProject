package cn.iocoder.txgy.module.hrm.dal.mysql.employee.approval;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval.HrmEmployeeApprovalPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.approval.HrmEmployeeApprovalDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrmEmployeeApprovalMapper extends BaseMapperX<HrmEmployeeApprovalDO> {

    default PageResult<HrmEmployeeApprovalDO> selectPage(HrmEmployeeApprovalPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmEmployeeApprovalDO>()
                .eqIfPresent(HrmEmployeeApprovalDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmEmployeeApprovalDO::getType, reqVO.getType())
                .eqIfPresent(HrmEmployeeApprovalDO::getStatus, reqVO.getStatus())
                .orderByAsc(HrmEmployeeApprovalDO::getStatus)
                .orderByDesc(HrmEmployeeApprovalDO::getId));
    }

}
