package cn.iocoder.txgy.module.training.dal.dataobject.learning;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.training.enums.learner.LearnerSourceTypeEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerTaskStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学员学习任务 DO
 *
 * <p>对应表 training_learner_task：记录「学员 × 课程」的学习任务（管理员指派或学员自主选学）。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_learner_task")
@KeySequence("training_learner_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LearnerTaskDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 学员用户 ID
     */
    private Long userId;
    /**
     * 课程 ID（当前生效版本课程）
     */
    private Long courseId;
    /**
     * 来源：见 {@link LearnerSourceTypeEnum}，assign 指派 / self 自主选学
     */
    private String sourceType;
    /**
     * 状态：见 {@link LearnerTaskStatusEnum}，0 待学习 1 学习中 2 已完成
     */
    private Integer status;
    /**
     * 学习进度（%），0-100，保留两位小数
     */
    private BigDecimal progressPercent;
    /**
     * 应获学时（小时）
     */
    private BigDecimal creditTotal;
    /**
     * 已获学时（小时）
     */
    private BigDecimal creditEarned;
    /**
     * 首次学习开始时间
     */
    private LocalDateTime startedAt;
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    /**
     * 备注
     */
    private String remark;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}
