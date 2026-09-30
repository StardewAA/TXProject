package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 附件简历结构化请求。
 *
 * <p>入参是前端从附件里提取出的**整段正文**（PDF/DOCX 的文字层，扫描件先经多模态转写）。
 * 服务端会把它按纯文本处理：走脱敏、长度上限与失败关闭，再交给模型整理成六个字段。</p>
 *
 * <p>不接收文件名之外的任何元数据 —— 归属人由登录态决定。</p>
 */
@Schema(description = "个人端 - 附件简历结构化 Request VO")
@Data
public class HrmPortalResumeStructureReqVO {

    @Schema(description = "从附件中提取出的简历正文")
    @NotBlank(message = "简历正文不能为空")
    @Size(max = 8000, message = "简历正文不能超过 8000 个字符")
    private String text;

    @Schema(description = "幂等键，重复提交返回同一任务")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;
}
