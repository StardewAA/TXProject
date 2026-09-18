package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - 课程分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CoursePageReqVO extends PageParam {

    @Schema(description = "课程关键字（标题、编码、讲师模糊匹配）", example = "防腐")
    private String keyword;

    @Schema(description = "课程标题（模糊）", example = "防腐")
    private String title;

    @Schema(description = "课程编码（模糊）", example = "TC-")
    private String code;

    @Schema(description = "课程分类 ID", example = "101")
    private Long categoryId;

    @Schema(description = "课程分类 ID 集合（服务端展开父分类后使用）", hidden = true)
    private List<Long> categoryIds;

    @Schema(description = "排序字段：latest 最新上线 / credit 学时", example = "latest")
    private String sortField;

    @Schema(description = "排序方向：asc / desc", example = "desc")
    private String sortOrder;

    @Schema(description = "状态：0 草稿 1 待审核 2 审核通过 3 已上架 4 已下架", example = "3")
    private Integer status;

    @Schema(description = "主讲教师", example = "张工")
    private String lecturer;

    @Schema(description = "成长等级：APPRENTICE/JUNIOR/MIDDLE/SENIOR", example = "JUNIOR")
    private String courseLevel;

    @Schema(description = "是否必修", example = "true")
    private Boolean isRequired;

    @Schema(description = "是否复合课程", example = "true")
    private Boolean isComposite;

    @Schema(description = "是否当前生效版本", example = "true")
    private Boolean isCurrent;

}
