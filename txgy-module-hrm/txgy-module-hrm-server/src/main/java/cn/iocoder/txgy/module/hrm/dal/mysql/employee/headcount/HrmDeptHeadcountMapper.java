package cn.iocoder.txgy.module.hrm.dal.mysql.employee.headcount;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.headcount.HrmDeptHeadcountDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrmDeptHeadcountMapper extends BaseMapperX<HrmDeptHeadcountDO> {

    default PageResult<HrmDeptHeadcountDO> selectPage(HrmDeptHeadcountPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmDeptHeadcountDO>()
                .eqIfPresent(HrmDeptHeadcountDO::getDeptId, reqVO.getDeptId())
                .orderByDesc(HrmDeptHeadcountDO::getId));
    }

    default HrmDeptHeadcountDO selectByDeptId(Long deptId) {
        return selectOne(HrmDeptHeadcountDO::getDeptId, deptId);
    }

}
