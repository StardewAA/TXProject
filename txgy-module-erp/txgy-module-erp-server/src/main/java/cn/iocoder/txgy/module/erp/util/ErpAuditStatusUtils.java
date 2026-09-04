package cn.iocoder.txgy.module.erp.util;

import cn.hutool.core.lang.Assert;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;

/** ERP 与 BPM 审批状态转换工具。 */
public final class ErpAuditStatusUtils {

    private ErpAuditStatusUtils() {
    }

    public static Integer convertBpmResultToAuditStatus(Integer bpmStatus) {
        Integer auditStatus = BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(bpmStatus)
                ? ErpAuditStatus.APPROVE.getStatus()
                : BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(bpmStatus)
                ? ErpAuditStatus.REJECT.getStatus()
                : BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(bpmStatus)
                ? ErpAuditStatus.CANCEL.getStatus() : null;
        Assert.notNull(auditStatus, "BPM 审批结果({})转换失败", bpmStatus);
        return auditStatus;
    }

}
