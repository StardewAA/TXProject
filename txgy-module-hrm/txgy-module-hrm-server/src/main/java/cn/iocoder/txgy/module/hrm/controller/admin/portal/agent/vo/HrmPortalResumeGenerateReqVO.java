package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工端 AI 简历生成请求。
 *
 * <p>只接收求职方向与经历描述，不接收姓名、电话、邮箱、年龄等身份属性；
 * 联系方式由前端在生成后从个人资料合并，避免把无关敏感信息送入模型。</p>
 */
@Schema(description = "员工端 - AI 简历生成 Request VO")
@Data
public class HrmPortalResumeGenerateReqVO {

    @Schema(description = "期望职位")
    @NotBlank(message = "期望职位不能为空")
    @Size(max = 100, message = "期望职位不能超过 100 个字符")
    private String targetRole;

    @Schema(description = "期望城市")
    @Size(max = 100, message = "期望城市不能超过 100 个字符")
    private String targetCity;

    @Schema(description = "教育经历")
    @Size(max = 2000, message = "教育经历不能超过 2000 个字符")
    private String education;

    @Schema(description = "工作或实习经历")
    @Size(max = 8000, message = "工作或实习经历不能超过 8000 个字符")
    private String experience;

    @Schema(description = "项目经历")
    @Size(max = 6000, message = "项目经历不能超过 6000 个字符")
    private String projects;

    @Schema(description = "专业技能")
    @Size(max = 3000, message = "专业技能不能超过 3000 个字符")
    private String skills;

    @Schema(description = "个人优势")
    @Size(max = 3000, message = "个人优势不能超过 3000 个字符")
    private String strengths;

    @Schema(description = "幂等键，重复提交返回同一任务")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;
}
