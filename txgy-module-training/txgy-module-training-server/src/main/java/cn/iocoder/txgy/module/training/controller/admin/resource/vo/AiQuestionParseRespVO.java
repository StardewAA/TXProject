package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - AI 文档解析试题 预览 Response VO")
@Data
public class AiQuestionParseRespVO {

    @Schema(description = "识别出的试题列表")
    private List<AiQuestionItemVO> items;

    @Schema(description = "AI 是否已配置生效", example = "true")
    private Boolean aiEnabled;

    @Schema(description = "是否走了本地规则兜底（AI 不可用或调用失败时为 true）", example = "false")
    private Boolean fallback;

    @Schema(description = "提示信息", example = "AI 识别完成，共 8 题")
    private String message;

    @Schema(description = "从文档中提取的文本长度")
    private Integer textLength;

    @Schema(description = "文档名称")
    private String fileName;

    @Schema(description = "文档类型", example = "docx")
    private String fileType;

}
