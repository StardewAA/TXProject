package cn.iocoder.txgy.module.mes.service.wms.integration;

import cn.iocoder.txgy.module.mes.dal.dataobject.wms.integration.MesWmsIntegrationTaskDO;
import cn.iocoder.txgy.module.mes.enums.wms.MesWmsIntegrationOrderTypeEnum;

/**
 * MES↔WMS 集成服务
 *
 * @author OPENLAB BS
 */
public interface MesWmsIntegrationService {

    /**
     * 创建集成任务（业务单据审批通过时调用，幂等）
     *
     * 同一 (orderType, sourceOrderId) 已存在非取消任务时直接返回已有任务编号，不重复创建。
     * requestPayload 必须是最终可发送的 WMS 申请报文（JSON），由业务侧组装（含编码映射）。
     *
     * @param orderType       业务类型
     * @param sourceOrderId   MES 业务单据编号
     * @param sourceOrderNo   MES 业务单据号
     * @param businessChainNo 跨系统追踪链号
     * @param warehouseCode   目标 WMS 仓库编码（拆单后单仓）
     * @param requestPayload  WMS 申请报文（JSON 字符串）
     * @return 任务编号
     */
    Long createTask(MesWmsIntegrationOrderTypeEnum orderType, Long sourceOrderId, String sourceOrderNo,
                    String businessChainNo, String warehouseCode, String requestPayload);

    /** 定时处理：发送待发送任务 + 轮询已受理/执行中任务的结果（每 30 秒） */
    void processPendingTasks();

    /** 人工重试失败任务（重置状态与重试计数） */
    void retryTask(Long id);

    /** 查询某业务单据的最新集成任务 */
    MesWmsIntegrationTaskDO getTaskBySourceOrder(MesWmsIntegrationOrderTypeEnum orderType, Long sourceOrderId);

    /** 取消（物理删除）某业务单据的集成任务，便于重新创建（如缺料恢复领料） */
    void cancelTask(MesWmsIntegrationOrderTypeEnum orderType, Long sourceOrderId);

}
