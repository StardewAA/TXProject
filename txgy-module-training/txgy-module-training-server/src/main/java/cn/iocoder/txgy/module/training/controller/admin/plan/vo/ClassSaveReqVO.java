package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 培训班创建/修改 Request VO")
@Data
public class ClassSaveReqVO {

    @Schema(description = "培训班 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "培训班名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026 年管道工岗前培训（一期）")
    @NotEmpty(message = "培训班名称不能为空")
    private String name;

    @Schema(description = "培训类型：1岗前 2复工 3技能 4质检 5安全 6特种 7学徒 8复合", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "培训类型不能为空")
    private Integer type;

    @Schema(description = "关联工程项目编号", example = "PROJ-2026-001")
    private String projectId;

    @Schema(description = "关联工程项目名称", example = "某炼化管道安装项目")
    private String projectName;

    @Schema(description = "参训对象筛选条件（JSON）", example = "{\"posts\":[\"管道工\"]}")
    private String targetCondition;

    @Schema(description = "定岗（JSON 数组）", example = "[\"管道工\"]")
    private String targetPosts;

    @Schema(description = "开班时间", example = "2026-09-05 08:00:00")
    private LocalDateTime startTime;

    @Schema(description = "培训截止时间", example = "2026-09-30 23:59:59")
    private LocalDateTime deadline;

    @Schema(description = "绑定课程 ID 列表（创建必填，开课前可调整）", example = "[1,2]")
    private List<Long> courseIds;

    @Schema(description = "备注", example = "由各班组推荐报名")
    private String remark;

}
