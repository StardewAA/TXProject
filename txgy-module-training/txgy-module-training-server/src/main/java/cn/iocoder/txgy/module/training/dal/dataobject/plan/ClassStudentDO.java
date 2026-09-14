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
 * 培训班学员 DO（四定-定人）
 *
 * 状态 status：0 在班 1 已移除（保留历史，便于结业追溯）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_class_student")
@KeySequence("training_class_student_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassStudentDO extends TenantBaseDO {

    @TableId
    private Long id;

    /**
     * 培训班 ID
     */
    private Long classId;
    /**
     * 学员用户 ID（system_users，登录用户即工人/员工）
     */
    private Long userId;
    /**
     * 学员姓名快照
     */
    private String userName;
    /**
     * 部门/班组快照
     */
    private String deptName;
    /**
     * 加入来源：1 手动添加 2 Excel 批量导入 3 按参与对象筛选
     */
    private Integer source;
    /**
     * 状态：0 在班 1 已移除
     */
    private Integer status;
    /**
     * 入班时间
     */
    private LocalDateTime joinTime;
    /**
     * 移除时间
     */
    private LocalDateTime removedTime;
    /**
     * 移除原因
     */
    private String removedReason;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
