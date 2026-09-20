package cn.iocoder.txgy.module.hrm.dal.mysql.employee.blacklist;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.blacklist.HrmEmployeeBlacklistDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmEmployeeBlacklistMapper extends BaseMapperX<HrmEmployeeBlacklistDO> {

    default PageResult<HrmEmployeeBlacklistDO> selectPage(HrmEmployeeBlacklistPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmEmployeeBlacklistDO>()
                .likeIfPresent(HrmEmployeeBlacklistDO::getName, reqVO.getName())
                .likeIfPresent(HrmEmployeeBlacklistDO::getMobile, reqVO.getMobile())
                .likeIfPresent(HrmEmployeeBlacklistDO::getIdCardNo, reqVO.getIdCardNo())
                .eqIfPresent(HrmEmployeeBlacklistDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmEmployeeBlacklistDO::getStatus, reqVO.getStatus())
                .eqIfPresent(HrmEmployeeBlacklistDO::getSource, reqVO.getSource())
                .orderByDesc(HrmEmployeeBlacklistDO::getId));
    }

    /**
     * 查询生效中的黑名单（命中校验用）
     */
    default List<HrmEmployeeBlacklistDO> selectActiveByMobile(String mobile) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeBlacklistDO>()
                .eq(HrmEmployeeBlacklistDO::getMobile, mobile)
                .eq(HrmEmployeeBlacklistDO::getStatus, 1));
    }

    default List<HrmEmployeeBlacklistDO> selectActiveByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeBlacklistDO>()
                .eq(HrmEmployeeBlacklistDO::getEmployeeId, employeeId)
                .eq(HrmEmployeeBlacklistDO::getStatus, 1));
    }

    default List<HrmEmployeeBlacklistDO> selectActiveByIdCardNo(String idCardNo) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeBlacklistDO>()
                .eq(HrmEmployeeBlacklistDO::getIdCardNo, idCardNo)
                .eq(HrmEmployeeBlacklistDO::getStatus, 1));
    }

}
