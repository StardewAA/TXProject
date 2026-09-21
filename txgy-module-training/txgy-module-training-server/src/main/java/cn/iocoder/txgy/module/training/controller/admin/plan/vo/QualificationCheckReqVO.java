package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - 上岗资格核查 Request VO（姓名 + 手机号）")
@Data
public class QualificationCheckReqVO {

    @Schema(description = "人员姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotEmpty(message = "姓名不能为空")
    private String userName;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotEmpty(message = "手机号不能为空")
    private String mobile;

}
