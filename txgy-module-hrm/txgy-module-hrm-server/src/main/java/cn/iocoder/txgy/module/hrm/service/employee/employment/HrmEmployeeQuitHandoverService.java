package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverConfirmReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverItemSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverProgressRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverWaiveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitSettleReqVO;
import jakarta.validation.Valid;

/**
 * HRM 员工离职交接 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeQuitHandoverService {

    /**
     * 保存离职交接明细（无编号为新增，有编号为修改）
     *
     * @param reqVO 交接明细
     * @return 交接明细编号
     */
    Long saveHandoverItem(@Valid HrmEmployeeQuitHandoverItemSaveReqVO reqVO);

    /**
     * 删除离职交接明细
     *
     * @param id 交接明细编号
     */
    void deleteHandoverItem(Long id);

    /**
     * 获得离职交接进度（含明细）
     *
     * @param employeeId 员工编号
     * @return 交接进度
     */
    HrmEmployeeQuitHandoverProgressRespVO getHandoverProgress(Long employeeId);

    /**
     * 确认离职交接完成
     *
     * @param reqVO 确认请求
     */
    void confirmHandover(@Valid HrmEmployeeQuitHandoverConfirmReqVO reqVO);

    /**
     * 标记为「无需交接」
     *
     * @param reqVO 无需交接请求
     */
    void waiveHandover(@Valid HrmEmployeeQuitHandoverWaiveReqVO reqVO);

    /**
     * 校验离职交接已具备结论（已确认或无需交接），供离职生效卡控使用
     *
     * @param employeeId 员工编号
     * @param quitInfoId 离职信息编号
     */
    void validateHandoverFinished(Long employeeId, Long quitInfoId);

    /**
     * 确认离职工资已结清（含计薪截止日）
     *
     * @param reqVO 结算请求
     */
    void settleQuitSalary(@Valid HrmEmployeeQuitSettleReqVO reqVO);

    /**
     * 校验离职工资金额已结清，供再入职卡控使用
     *
     * @param employeeId 员工编号
     */
    void validateSalarySettled(Long employeeId);

}
