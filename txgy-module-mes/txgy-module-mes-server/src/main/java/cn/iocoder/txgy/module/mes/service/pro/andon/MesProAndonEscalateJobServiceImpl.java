package cn.iocoder.txgy.module.mes.service.pro.andon;

import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.andon.MesProAndonRecordDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder.MesProWorkOrderMapper;
import cn.iocoder.txgy.module.mes.enums.pro.MesProAndonTriggerTypeEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 安灯超时升级 + 工单超期自动建单 定时任务。
 *
 * <p>职责：
 * 1. 扫描已达 SLA 仍未处置、未升级的安灯，标记升级并主动通知处置人；
 * 2. 扫描已超需求日期仍未完工的生产工单，自动创建「工单超期」安灯（按触发类型匹配配置，未配置则跳过）。
 * 跨租户数据统一扫描后逐条切租户处理，保证租户隔离正确。</p>
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
public class MesProAndonEscalateJobServiceImpl {

    @Resource
    private MesProAndonRecordService andonRecordService;
    @Resource
    private MesProWorkOrderMapper workOrderMapper;

    /**
     * 每 60 秒执行一次：先升级超时安灯，再扫描超期工单自动建单。
     */
    @Scheduled(fixedDelay = 60_000)
    public void execute() {
        escalateOverdueAndon();
        autoCreateWorkOrderOverdue();
    }

    private void escalateOverdueAndon() {
        LocalDateTime now = LocalDateTime.now();
        List<MesProAndonRecordDO> pending = TenantUtils.executeIgnore(
                () -> andonRecordService.getPendingEscalateList(now));
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (MesProAndonRecordDO record : pending) {
            try {
                TenantUtils.executeIgnore(() -> andonRecordService.processEscalate(record));
            } catch (Exception e) {
                log.error("[escalateOverdueAndon][安灯({}) 升级处理异常]", record.getId(), e);
            }
        }
    }

    private void autoCreateWorkOrderOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<MesProWorkOrderDO> overdueWorkOrders = TenantUtils.executeIgnore(() ->
                workOrderMapper.selectList(new LambdaQueryWrapperX<MesProWorkOrderDO>()
                        .notIn(MesProWorkOrderDO::getStatus,
                                MesProWorkOrderStatusEnum.FINISHED.getStatus(),
                                MesProWorkOrderStatusEnum.CANCELED.getStatus())
                        .lt(MesProWorkOrderDO::getRequestDate, now)));
        if (overdueWorkOrders == null || overdueWorkOrders.isEmpty()) {
            return;
        }
        for (MesProWorkOrderDO workOrder : overdueWorkOrders) {
            try {
                TenantUtils.executeIgnore(() ->
                        andonRecordService.createSystemAndonRecord(
                                MesProAndonTriggerTypeEnum.WO_OVERDUE.getType(),
                                null, workOrder.getId(), null,
                                "工单(" + workOrder.getCode() + ") 已超需求日期仍未完工"));
            } catch (Exception e) {
                log.error("[autoCreateWorkOrderOverdue][工单({}) 超期安灯创建异常]", workOrder.getId(), e);
            }
        }
    }

}
