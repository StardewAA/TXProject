package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产流转卡 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 生产流转卡 Response DTO")
@Data
public class MesProCardRespDTO {

    @Schema(description = "流转卡编号", example = "1024")
    private Long id;

    @Schema(description = "流转卡编码", example = "CARD-001")
    private String code;

    @Schema(description = "生产工单编号", example = "100")
    private Long workOrderId;

    @Schema(description = "生产工单编码", example = "WO-001")
    private String workOrderCode;

    @Schema(description = "生产工单名称", example = "生产工单-A")
    private String workOrderName;

    @Schema(description = "产品物料编号", example = "200")
    private Long itemId;

    @Schema(description = "产品物料编码", example = "P-001")
    private String itemCode;

    @Schema(description = "产品物料名称", example = "防腐管道 A")
    private String itemName;

    @Schema(description = "批次号", example = "BATCH-001")
    private String batchCode;

    @Schema(description = "流转数量", example = "100.00")
    private BigDecimal transferedQuantity;

    @Schema(description = "状态", example = "1")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
