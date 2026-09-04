package cn.iocoder.txgy.module.mes.api.pro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工单 Response DTO
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 生产报工单 Response DTO")
@Data
public class MesProFeedbackRespDTO {

    @Schema(description = "报工编号", example = "1024")
    private Long id;

    @Schema(description = "报工单编号", example = "FB202503160001")
    private String code;

    @Schema(description = "报工类型", example = "1")
    private Integer type;

    @Schema(description = "报工途径", example = "PC")
    private String channel;

    @Schema(description = "报工时间")
    private LocalDateTime feedbackTime;

    @Schema(description = "工作站编号", example = "1")
    private Long workstationId;

    @Schema(description = "工作站名称", example = "注塑工作站")
    private String workstationName;

    @Schema(description = "工序编号", example = "1")
    private Long processId;

    @Schema(description = "工序名称", example = "注塑")
    private String processName;

    @Schema(description = "生产工单编号", example = "1")
    private Long workOrderId;

    @Schema(description = "工单编码", example = "MO202503120008")
    private String workOrderCode;

    @Schema(description = "工单名称", example = "博世螺丝刀")
    private String workOrderName;

    @Schema(description = "生产任务编号", example = "1")
    private Long taskId;

    @Schema(description = "任务编码", example = "PT202503150001")
    private String taskCode;

    @Schema(description = "产品物料编号", example = "75")
    private Long itemId;

    @Schema(description = "物料编码", example = "I-075")
    private String itemCode;

    @Schema(description = "物料名称", example = "博世螺丝刀")
    private String itemName;

    @Schema(description = "排产数量", example = "5000.00")
    private BigDecimal scheduledQuantity;

    @Schema(description = "本次报工数量", example = "500.00")
    private BigDecimal feedbackQuantity;

    @Schema(description = "合格品数量", example = "490.00")
    private BigDecimal qualifiedQuantity;

    @Schema(description = "不良品数量", example = "10.00")
    private BigDecimal unqualifiedQuantity;

    @Schema(description = "待检测数量", example = "0")
    private BigDecimal uncheckQuantity;

    @Schema(description = "工废数量", example = "6.00")
    private BigDecimal laborScrapQuantity;

    @Schema(description = "料废数量", example = "4.00")
    private BigDecimal materialScrapQuantity;

    @Schema(description = "其他废品数量", example = "0")
    private BigDecimal otherScrapQuantity;

    @Schema(description = "报工用户编号", example = "1")
    private Long feedbackUserId;

    @Schema(description = "报工人昵称", example = "张三")
    private String feedbackUserNickname;

    @Schema(description = "审核人昵称", example = "李四")
    private String approveUserNickname;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
