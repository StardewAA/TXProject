package cn.iocoder.txgy.module.hrm.job.certificate;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateLedgerService;
import cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateWarningService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * HRM 证件状态与预警 Job
 *
 * 每日执行三步：重算证件状态、生成到期与复审预警、发送待通知预警。
 * 三步均幂等：状态按当日口径重算，预警按「证件 + 预警类型 + 预警日期」去重，
 * 通知失败只记录原因并累计重试次数，不阻断业务事实。
 *
 * @author OPENLAB BS
 */
@Component
public class HrmCertificateWarningJob {

    @Resource
    private HrmCertificateLedgerService certificateLedgerService;
    @Resource
    private HrmCertificateWarningService certificateWarningService;

    @XxlJob("hrmCertificateWarningJob")
    @TenantJob
    public String execute() {
        LocalDate today = LocalDate.now();
        int changedStatusCount = certificateLedgerService.refreshCertificateStatus(today);
        int createdWarningCount = certificateWarningService.scanAndGenerateWarnings(today);
        int notifiedCount = certificateWarningService.notifyPendingWarnings(today);
        return String.format("HRM 证件状态与预警：状态变更 %s 条，新增预警 %s 条，通知成功 %s 条",
                changedStatusCount, createdWarningCount, notifiedCount);
    }

}
