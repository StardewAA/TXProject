package cn.iocoder.txgy.module.member.controller.admin.platform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 平台账号人工身份归并 Request VO")
@Data
public class MemberPlatformAccountMergeReqVO {

    @Schema(description = "审核单号；须来自线下人工身份核验单", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "审核单号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{5,63}$", message = "审核单号格式不正确")
    private String reviewNo;

    @Schema(description = "目标平台账号编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标平台账号编号不能为空")
    private Long targetPlatformAccountId;

    @Schema(description = "本次核验选中的链接编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "链接编号不能为空")
    @Size(max = 50, message = "单次最多归并 50 条链接")
    private List<@NotNull(message = "链接编号不能为空") Long> linkIds;

    @Schema(description = "人工核验依据摘要（不得填写密码或验证码）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "审核说明不能为空")
    @Size(min = 10, max = 500, message = "审核说明长度为 10 到 500 个字符")
    private String reviewNote;
}
