package cn.iocoder.txgy.module.mes.service.wms.integration;

import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationRequestRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wms.integration.MesWmsIntegrationTaskDO;
import cn.iocoder.txgy.module.mes.dal.mysql.wms.integration.MesWmsIntegrationTaskMapper;
import cn.iocoder.txgy.module.mes.enums.wms.MesWmsIntegrationOrderTypeEnum;
import cn.iocoder.txgy.module.mes.enums.wms.MesWmsIntegrationTaskStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MES↔WMS 集成服务实现
 *
 * 复用「事件落任务表 + 定时轮询 + 失败重试」范式（同 ERP 同步 / 报工审批任务）。
 * 每条任务独立处理，单条失败不影响同批其他任务；任务处理包裹在租户上下文中执行。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
public class MesWmsIntegrationServiceImpl implements MesWmsIntegrationService {

    /** 最大自动重试次数，超过后置失败等待人工处理 */
    private static final int MAX_RETRY_COUNT = 10;
    /** 重试间隔基数（分钟），按 2^n 退避 */
    private static final int RETRY_INTERVAL_MINUTES = 2;
    /** 每轮处理的任务上限 */
    private static final int BATCH_LIMIT = 50;

    private final MesWmsIntegrationTaskMapper taskMapper;
    private final WmsIntegrationApi wmsApi;
    private final List<MesWmsIntegrationResultHandler> resultHandlers;
    private final boolean enabled;

    public MesWmsIntegrationServiceImpl(
            MesWmsIntegrationTaskMapper taskMapper,
            WmsIntegrationApi wmsApi,
            List<MesWmsIntegrationResultHandler> resultHandlers,
            @Value("${mes.wms.integration.enabled:false}") boolean enabled) {
        this.taskMapper = taskMapper;
        this.wmsApi = wmsApi;
        this.resultHandlers = resultHandlers == null ? List.of() : resultHandlers;
        this.enabled = enabled;
    }

    @Override
    public Long createTask(MesWmsIntegrationOrderTypeEnum orderType, Long sourceOrderId, String sourceOrderNo,
                           String businessChainNo, String warehouseCode, String requestPayload) {
        // 1. 幂等：同业务单据已有未取消任务时，不重复创建
        MesWmsIntegrationTaskDO exist = taskMapper.selectBySourceOrder(orderType.getCode(), sourceOrderId);
        if (exist != null && !Objects.equals(exist.getStatus(), MesWmsIntegrationTaskStatusEnum.CANCELLED.getStatus())) {
            return exist.getId();
        }
        String requestId = buildRequestId(orderType, sourceOrderNo);
        // 2. 插入任务（requestId 唯一键兜底并发）
        try {
            MesWmsIntegrationTaskDO task = MesWmsIntegrationTaskDO.builder()
                    .orderType(orderType.getCode())
                    .sourceOrderId(sourceOrderId)
                    .sourceOrderNo(sourceOrderNo)
                    .businessChainNo(businessChainNo)
                    .requestId(requestId)
                    .warehouseCode(warehouseCode)
                    .status(MesWmsIntegrationTaskStatusEnum.PENDING_SEND.getStatus())
                    .retryCount(0)
                    .requestPayload(requestPayload)
                    .build();
            taskMapper.insert(task);
            return task.getId();
        } catch (DuplicateKeyException ex) {
            // 并发下同 requestId 已插入，返回已有任务
            MesWmsIntegrationTaskDO task = taskMapper.selectBySourceOrder(orderType.getCode(), sourceOrderId);
            return task == null ? null : task.getId();
        }
    }

    @Override
    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void processPendingTasks() {
        if (!enabled) {
            return;
        }
        // 1. 发送阶段
        List<MesWmsIntegrationTaskDO> pendingList = taskMapper.selectPendingSendList(BATCH_LIMIT);
        for (MesWmsIntegrationTaskDO task : pendingList) {
            processWithTenant(task, () -> sendTask(task));
        }
        // 2. 轮询阶段
        List<MesWmsIntegrationTaskDO> acceptedList = taskMapper.selectAcceptedOrExecutingList(BATCH_LIMIT);
        for (MesWmsIntegrationTaskDO task : acceptedList) {
            processWithTenant(task, () -> pollTask(task));
        }
    }

    @Override
    public void retryTask(Long id) {
        taskMapper.update(null, new LambdaUpdateWrapper<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getId, id)
                .set(MesWmsIntegrationTaskDO::getStatus, MesWmsIntegrationTaskStatusEnum.PENDING_SEND.getStatus())
                .set(MesWmsIntegrationTaskDO::getRetryCount, 0)
                .set(MesWmsIntegrationTaskDO::getNextRetryTime, LocalDateTime.now())
                .set(MesWmsIntegrationTaskDO::getErrorMessage, null));
    }

    @Override
    public MesWmsIntegrationTaskDO getTaskBySourceOrder(MesWmsIntegrationOrderTypeEnum orderType, Long sourceOrderId) {
        return taskMapper.selectBySourceOrder(orderType.getCode(), sourceOrderId);
    }

    @Override
    public void cancelTask(MesWmsIntegrationOrderTypeEnum orderType, Long sourceOrderId) {
        // 物理删除旧任务：createTask 以 requestId(=MES-MR-工单号-v1) 唯一键兜底，
        // 仅置 CANCELLED 会因唯一键冲突无法重建，故恢复领料时需物理删除后重新建任务。
        MesWmsIntegrationTaskDO task = taskMapper.selectBySourceOrder(orderType.getCode(), sourceOrderId);
        if (task != null) {
            taskMapper.deleteById(task.getId());
        }
    }

    // ==================== 发送阶段 ====================

    private void sendTask(MesWmsIntegrationTaskDO task) {
        MesWmsIntegrationOrderTypeEnum orderType = MesWmsIntegrationOrderTypeEnum.valueOf(task.getOrderType());
        WmsOrderRequestDTO payload;
        try {
            payload = JSONUtil.toBean(task.getRequestPayload(), WmsOrderRequestDTO.class);
        } catch (Exception ex) {
            failTask(task, "请求报文解析失败: " + ex.getMessage());
            return;
        }
        CommonResult<WmsIntegrationAcceptedRespDTO> resp;
        try {
            resp = orderType == MesWmsIntegrationOrderTypeEnum.PRODUCTION_SHIPMENT
                    ? wmsApi.createProductionShipment(payload)
                    : wmsApi.createProductionReceipt(payload);
        } catch (Exception ex) {
            scheduleRetry(task, "网络异常: " + ex.getMessage());
            return;
        }
        // 1. 校验业务码：code!=0 视为失败，不只看 HTTP 200
        if (resp == null || !Objects.equals(resp.getCode(), 0)) {
            String msg = resp == null ? "无响应" : resp.getMsg();
            int httpLikeCode = resp == null ? 500 : resp.getCode();
            // 不可重试类错误（参数/冲突/租户不匹配/功能未启用）直接置失败
            if (isNonRetryableBusinessCode(httpLikeCode)) {
                failTask(task, "WMS 受理失败: " + msg);
            } else {
                scheduleRetry(task, "WMS 受理失败: " + msg);
            }
            return;
        }
        // 2. 受理成功：记录 WMS 单号，进入轮询阶段
        WmsIntegrationAcceptedRespDTO data = resp.getData();
        boolean accepted = data != null && Boolean.TRUE.equals(data.getAccepted());
        if (!accepted) {
            scheduleRetry(task, "WMS 未受理该申请");
            return;
        }
        taskMapper.update(null, new LambdaUpdateWrapper<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getId, task.getId())
                .set(MesWmsIntegrationTaskDO::getStatus, MesWmsIntegrationTaskStatusEnum.ACCEPTED.getStatus())
                .set(MesWmsIntegrationTaskDO::getWmsOrderNo, data.getTargetOrderNo())
                .set(MesWmsIntegrationTaskDO::getResultPayload, JSONUtil.toJsonStr(data)));
        log.info("[sendTask][任务({}) 单号({}) 已受理，WMS 单号({})]", task.getId(), task.getSourceOrderNo(), data.getTargetOrderNo());
    }

    // ==================== 轮询阶段 ====================

    private void pollTask(MesWmsIntegrationTaskDO task) {
        CommonResult<WmsIntegrationRequestRespDTO> resp;
        try {
            resp = wmsApi.getOrderExecution(task.getTenantId(), task.getSourceOrderNo(), task.getOrderType());
        } catch (Exception ex) {
            scheduleRetry(task, "查询执行结果网络异常: " + ex.getMessage());
            return;
        }
        if (resp == null || !Objects.equals(resp.getCode(), 0)) {
            scheduleRetry(task, "查询执行结果失败: " + (resp == null ? "无响应" : resp.getMsg()));
            return;
        }
        WmsIntegrationRequestRespDTO data = resp.getData();
        if (data == null) {
            scheduleRetry(task, "WMS 尚无该来源单的执行记录");
            return;
        }
        String processingStatus = data.getProcessingStatus();
        // 1. 非终态：维持执行中，等待下次轮询
        if (processingStatus == null || processingStatus.startsWith("DRAFT")
                || processingStatus.equals("WAITING_APPROVAL") || processingStatus.equals("WAITING_EXECUTION")) {
            setExecuting(task, data);
            return;
        }
        // 2. 终态映射
        MesWmsIntegrationTaskStatusEnum terminal;
        switch (processingStatus) {
            case "SUCCESS":
                terminal = MesWmsIntegrationTaskStatusEnum.SUCCESS;
                break;
            case "EXECUTION_FAILED":
                terminal = MesWmsIntegrationTaskStatusEnum.FAILED;
                break;
            case "REJECTED":
                terminal = MesWmsIntegrationTaskStatusEnum.REJECTED;
                break;
            case "CANCELLED":
                terminal = MesWmsIntegrationTaskStatusEnum.CANCELLED;
                break;
            default:
                scheduleRetry(task, "未知 WMS 处理状态: " + processingStatus);
                return;
        }
        // 3. 终态落库
        Map<String, Object> resultData = data.getResultData();
        Integer executionStatus = resultData == null ? null : toInt(resultData.get("executionStatus"));
        BigDecimal completed = null;
        if (resultData != null && resultData.get("completedQuantity") != null) {
            completed = new BigDecimal(String.valueOf(resultData.get("completedQuantity")));
        }
        taskMapper.update(null, new LambdaUpdateWrapper<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getId, task.getId())
                .set(MesWmsIntegrationTaskDO::getStatus, terminal.getStatus())
                .set(MesWmsIntegrationTaskDO::getExecutionStatus, executionStatus)
                .set(MesWmsIntegrationTaskDO::getCompletedQuantity, completed)
                .set(MesWmsIntegrationTaskDO::getResultPayload, JSONUtil.toJsonStr(data)));
        log.info("[pollTask][任务({}) 单号({}) 到达终态({})]", task.getId(), task.getSourceOrderNo(), processingStatus);
        // 4. 分发业务终态回调（异常隔离，不影响状态已落库的事实）
        dispatchTerminal(task, processingStatus, resultData);
    }

    private void dispatchTerminal(MesWmsIntegrationTaskDO task, String wmsStatus, Map<String, Object> resultData) {
        MesWmsIntegrationOrderTypeEnum orderType = MesWmsIntegrationOrderTypeEnum.valueOf(task.getOrderType());
        for (MesWmsIntegrationResultHandler handler : resultHandlers) {
            if (handler.supportedOrderType() != orderType) {
                continue;
            }
            try {
                handler.onTerminal(task, wmsStatus, resultData);
            } catch (Exception ex) {
                log.error("[dispatchTerminal][任务({}) 终态回调处理失败，订单类型({})]",
                        task.getId(), orderType, ex);
            }
        }
    }

    // ==================== 公共方法 ====================

    /** 在租户上下文中处理任务（定时线程无租户，需显式设置），单条异常隔离 */
    private void processWithTenant(MesWmsIntegrationTaskDO task, Runnable runnable) {
        try {
            TenantUtils.execute(task.getTenantId(), runnable::run);
        } catch (BadSqlGrammarException ex) {
            // 集成任务表尚未建表时静默等待，不刷爆日志
            log.debug("[processWithTenant][集成任务表可能未创建，跳过本轮处理]");
        } catch (Exception ex) {
            log.error("[processWithTenant][任务({}) 处理异常]", task.getId(), ex);
        }
    }

    /** 可重试失败：退避后重新处理；超过上限置失败 */
    private void scheduleRetry(MesWmsIntegrationTaskDO task, String error) {
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (retryCount > MAX_RETRY_COUNT) {
            failTask(task, error + "（超过最大重试次数 " + MAX_RETRY_COUNT + "）");
            return;
        }
        LocalDateTime next = LocalDateTime.now().plusMinutes((long) RETRY_INTERVAL_MINUTES * (1L << Math.min(retryCount, 6)));
        taskMapper.update(null, new LambdaUpdateWrapper<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getId, task.getId())
                .set(MesWmsIntegrationTaskDO::getRetryCount, retryCount)
                .set(MesWmsIntegrationTaskDO::getNextRetryTime, next)
                .set(MesWmsIntegrationTaskDO::getErrorMessage, truncate(error)));
        log.warn("[scheduleRetry][任务({}) 第{}次重试已安排，原因({})]", task.getId(), retryCount, error);
    }

    private void failTask(MesWmsIntegrationTaskDO task, String error) {
        taskMapper.update(null, new LambdaUpdateWrapper<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getId, task.getId())
                .set(MesWmsIntegrationTaskDO::getStatus, MesWmsIntegrationTaskStatusEnum.FAILED.getStatus())
                .set(MesWmsIntegrationTaskDO::getErrorMessage, truncate(error)));
        log.error("[failTask][任务({}) 置为失败，单号({})，原因({})]", task.getId(), task.getSourceOrderNo(), error);
    }

    private void setExecuting(MesWmsIntegrationTaskDO task, WmsIntegrationRequestRespDTO data) {
        Integer executionStatus = null;
        Map<String, Object> resultData = data.getResultData();
        if (resultData != null && resultData.get("executionStatus") != null) {
            executionStatus = toInt(resultData.get("executionStatus"));
        }
        taskMapper.update(null, new LambdaUpdateWrapper<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getId, task.getId())
                .set(MesWmsIntegrationTaskDO::getStatus, MesWmsIntegrationTaskStatusEnum.EXECUTING.getStatus())
                .set(MesWmsIntegrationTaskDO::getExecutionStatus, executionStatus)
                .set(MesWmsIntegrationTaskDO::getResultPayload, JSONUtil.toJsonStr(data)));
    }

    private static String buildRequestId(MesWmsIntegrationOrderTypeEnum orderType, String sourceOrderNo) {
        String prefix = orderType == MesWmsIntegrationOrderTypeEnum.PRODUCTION_SHIPMENT ? "MES-MR" : "MES-FG";
        String requestId = prefix + "-" + sourceOrderNo + "-v1";
        return requestId.length() > 64 ? requestId.substring(0, 64) : requestId;
    }

    private static boolean isNonRetryableBusinessCode(int code) {
        // 400 参数错误 / 401 403 鉴权 / 409 冲突类业务码（1060900000/1060900009/1060900024）
        return code == 400 || code == 401 || code == 403 || code == 409
                || code == 1060900000 || code == 1060900009 || code == 1060900024;
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }

}
