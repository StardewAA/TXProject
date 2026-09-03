package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产任务 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 生产任务 Response DTO")
@Data
public class MesProTaskRespDTO {

    @Schema(description = "任务编号", example = "1024")
    private Long id;

    @Schema(description = "任务编码", example = "PT202503150001")
    private String code;

    @Schema(description = "任务名称", example = "注塑任务")
    private String name;

    @Schema(description = "生产工单编号", example = "1")
    private Long workOrderId;

    @Schema(description = "工单编码", example = "WO-001")
    private String workOrderCode;

    @Schema(description = "工作站编号", example = "1")
    private Long workstationId;

    @Schema(description = "工作站名称", example = "注塑工作站")
    private String workstationName;

    @Schema(description = "工序编号", example = "1")
    private Long processId;

    @Schema(description = "工序名称", example = "注塑")
    private String processName;

    @Schema(description = "产品物料编号", example = "100")
    private Long itemId;

    @Schema(description = "产品编码", example = "P-001")
    private String itemCode;

    @Schema(description = "产品名称", example = "电路板")
    private String itemName;

    @Schema(description = "排产数量", example = "100.00")
    private BigDecimal quantity;

    @Schema(description = "已生产数量", example = "50.00")
    private BigDecimal producedQuantity;

    @Schema(description = "合格品数量", example = "48.00")
    private BigDecimal qualifyQuantity;

    @Schema(description = "不良品数量", example = "2.00")
    private BigDecimal unqualifyQuantity;

    @Schema(description = "开始生产时间")
    private LocalDateTime startTime;

    @Schema(description = "结束生产时间")
    private LocalDateTime endTime;

    @Schema(description = "需求日期")
    private LocalDateTime requestDate;

    @Schema(description = "完成日期")
    private LocalDateTime finishDate;

    @Schema(description = "任务状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
