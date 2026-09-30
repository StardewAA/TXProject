package cn.iocoder.txgy.module.hrm.job.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractSignRecordService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HRM 合同「逾期未签」自动催办 Job（B4）
 *
 * <p>扫描超过 N 天仍处于「待签」的签署记录，自动累加催办次数并向员工后台账号发送待签站内信，
 * 让合同签署闭环不依赖 HR 手工盯列表。参数：逾期天数（默认 3），可通过 XXL-Job
 * handler 名 {@code hrmContractSignOverdueJob} 的 jobParam 覆盖。</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class HrmContractSignOverdueJob {

    private static final int DEFAULT_OVERDUE_DAYS = 3;
    /** 单次最多处理条数，避免一次拉全表 */
    private static final int BATCH_SIZE = 500;

    @Resource
    private HrmContractSignRecordService contractSignRecordService;

    @XxlJob("hrmContractSignOverdueJob")
    @TenantJob
    public String execute() {
        String param = XxlJobHelper.getJobParam();
        int overdueDays = StrUtil.isBlank(param) ? DEFAULT_OVERDUE_DAYS : Integer.parseInt(param.trim());
        LocalDateTime deadline = LocalDateTime.now().minusDays(Math.max(overdueDays, 1));

        HrmContractSignRecordPageReqVO reqVO = new HrmContractSignRecordPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(BATCH_SIZE);
        reqVO.setStatus(HrmContractSignStatusEnum.PENDING.getStatus());
        List<HrmContractSignRecordDO> pendings = contractSignRecordService.getSignRecordPage(reqVO).getList();

        // 只催办「创建时间早于截止日」的待签记录（列表按编号倒序，这里做时间过滤）
        List<Long> overdueIds = pendings.stream()
                .filter(item -> item.getCreateTime() != null && item.getCreateTime().isBefore(deadline))
                .map(HrmContractSignRecordDO::getId)
                .toList();
        if (overdueIds.isEmpty()) {
            return String.format("HRM 合同逾期未签催办：逾期阈值 %s 天，待签 %s 条，命中 0 条",
                    overdueDays, pendings.size());
        }
        int urged = contractSignRecordService.urgeSignRecords(overdueIds);
        log.info("[execute][合同逾期未签催办：阈值({})天，命中({})条，成功催办({})条]", overdueDays, overdueIds.size(), urged);
        return String.format("HRM 合同逾期未签催办：逾期阈值 %s 天，待签 %s 条，命中 %s 条，已催办 %s 条",
                overdueDays, pendings.size(), overdueIds.size(), urged);
    }

}
