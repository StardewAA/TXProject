package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quitinfo.HrmEmployeeQuitInfoSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeQuitInfoDO;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * HRM 员工离职信息 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeQuitInfoService {

    /**
     * 保存员工离职信息
     *
     * @param saveReqVO 离职信息
     * @return 离职信息编号
     */
    Long saveEmployeeQuitInfo(@Valid HrmEmployeeQuitInfoSaveReqVO saveReqVO);

    /**
     * 以独立事务保存员工离职信息
     *
     * <p>用于「立即离职」这类需要先落离职单、再校验交接完成度的场景：
     * 若与调用方共用事务，交接校验抛异常时离职单会被一并回滚，
     * 导致交接页拿不到离职单、无法登记交接明细（互为前置的死锁）。</p>
     *
     * @param saveReqVO 离职信息
     * @return 离职信息编号
     */
    Long saveEmployeeQuitInfoIndependently(@Valid HrmEmployeeQuitInfoSaveReqVO saveReqVO);

    /**
     * 删除员工离职信息
     *
     * @param employeeId 员工编号
     */
    void deleteEmployeeQuitInfo(Long employeeId);

    /**
     * 获得员工离职信息
     *
     * @param employeeId 员工编号
     * @return 离职信息
     */
    HrmEmployeeQuitInfoDO getQuitInfoByEmployeeId(Long employeeId);

    /**
     * 获得员工离职信息列表
     *
     * @param employeeIds 员工编号集合
     * @return 员工离职信息列表
     */
    List<HrmEmployeeQuitInfoDO> getQuitInfoListByEmployeeIds(Collection<Long> employeeIds);

    /**
     * 校验员工离职信息是否存在
     *
     * @param employeeId 员工编号
     * @return 离职信息
     */
    @SuppressWarnings("UnusedReturnValue")
    HrmEmployeeQuitInfoDO validateQuitInfoByEmployeeId(Long employeeId);

    /**
     * 获得到期的离职信息列表
     *
     * @param deadlineTime 截止时间
     * @return 离职信息列表
     */
    List<HrmEmployeeQuitInfoDO> getDueQuitInfoList(LocalDateTime deadlineTime);

}
