package cn.iocoder.txgy.module.training.dal.dataobject.basedata;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工种学习地图 DO
 *
 * 对应表 training_learning_map；按成长阶段组织的课程路径
 * 数据来源：《云培训学院-学习地图示例.xlsx》
 *
 * 关联关系：
 * - jobCode    -> training_job.code
 * - courseCode -> training_course_category.code（L3 课程级）
 * - stage      -> 晋级门槛阶段（entry/core/advanced/technician）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_learning_map")
@KeySequence("training_learning_map_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningMapDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 工种编码（关联 training_job.code）
     */
    private String jobCode;
    /**
     * 工种名称（冗余，便于展示）
     */
    private String jobName;
    /**
     * 成长阶段：entry 入门段(L1→L2) / core 核心段(L2→L3) / advanced 进阶段(L3→L4) / technician 技师段(L4→L5)
     */
    private String stage;
    /**
     * 阶段名称（如 入门段(L1→L2)）
     */
    private String stageName;
    /**
     * 阶段序号：1 入门 / 2 核心 / 3 进阶 / 4 技师
     */
    private Integer stageNo;
    /**
     * 课程编码（关联 training_course_category.code，level=3 课程级）
     */
    private String courseCode;
    /**
     * 课程名称（冗余，便于展示）
     */
    private String courseName;
    /**
     * 是否必修（0 否 1 是）
     */
    private Boolean isRequired;
    /**
     * 阶段内排序
     */
    private Integer sort;
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
