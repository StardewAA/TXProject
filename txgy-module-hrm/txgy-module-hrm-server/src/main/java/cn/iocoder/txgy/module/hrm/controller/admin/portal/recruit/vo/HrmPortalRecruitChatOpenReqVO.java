package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "员工端 - 打开招聘沟通会话 Request VO")
@Data
public class HrmPortalRecruitChatOpenReqVO {
    @Schema(description = "招聘职位编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "招聘职位编号不能为空")
    private Long postId;
}
