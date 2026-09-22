package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 个人简历版本固化 Request VO")
@Data
public class HrmPortalResumeVersionCreateReqVO {

    @Schema(description = "来源个人简历编号（在线简历草稿），不传则必须显式提供内容")
    private Long sourceResumeId;

    @Schema(description = "父版本编号，用于记录版本血缘")
    private Long parentVersionId;

    @Schema(description = "来源目标职位编号")
    private Long sourcePostId;

    @Schema(description = "创建方式：1 手动固化，2 确认优化建议")
    private Integer createType;

    @Schema(description = "修改摘要")
    @Size(max = 500, message = "修改摘要不能超过 500 个字符")
    private String changeSummary;

    @Schema(description = "最终确认后的简历内容；不传时从来源草稿固化")
    @Size(max = 200_000, message = "简历内容过长")
    private String content;

    @Schema(description = "幂等键，同一用户重复提交不会生成多个版本")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;
}
