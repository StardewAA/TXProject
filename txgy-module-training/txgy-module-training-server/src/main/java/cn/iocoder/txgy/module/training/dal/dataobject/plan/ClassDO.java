package cn.iocoder.txgy.module.training.dal.dataobject.plan;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.training.enums.plan.ClassStatusEnum;
import cn.iocoder.txgy.module.training.enums.plan.ClassTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 培训班 DO
 *
 * 对应表 training_class（模块三：培训管理核心主表）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_class")
@KeySequence("training_class_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassDO extends TenantBaseDO {

    @TableId
    private Long id;

    /**
     * 培训班编码（开班后自动生成，如 TB{id}）
     */
    private String code;
    /**
     * 培训班名称
     */
    private String name;
    /**
     * 培训类型：见 {@link ClassTypeEnum}
     */
    private Integer type;
    /**
     * 关联工程项目编号（岗前/复工培训必填，供上岗资格核查）
     */
    private String projectId;
    /**
     * 关联工程项目名称
     */
    private String projectName;
    /**
     * 参训对象筛选条件（JSON，如岗位/工种/部门/班组/成长等级）
     */
    private String targetCondition;
    /**
     * 定岗（结业后对应岗位，JSON 数组）
     */
    private String targetPosts;
    /**
     * 开班时间
     */
    private LocalDateTime startTime;
    /**
     * 培训截止时间
     */
    private LocalDateTime deadline;
    /**
     * 绑定课程数（冗余）
     */
    private Integer courseCount;
    /**
     * 在班学员数（冗余）
     */
    private Integer studentCount;
    /**
     * 状态：见 {@link ClassStatusEnum}
     */
    private Integer status;
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
