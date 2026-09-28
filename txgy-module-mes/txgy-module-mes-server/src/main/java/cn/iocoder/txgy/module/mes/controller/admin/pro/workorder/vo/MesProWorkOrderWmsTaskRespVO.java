package cn.iocoder.txgy.module.mes.controller.admin.pro.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 生产工单 WMS 集成任务状态 Response VO
 *
 * 用于工单列表/详情展示该工单的领料、完工入库在 WMS 侧的流转状态。
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 生产工单 WMS 集成任务状态 Response VO")
@Data
public class MesProWorkOrderWmsTaskRespVO {

    @Schema(description = "MES 工单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long sourceOrderId;

    @Schema(description = "MES 工单编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "WO202609210002")
    private String sourceOrderNo;

    @Schema(description = "集成类型：PRODUCTION_SHIPMENT 领料 / PRODUCTION_RECEIPT 完工入库", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderType;

    @Schema(description = "任务状态：0 待发送 1 已受理 2 执行中 3 成功 4 失败 5 拒绝 6 取消", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer status;

    @Schema(description = "WMS 单据编号", example = "WMS-MES-WO202609210002")
    private String wmsOrderNo;

    @Schema(description = "WMS 执行状态：0 待执行 1 成功", example = "1")
    private Integer executionStatus;

    @Schema(description = "完成数量", example = "120")
    private BigDecimal completedQuantity;

    @Schema(description = "最近一次错误信息")
    private String errorMessage;

}
