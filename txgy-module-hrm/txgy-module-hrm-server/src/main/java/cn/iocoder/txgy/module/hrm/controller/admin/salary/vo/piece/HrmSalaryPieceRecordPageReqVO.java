package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - HRM 计件计量明细分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmSalaryPieceRecordPageReqVO extends PageParam {

    @Schema(description = "员工编号", example = "1")
    private Long employeeId;

    @Schema(description = "年", example = "2026")
    private Integer year;

    @Schema(description = "月", example = "9")
    private Integer month;

    @Schema(description = "工程量/工序名称（模糊）", example = "保温")
    private String itemName;

}
