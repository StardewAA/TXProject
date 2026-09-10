package cn.iocoder.txgy.module.training.dal.dataobject.course;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程学习引用占位 DO
 *
 * 对应表 training_course_learning_ref（课程生命周期-删除保护占位：
 * 学员学习/档案归档/考核引用时禁止物理删除课程，返回 409）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_course_learning_ref")
@KeySequence("training_course_learning_ref_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseLearningRefDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 学员用户 ID
     */
    private Long userId;
    /**
     * 学员姓名快照
     */
    private String userName;
    /**
     * 引用类型：LEARN 学习 / RECORD 档案 / ASSESS 考核（预留）
     */
    private String refType;
    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
