package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverItemStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverItemTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - HRM 员工离职交接明细保存 Request VO")
@Data
public class HrmEmployeeQuitHandoverItemSaveReqVO {

    @Schema(description = "交接明细编号", example = "1")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "员工编号不能为空")
    private Long employeeId;

    @Schema(description = "物品类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "物品类型不能为空")
    @InEnum(value = HrmEmployeeQuitHandoverItemTypeEnum.class, message = "物品类型必须是 {value}")
    private Integer itemType;

    @Schema(description = "物品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "电焊机")
    @NotBlank(message = "物品名称不能为空")
    @Size(max = 100, message = "物品名称不能超过 100 个字符")
    private String itemName;

    @Schema(description = "数量", example = "1")
    @DecimalMin(value = "0", message = "数量不能小于 0")
    private BigDecimal quantity;

    @Schema(description = "单位", example = "台")
    @Size(max = 20, message = "单位不能超过 20 个字符")
    private String unit;

    @Schema(description = "交接状态：0待交回 1已交回 2缺失未交回 3无需交接", example = "0")
    @InEnum(value = HrmEmployeeQuitHandoverItemStatusEnum.class, message = "交接状态必须是 {value}")
    private Integer status;

    @Schema(description = "备注", example = "已当面点交")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
