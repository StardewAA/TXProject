package cn.iocoder.txgy.module.training.dal.dataobject.plan;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学员学习任务 DO
 *
 * 对应表 training_student_task。学员入班时按“学员 × 绑定课程”自动生成一条学习任务，
 * 支撑班级学习进度看板。
 *
 * @author 云培训学院课题小组
 */
@TableName("training_student_task")
@KeySequence("training_student_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentTaskDO extends TenantBaseDO {

    @TableId
    private Long id;

    /**
     * 培训班 ID
     */
    private Long classId;
    /**
     * 学员用户 ID
     */
    private Long userId;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 课程标题快照
     */
    private String courseTitle;
    /**
     * 学时完成状态：0 未完成 1 已完成
     */
    private Integer studyStatus;
    /**
     * 课程完成状态：0 未开始 1 进行中 2 已完成
     */
    private Integer courseStatus;
    /**
     * 考试状态：0 未参加 1 已通过 2 未通过
     */
    private Integer examStatus;
    /**
     * 证书获取状态：0 未获取 1 已获取
     */
    private Integer certStatus;
    /**
     * 任务总状态：0 未开始 1 进行中 2 已完成
     */
    private Integer status;
    /**
     * 任务完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
