package cn.iocoder.txgy.module.training.dal.dataobject.course;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程分类 DO
 *
 * 对应表 training_course_category（资源中心 categories(type=course) 在当前工程内的占位实现）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_course_category")
@KeySequence("training_course_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseCategoryDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 分类名称
     */
    private String name;
    /**
     * 分类编码（唯一标识，对接资源中心时使用）
     */
    private String code;
    /**
     * 排序
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
