package cn.iocoder.txgy.module.hrm.service.employee.info;

import java.util.List;

/**
 * HRM 员工档案完整性校验 Service 接口
 *
 * <p>用于"转正"等关键动作前的档案齐备性卡控：档案不全不允许转正，
 * 避免出现"人已转正、合同和档案却没有"的情况。</p>
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeArchiveCheckService {

    /**
     * 获得缺失的档案项
     *
     * @param employeeId 员工编号
     * @return 缺失项名称列表（为空表示档案齐备）
     */
    List<String> getMissingItems(Long employeeId);

    /**
     * 校验档案齐备，不齐备时抛业务异常（提示缺哪些）
     *
     * @param employeeId 员工编号
     * @param action 动作名称（如"转正"），用于异常提示
     */
    void validateArchiveComplete(Long employeeId, String action);

}
