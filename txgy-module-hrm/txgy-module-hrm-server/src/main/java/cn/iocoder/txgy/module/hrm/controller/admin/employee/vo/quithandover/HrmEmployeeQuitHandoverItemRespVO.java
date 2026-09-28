package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 员工离职交接明细 Response VO")
@Data
public class HrmEmployeeQuitHandoverItemRespVO {

    @Schema(description = "交接明细编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long employeeId;

    @Schema(description = "物品类型", example = "1")
    private Integer itemType;

    @Schema(description = "物品类型名称", example = "工器具")
    private String itemTypeName;

    @Schema(description = "物品名称", example = "电焊机")
    private String itemName;

    @Schema(description = "数量", example = "1")
    private BigDecimal quantity;

    @Schema(description = "单位", example = "台")
    private String unit;

    @Schema(description = "交接状态：0待交回 1已交回 2缺失未交回 3无需交接", example = "1")
    private Integer status;

    @Schema(description = "交接状态名称", example = "已交回")
    private String statusName;

    @Schema(description = "交回确认时间")
    private LocalDateTime confirmTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
