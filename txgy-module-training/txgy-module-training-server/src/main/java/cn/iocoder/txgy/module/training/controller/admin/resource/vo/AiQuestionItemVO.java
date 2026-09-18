package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - AI 识别的试题项 VO")
@Data
public class AiQuestionItemVO {

    @Schema(description = "题型：1 单选 2 多选 3 判断 4 实操问答 5 案例分析 6 填空 7 简答", example = "1")
    private Integer qtype;

    @Schema(description = "题干")
    private String stem;

    @Schema(description = "选项（单选/多选/判断使用）")
    private List<OptionItem> options;

    @Schema(description = "正确答案（单选/多选如 A / AB；判断如 正确/错误）")
    private String answer;

    @Schema(description = "解析")
    private String analysis;

    @Schema(description = "难度：1 易 2 中 3 难", example = "2")
    private Integer difficulty;

    @Schema(description = "AI 识别置信度（0~1，本地规则兜底时为 null）")
    private Double confidence;

    @Schema(description = "试题选项项")
    @Data
    public static class OptionItem {

        @Schema(description = "选项标识", example = "A")
        private String key;

        @Schema(description = "选项内容")
        private String text;

        @Schema(description = "是否正确答案")
        private Boolean isCorrect;

    }

}
