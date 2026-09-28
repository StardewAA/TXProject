package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 生产工单 Response DTO")
@Data
public class MesProWorkOrderRespDTO {

    @Schema(description = "工单编号", example = "1024")
    private Long id;

    @Schema(description = "工单编码", example = "WO-001")
    private String code;

    @Schema(description = "工单名称", example = "生产工单-A")
    private String name;

    @Schema(description = "工单类型", example = "1")
    private Integer type;

    @Schema(description = "来源类型", example = "1")
    private Integer orderSourceType;

    @Schema(description = "来源单据编号", example = "SO-001")
    private String orderSourceCode;

    @Schema(description = "产品编号", example = "100")
    private Long productId;

    @Schema(description = "产品名称", example = "防腐管道 A")
    private String productName;

    @Schema(description = "产品编码", example = "P-001")
    private String productCode;

    @Schema(description = "生产数量", example = "100.00")
    private BigDecimal quantity;

    @Schema(description = "已生产数量", example = "50.00")
    private BigDecimal quantityProduced;

    @Schema(description = "调整数量", example = "0")
    private BigDecimal quantityChanged;

    @Schema(description = "已排产数量", example = "80.00")
    private BigDecimal quantityScheduled;

    @Schema(description = "客户名称", example = "客户 A")
    private String clientName;

    @Schema(description = "批次号", example = "BATCH-001")
    private String batchCode;

    @Schema(description = "需求日期")
    private LocalDateTime requestDate;

    @Schema(description = "完成时间")
    private LocalDateTime finishDate;

    @Schema(description = "取消时间")
    private LocalDateTime cancelDate;

    @Schema(description = "工单状态", example = "1")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
