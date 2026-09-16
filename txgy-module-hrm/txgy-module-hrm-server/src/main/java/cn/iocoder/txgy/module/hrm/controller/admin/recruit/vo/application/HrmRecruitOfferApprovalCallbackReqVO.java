package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "HRM 招聘 Offer 审批回调请求")
public class HrmRecruitOfferApprovalCallbackReqVO {

    @NotNull(message = "Offer 编号不能为空")
    private Long offerId;

    @NotBlank(message = "流程实例编号不能为空")
    private String processInstanceId;

    @NotNull(message = "审批版本不能为空")
    private Integer approvalVersion;

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    @Size(max = 500, message = "审批意见不能超过 500 个字符")
    private String reason;
}
