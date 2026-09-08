package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 招聘职位状态修改 Request VO")
@Data
public class HrmRecruitPostStatusReqVO {

    @Schema(description = "招聘职位编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "招聘职位编号不能为空")
    private Long id;

    @Schema(description = "职位状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "职位状态不能为空")
    @InEnum(value = HrmRecruitPostStatusEnum.class, message = "职位状态必须是 {value}")
    private Integer status;

    @Schema(description = "状态变更原因（暂停、关闭时必填）", example = "岗位暂停")
    @Size(max = 255, message = "状态变更原因不能超过 255 个字符")
    private String stopReason;

    @AssertTrue(message = "暂停或关闭职位时，状态变更原因不能为空")
    @JsonIgnore
    public boolean isStopReasonValid() {
        return !HrmRecruitPostStatusEnum.PAUSED.getStatus().equals(status)
                && !HrmRecruitPostStatusEnum.CLOSED.getStatus().equals(status)
                || StrUtil.isNotBlank(stopReason);
    }

}
