package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 撤回招聘应聘 Request VO")
@Data
public class HrmPortalRecruitWithdrawReqVO {
    @NotNull(message = "应聘编号不能为空")
    private Long id;
    @Size(max = 500, message = "撤回原因不能超过 500 个字符")
    private String reason;
}
