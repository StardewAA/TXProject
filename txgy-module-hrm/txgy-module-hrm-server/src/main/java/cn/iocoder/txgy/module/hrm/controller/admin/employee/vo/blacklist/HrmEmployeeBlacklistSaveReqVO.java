package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 员工黑名单 新增/修改 Request VO")
@Data
public class HrmEmployeeBlacklistSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "员工编号（可从员工档案选择）", example = "1024")
    private Long employeeId;

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @NotEmpty(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过 64 个字符")
    private String name;

    @Schema(description = "手机号（命中校验主要依据）", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800000000")
    @NotEmpty(message = "手机号不能为空")
    @Size(max = 32, message = "手机号不能超过 32 个字符")
    private String mobile;

    @Schema(description = "身份证号", example = "610100199001011234")
    @Size(max = 32, message = "身份证号不能超过 32 个字符")
    private String idCardNo;

    @Schema(description = "加入原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "高空作业未系安全带，严重安全违规")
    @NotEmpty(message = "加入原因不能为空")
    @Size(max = 500, message = "加入原因不能超过 500 个字符")
    private String reason;

    @Schema(description = "来源：1 HRM 手工登记，2 现场上报，3 其他", example = "1")
    private Integer source;

    @Schema(description = "加入时间")
    private LocalDateTime joinTime;

    @Schema(description = "解除时间")
    private LocalDateTime releaseTime;

    @Schema(description = "是否永久", example = "true")
    private Boolean permanentFlag;

    @Schema(description = "状态：1 生效，2 已解除", example = "1")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
