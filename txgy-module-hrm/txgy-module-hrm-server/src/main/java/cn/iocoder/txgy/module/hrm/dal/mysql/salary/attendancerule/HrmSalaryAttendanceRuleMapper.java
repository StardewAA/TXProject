package cn.iocoder.txgy.module.hrm.dal.mysql.salary.attendancerule;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceRulePageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.attendancerule.HrmSalaryAttendanceRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmSalaryAttendanceRuleMapper extends BaseMapperX<HrmSalaryAttendanceRuleDO> {

    default PageResult<HrmSalaryAttendanceRuleDO> selectPage(HrmSalaryAttendanceRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmSalaryAttendanceRuleDO>()
                .likeIfPresent(HrmSalaryAttendanceRuleDO::getName, reqVO.getName())
                .eqIfPresent(HrmSalaryAttendanceRuleDO::getStatus, reqVO.getStatus())
                .orderByDesc(HrmSalaryAttendanceRuleDO::getDefaultFlag)
                .orderByDesc(HrmSalaryAttendanceRuleDO::getId));
    }

    /**
     * 查询默认启用的规则
     */
    default HrmSalaryAttendanceRuleDO selectDefaultRule() {
        List<HrmSalaryAttendanceRuleDO> list = selectList(new LambdaQueryWrapperX<HrmSalaryAttendanceRuleDO>()
                .eq(HrmSalaryAttendanceRuleDO::getStatus, 0)
                .orderByDesc(HrmSalaryAttendanceRuleDO::getDefaultFlag)
                .orderByDesc(HrmSalaryAttendanceRuleDO::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
