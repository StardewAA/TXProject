package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;
import static cn.iocoder.txgy.framework.common.util.date.DateUtils.TIME_ZONE_DEFAULT;

/**
 * 管理后台 - 课程业务属性配置 Request VO（23 课程业务属性配置）
 *
 * <p>与 {@link CourseSaveReqVO} 分离：仅承载业务属性字段，避免课程标题等必填项约束
 * 影响"单独配置业务属性"的场景（仅草稿态当前生效版本可配置）。</p>
 */
@Schema(description = "管理后台 - 课程业务属性配置 Request VO")
@Data
public class CourseAttributesReqVO {

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

    @Schema(description = "学习域/课程组编码（按适用岗位工种自动匹配，逗号分隔；* 表示全域）", example = "A,B01,B02")
    @Size(max = 255, message = "学习域/课程组长度不能超过 255 个字符")
    private String learnDomainCodes;

    @Schema(description = "资源归属标识（资源中心 owner_flag 快照）", example = "DEPT_AI_FACTORY")
    @Size(max = 64, message = "资源归属标识长度不能超过 64 个字符")
    private String ownerFlag;

    @Schema(description = "业务标签（JSON 数组）", example = "[\"岗前培训\"]")
    private String bizTags;

    @Schema(description = "是否必修", example = "false")
    private Boolean isRequired;

    @Schema(description = "是否复合课程", example = "false")
    private Boolean isComposite;

    @Schema(description = "复合课程子模块标签（JSON 数组）", example = "[\"管道安装\",\"防腐保温\"]")
    private String compositeTags;

    @Schema(description = "关联结课考试试卷 ID（模块三试卷）", example = "1")
    private Long examId;

    @Schema(description = "结课考试开始时间（yyyy-MM-dd HH:mm:ss）", example = "2026-10-01 09:00:00")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND, timezone = TIME_ZONE_DEFAULT)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime examStartTime;

    @Schema(description = "结课考试结束时间（yyyy-MM-dd HH:mm:ss）", example = "2026-10-01 11:00:00")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND, timezone = TIME_ZONE_DEFAULT)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime examEndTime;

}
