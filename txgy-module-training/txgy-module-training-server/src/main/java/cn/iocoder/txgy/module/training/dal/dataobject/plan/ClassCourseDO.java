package cn.iocoder.txgy.module.training.dal.dataobject.plan;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 培训班课程绑定 DO（四定-定课，含课程信息快照）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_class_course")
@KeySequence("training_class_course_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassCourseDO extends TenantBaseDO {

    @TableId
    private Long id;

    /**
     * 培训班 ID
     */
    private Long classId;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 课程编码快照
     */
    private String courseCode;
    /**
     * 课程标题快照
     */
    private String courseTitle;
    /**
     * 课程总时长快照（分钟）
     */
    private Integer durationMinutes;
    /**
     * 排序
     */
    private Integer sort;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
