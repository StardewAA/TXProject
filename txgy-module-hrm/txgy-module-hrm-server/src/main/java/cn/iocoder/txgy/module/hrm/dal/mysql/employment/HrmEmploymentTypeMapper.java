package cn.iocoder.txgy.module.hrm.dal.mysql.employment;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypePageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employment.HrmEmploymentTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmEmploymentTypeMapper extends BaseMapperX<HrmEmploymentTypeDO> {

    default PageResult<HrmEmploymentTypeDO> selectPage(HrmEmploymentTypePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmEmploymentTypeDO>()
                .likeIfPresent(HrmEmploymentTypeDO::getName, reqVO.getName())
                .eqIfPresent(HrmEmploymentTypeDO::getCode, reqVO.getCode())
                .eqIfPresent(HrmEmploymentTypeDO::getSalaryMode, reqVO.getSalaryMode())
                .eqIfPresent(HrmEmploymentTypeDO::getStatus, reqVO.getStatus())
                .orderByAsc(HrmEmploymentTypeDO::getSort)
                .orderByDesc(HrmEmploymentTypeDO::getId));
    }

    default List<HrmEmploymentTypeDO> selectListAll() {
        return selectList(new LambdaQueryWrapperX<HrmEmploymentTypeDO>()
                .orderByAsc(HrmEmploymentTypeDO::getSort)
                .orderByDesc(HrmEmploymentTypeDO::getId));
    }

    default List<HrmEmploymentTypeDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmEmploymentTypeDO>()
                .eq(HrmEmploymentTypeDO::getStatus, status)
                .orderByAsc(HrmEmploymentTypeDO::getSort)
                .orderByDesc(HrmEmploymentTypeDO::getId));
    }

    default HrmEmploymentTypeDO selectByName(String name) {
        return selectOne(HrmEmploymentTypeDO::getName, name);
    }

    default HrmEmploymentTypeDO selectByCode(String code) {
        return selectOne(HrmEmploymentTypeDO::getCode, code);
    }

}
