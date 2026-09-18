package cn.iocoder.txgy.module.training.dal.dataobject.course;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程审核日志 DO（永久留痕）
 *
 * 对应表 training_course_audit_log
 *
 * @author 云培训学院课题小组
 */
@TableName("training_course_audit_log")
@KeySequence("training_course_audit_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseAuditLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 动作：SUBMIT / WITHDRAW / APPROVE / REJECT / ONLINE / OFFLINE / PUBLISH
     */
    private String action;
    /**
     * 操作前状态（见 CourseStatusEnum）
     */
    private Integer fromStatus;
    /**
     * 操作后状态（见 CourseStatusEnum）
     */
    private Integer toStatus;
    /**
     * 操作人 ID
     */
    private Long operatorId;
    /**
     * 操作人姓名快照
     */
    private String operatorName;
    /**
     * 备注 / 驳回原因
     */
    private String reason;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
