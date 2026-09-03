package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 招聘应聘创建 Request VO")
@Data
public class HrmRecruitApplicationCreateReqVO {
    @Schema(description = "候选人编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "候选人编号不能为空")
    private Long candidateId;
    @Schema(description = "职位编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "职位编号不能为空")
    private Long postId;
    @Schema(description = "简历版本编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "简历版本编号不能为空")
    private Long resumeId;
    private Long channelId;
    @Size(max = 500, message = "来源说明不能超过 500 个字符")
    private String sourceRemark;
}
