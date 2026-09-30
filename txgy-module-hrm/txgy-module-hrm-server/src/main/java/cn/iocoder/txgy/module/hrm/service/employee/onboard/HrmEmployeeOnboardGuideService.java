package cn.iocoder.txgy.module.hrm.service.employee.onboard;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeOnboardGuideRespVO;

/**
 * HRM 员工「上岗向导」Service 接口（I-020）
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeOnboardGuideService {

    /**
     * 获得员工上岗就绪清单：后台账号 / 薪资（薪资组 + 薪资档案）/ 考勤组 / 社保参保
     *
     * @param employeeId 员工编号
     * @return 上岗向导结果
     */
    HrmEmployeeOnboardGuideRespVO getOnboardGuide(Long employeeId);

}
