package cn.iocoder.txgy.module.training.dal.dataobject.analytics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 培训学习行为日志 DO
 *
 * 对应表 training_study_behavior_log（91 学时管控与学习监管：全量学习行为留痕）
 * // TODO[模块九-学习行为] 防刷课规则计算有效学时的采集与写入逻辑后续实现
 *
 * @author 云培训学院课题小组
 */
@TableName("training_study_behavior_log")
@KeySequence("training_study_behavior_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StudyBehaviorLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 学员用户ID
     */
    private Long userId;
    /**
     * 学员姓名
     */
    private String userName;
    /**
     * 学习类型：video/document/live/vr
     */
    private String studyType;
    /**
     * 资源ID
     */
    private Long resourceId;
    /**
     * 资源名称
     */
    private String resourceTitle;
    /**
     * 关联课程ID
     */
    private Long courseId;
    /**
     * 学习时长（秒）
     */
    private Long studySeconds;
    /**
     * 有效学时秒数（防刷课后）
     */
    private Long validSeconds;
    /**
     * 是否有效学时（false-异常刷课 true-正常）
     */
    private Boolean validFlag;
    /**
     * 刷课异常原因
     */
    private String abnormalReason;
    /**
     * 学习进度百分比
     */
    private Integer progressPercent;
    /**
     * 学习日期
     */
    private LocalDate studyDate;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
