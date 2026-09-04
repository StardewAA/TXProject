package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "学员端 - 文档翻页上报 Request VO")
@Data
public class DocPageReqVO {

    @Schema(description = "学习会话 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "学习会话不能为空")
    private Long sessionId;

    @Schema(description = "当前页（从 1 开始）", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "当前页不能为空")
    @Min(value = 1, message = "当前页最小为 1")
    private Integer pageNo;

    @Schema(description = "文档总页数", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "文档总页数不能为空")
    @Min(value = 1, message = "文档总页数最小为 1")
    @Max(value = 5000, message = "文档总页数过大")
    private Integer totalPages;

}
