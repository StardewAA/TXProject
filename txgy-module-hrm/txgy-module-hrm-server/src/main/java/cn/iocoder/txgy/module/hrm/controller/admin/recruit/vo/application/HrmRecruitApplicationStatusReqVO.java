package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 招聘应聘状态操作 Request VO")
@Data
public class HrmRecruitApplicationStatusReqVO {
    @NotNull(message = "应聘编号不能为空")
    private Long id;
    @Size(max = 500, message = "操作原因不能超过 500 个字符")
    private String reason;
}
