package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - 试题 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionPageReqVO extends PageParam {

    @Schema(description = "关键词（题干模糊匹配）")
    private String keyword;

    @Schema(description = "题型：1 单选 2 多选 3 判断 4 实操问答 5 案例分析")
    private Integer qtype;

    @Schema(description = "难度：1 易 2 中 3 难")
    private Integer difficulty;

    @Schema(description = "所属分类 ID（资源分类 question 类型，支持三级）")
    private Long categoryId;

    @Schema(description = "状态：1 启用 2 停用")
    private Integer status;

    @Schema(description = "标签 ID（按标签筛选时使用）")
    private Long tagId;

    /**
     * 试题 ID 集合（由 Service 通过标签关联计算得出，内部使用）
     */
    private List<Long> questionIds;

}
