package cn.iocoder.txgy.module.hrm.controller.admin.employment.vo;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.employment.HrmSalaryModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 用工形式创建/修改 Request VO")
@Data
public class HrmEmploymentTypeSaveReqVO {

    @Schema(description = "编号（新增留空）", example = "1")
    private Long id;

    @Schema(description = "用工形式名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "临时工")
    @NotEmpty(message = "用工形式名称不能为空")
    @Size(max = 64, message = "用工形式名称长度不能超过 64 个字符")
    private String name;

    @Schema(description = "用工形式编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "TEMPORARY")
    @NotEmpty(message = "用工形式编码不能为空")
    @Size(max = 32, message = "用工形式编码长度不能超过 32 个字符")
    private String code;

    @Schema(description = "默认合同模板编号", example = "5")
    private Long contractTemplateId;

    @Schema(description = "默认计薪方式：1 月薪，2 日薪，3 计件，4 月薪+计件", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "默认计薪方式不能为空")
    @InEnum(value = HrmSalaryModeEnum.class, message = "默认计薪方式必须是 {value}")
    private Integer salaryMode;

    @Schema(description = "是否按工日统计出勤", example = "true")
    private Boolean needWorkDay;

    @Schema(description = "是否要求人脸打卡", example = "true")
    private Boolean needFaceClock;

    @Schema(description = "是否参与自动转正", example = "false")
    private Boolean joinRegular;

    @Schema(description = "显示顺序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0 启用，1 停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注", example = "项目制短工，以完成一定工作任务为期限")
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

}
