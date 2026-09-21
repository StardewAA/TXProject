package cn.iocoder.txgy.module.crm.util;

import java.time.LocalDateTime;

/**
 * CRM 跟进时间工具类
 *
 * 用于统一「写跟进」时，业务对象（客户/商机/线索/联系人/合同）上「最后跟进时间」的取值口径。
 *
 * @author OPENLAB BS
 */
public class CrmFollowUpTimeUtils {

    /**
     * 计算写入业务对象的「最后跟进时间」contactLastTime
     *
     * 口径：本次实际跟进时间 与 已有的最后跟进时间 取较大值。原因：
     *   1. 「最后跟进时间」= 实际和对方发生跟进的时间，是「多久没跟进」的判定依据
     *      （客户公海自动回收、待办已联系统计等都读它）；
     *   2. 允许补录历史跟进（例如今天补录 9/1 的拜访），此时不能把最后跟进时间往回拨，
     *      否则会把公海回收计时错误地重置，客户可能被提前/滞后回收；
     *   3. 未填写实际跟进时间时，兜底为当前时间，保持与历史行为一致（老前端不传该字段）。
     *
     * @param contactLastTime 已有的最后跟进时间，可为空（表示从未跟进过）
     * @param contactTime     本次实际跟进时间，可为空（视为当前时间）
     * @return 应该写入的最后跟进时间
     */
    public static LocalDateTime calculateContactLastTime(LocalDateTime contactLastTime, LocalDateTime contactTime) {
        LocalDateTime actualTime = contactTime != null ? contactTime : LocalDateTime.now();
        if (contactLastTime != null && contactLastTime.isAfter(actualTime)) {
            return contactLastTime;
        }
        return actualTime;
    }

}
