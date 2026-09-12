package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 学习地图分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningMapPageReqVO extends PageParam {

    @Schema(description = "工种编码（关联岗位工种）", example = "OP-02")
    private String jobCode;

    @Schema(description = "工种名称（模糊）", example = "防腐涂装工")
    private String jobName;

    @Schema(description = "成长阶段：entry/core/advanced/technician", example = "entry")
    private String stage;

    @Schema(description = "阶段序号：1 入门 / 2 核心 / 3 进阶 / 4 技师", example = "1")
    private Integer stageNo;

    @Schema(description = "课程编码（关联课程分类 L3）", example = "B0101")
    private String courseCode;

    @Schema(description = "课程名称（模糊）", example = "钢材表面处理")
    private String courseName;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;

}
