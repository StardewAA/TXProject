package cn.iocoder.txgy.module.mes.dal.dataobject.wms.integration;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MES↔WMS 集成任务 DO
 *
 * 一行代表一次「向 WMS 申请出库/入库」的集成任务：
 * 业务单据审批通过 → 创建任务（携带最终报文）→ 定时任务发送/轮询 → 终态回写业务单据。
 *
 * @author OPENLAB BS
 */
@TableName("mes_wms_integration_task")
@KeySequence("mes_wms_integration_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesWmsIntegrationTaskDO extends TenantBaseDO {

    /** 编号 */
    @TableId
    private Long id;
    /** 业务类型（PRODUCTION_SHIPMENT/PRODUCTION_RECEIPT），见 MesWmsIntegrationOrderTypeEnum */
    private String orderType;
    /** MES 业务单据编号 */
    private Long sourceOrderId;
    /** MES 业务单据号 */
    private String sourceOrderNo;
    /** 跨系统追踪链号 */
    private String businessChainNo;
    /** 传输幂等键（同租户唯一） */
    private String requestId;
    /** 目标 WMS 仓库编码（拆单后单仓） */
    private String warehouseCode;
    /** 状态，见 MesWmsIntegrationTaskStatusEnum */
    private Integer status;
    /** 重试次数 */
    private Integer retryCount;
    /** 下次处理时间 */
    private LocalDateTime nextRetryTime;
    /** WMS 单据号（受理成功后返回） */
    private String wmsOrderNo;
    /** WMS 执行状态（0待执行 1成功 2出库失败） */
    private Integer executionStatus;
    /** WMS 完成数量 */
    private BigDecimal completedQuantity;
    /** 发送报文快照（JSON） */
    private String requestPayload;
    /** 结果报文快照（JSON） */
    private String resultPayload;
    /** 失败原因 */
    private String errorMessage;

}
