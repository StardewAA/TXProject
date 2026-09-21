package cn.iocoder.txgy.module.hrm.service.salary.attendancerule;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceRulePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceRuleSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.attendancerule.HrmSalaryAttendanceRuleDO;

/**
 * HRM 考勤计薪规则 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryAttendanceRuleService {

    Long createRule(HrmSalaryAttendanceRuleSaveReqVO createReqVO);

    void updateRule(HrmSalaryAttendanceRuleSaveReqVO updateReqVO);

    void deleteRule(Long id);

    HrmSalaryAttendanceRuleDO getRule(Long id);

    HrmSalaryAttendanceRuleDO validateRuleExists(Long id);

    PageResult<HrmSalaryAttendanceRuleDO> getRulePage(HrmSalaryAttendanceRulePageReqVO pageReqVO);

    /**
     * 获得默认启用的规则（没有则返回 null）
     */
    HrmSalaryAttendanceRuleDO getDefaultRule();

    /**
     * 考勤计薪试算
     *
     * @param reqVO 试算参数
     * @return 试算结果
     */
    HrmSalaryAttendanceCalcRespVO calc(HrmSalaryAttendanceCalcReqVO reqVO);

}
