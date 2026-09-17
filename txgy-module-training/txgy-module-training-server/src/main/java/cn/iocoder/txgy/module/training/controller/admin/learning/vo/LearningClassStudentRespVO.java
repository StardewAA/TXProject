package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 培训班学员（学习中心「筛选学员 / 课程指派」候选）
 *
 * <p>数据来源：培训管理-培训班在班学员（training_class_student，status=0）。
 * 同一学员可能同时在多个培训班，按学员去重后返回，多个班级以「、」拼接展示。</p>
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 培训班学员（学习中心候选） Response VO")
@Data
public class LearningClassStudentRespVO {

    @Schema(description = "学员用户 ID", example = "3001")
    private Long userId;

    @Schema(description = "学员姓名（入班快照）", example = "张三")
    private String userName;

    @Schema(description = "所属部门（入班快照）", example = "防腐涂装车间")
    private String deptName;

    @Schema(description = "所在培训班名称（多个以「、」拼接）", example = "2026 年管道工岗前培训（一期）")
    private String className;

    @Schema(description = "所在培训班数量", example = "1")
    private Integer classCount;

    @Schema(description = "最近一次入班时间")
    private LocalDateTime joinTime;

}
