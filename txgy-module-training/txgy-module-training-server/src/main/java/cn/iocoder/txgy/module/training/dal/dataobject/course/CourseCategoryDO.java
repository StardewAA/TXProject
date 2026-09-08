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
     * 父级分类ID（0=一级域）
     */
    private Long parentId;
    /**
     * 层级：1 域 / 2 组 / 3 课
     */
    private Integer level;
    /**
     * 业务类别/适用工程队（域级，如：一/二/三 或 全部必修；供模块一资源打标/模块九报表关联）
     */
    private String bizCategory;
    /**
     * 标准学时（课程级，供模块四学时统计/模块九学时完成率校验）
     */
    private Integer stdHours;
    /**
     * 是否必修（课程级）
     */
    private Boolean isRequired;
    /**
     * 状态：active 启用 / inactive 停用
     */
    private String status;
    /**
     * 分类名称
     */
    private String name;
    /**
     * 分类编码（唯一标识：域 A / 组 A01 / 课 A0101；对接资源中心与课程绑定时使用）
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
