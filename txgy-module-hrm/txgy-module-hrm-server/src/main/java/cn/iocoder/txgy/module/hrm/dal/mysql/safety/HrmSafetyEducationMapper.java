package cn.iocoder.txgy.module.hrm.dal.mysql.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmSafetyEducationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmSafetyEducationMapper extends BaseMapperX<HrmSafetyEducationDO> {

    default PageResult<HrmSafetyEducationDO> selectPage(HrmSafetyEducationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmSafetyEducationDO>()
                .eqIfPresent(HrmSafetyEducationDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmSafetyEducationDO::getLevel, reqVO.getLevel())
                .eqIfPresent(HrmSafetyEducationDO::getQualified, reqVO.getQualified())
                .orderByDesc(HrmSafetyEducationDO::getEducationDate)
                .orderByDesc(HrmSafetyEducationDO::getId));
    }

    default List<HrmSafetyEducationDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmSafetyEducationDO>()
                .eq(HrmSafetyEducationDO::getEmployeeId, employeeId)
                .orderByAsc(HrmSafetyEducationDO::getLevel)
                .orderByDesc(HrmSafetyEducationDO::getEducationDate));
    }

}
