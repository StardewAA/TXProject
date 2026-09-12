package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - 试题 新增/修改 Request VO")
@Data
public class QuestionSaveReqVO {

    @Schema(description = "试题ID（修改时必传）")
    private Long id;

    @Schema(description = "题型：1 单选 2 多选 3 判断 4 实操问答 5 案例分析", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "题型不能为空")
    private Integer qtype;

    @Schema(description = "题干", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "题干不能为空")
    private String stem;

    @Schema(description = "选项 JSON：[{\"key\":\"A\",\"text\":\"...\",\"isCorrect\":true}]")
    private String optionsJson;

    @Schema(description = "正确答案")
    private String answer;

    @Schema(description = "解析")
    private String analysis;

    @Schema(description = "难度：1 易 2 中 3 难")
    private Integer difficulty;

    @Schema(description = "所属分类 ID（资源分类 question 类型，支持三级）")
    private Long categoryId;

    @Schema(description = "标签 ID 集合")
    private List<Long> tagIds;

}
