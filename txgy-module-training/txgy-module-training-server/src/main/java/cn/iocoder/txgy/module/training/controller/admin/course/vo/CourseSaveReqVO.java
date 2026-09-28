package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 课程新增/修改 Request VO")
@Data
public class CourseSaveReqVO {

    @Schema(description = "课程 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "课程分类 ID（资源中心 categories 预留）", example = "101")
    private Long categoryId;

    @Schema(description = "课程编码（审核通过后锁定）", example = "TC-2026-0001")
    private String code;

    @Schema(description = "课程标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "管道防腐保温工艺入门")
    @NotEmpty(message = "课程标题不能为空")
    @Size(max = 200, message = "课程标题长度不能超过 200 个字符")
    private String title;

    @Schema(description = "课程副标题", example = "零基础到上岗，含安全规范")
    @Size(max = 300, message = "课程副标题长度不能超过 300 个字符")
    private String subtitle;

    @Schema(description = "课程简介")
    private String summary;

    @Schema(description = "授课目标")
    private String objectives;

    @Schema(description = "封面图 URL", example = "https://example.com/cover.png")
    @Size(max = 500, message = "封面地址长度不能超过 500 个字符")
    private String coverUrl;

    @Schema(description = "课程级别：1 初级 2 中级 3 高级（兼容旧字段）", example = "1")
    private Integer level;

    @Schema(description = "成长等级：APPRENTICE/JUNIOR/MIDDLE/SENIOR", example = "JUNIOR")
    private String courseLevel;

    @Schema(description = "课程学分", example = "2.00")
    private BigDecimal credit;

    @Schema(description = "课程总时长（分钟）", example = "45")
    private Integer durationMinutes;

    @Schema(description = "面向行业", example = "防腐保温")
    private String industry;

    @Schema(description = "适用岗位/工种（JSON 数组）", example = "[\"管道工\",\"防腐工\"]")
    private String applicablePosts;

    @Schema(description = "资源归属标识（资源中心 owner_flag 快照）", example = "DEPT_AI_FACTORY")
    private String ownerFlag;

    @Schema(description = "业务标签（JSON 数组）", example = "[\"岗前培训\"]")
    private String bizTags;

    @Schema(description = "是否必修", example = "false")
    private Boolean isRequired;

    @Schema(description = "是否复合课程", example = "false")
    private Boolean isComposite;

    @Schema(description = "复合课程子模块标签（JSON 数组）", example = "[\"管道安装\",\"防腐保温\"]")
    private String compositeTags;

    @Schema(description = "关联结课考试试卷 ID（模块三预留）", example = "1")
    private Long examId;

    @Schema(description = "主讲教师", example = "张工")
    private String lecturer;

    @Schema(description = "讲师简介")
    private String lecturerBrief;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
