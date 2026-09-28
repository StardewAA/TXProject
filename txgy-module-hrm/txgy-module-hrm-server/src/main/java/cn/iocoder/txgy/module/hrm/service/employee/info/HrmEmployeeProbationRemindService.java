package cn.iocoder.txgy.module.hrm.service.employee.info;

import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;

import java.util.List;

/**
 * HRM 试用期到期提醒 Service 接口
 *
 * <p>用于把「试用期即将到期 → 应办理转正评估」这件事从人工盯日历变成系统提醒：
 * 既提供 HR 侧的待转正清单，也给员工（已绑定后台账号）发站内信提醒。</p>
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeProbationRemindService {

    /**
     * 默认提前提醒天数
     */
    int DEFAULT_REMIND_DAYS = 7;

    /**
     * 获得试用期即将到期的员工列表（在职、正式类型、未转正、试用期结束日期在 N 天内）
     *
     * @param days 提前天数，为空时按 {@link #DEFAULT_REMIND_DAYS}
     * @return 员工列表（按试用期结束日期升序）
     */
    List<HrmEmployeeDO> getExpiringEmployees(Integer days);

    /**
     * 给试用期即将到期的员工发送站内信提醒（未绑定后台账号的自动跳过）
     *
     * @param days 提前天数
     * @return 实际发送数量
     */
    int sendProbationReminders(Integer days);

}
