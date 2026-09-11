package cn.iocoder.txgy.module.training.dal.dataobject.course;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 成长等级 DO
 *
 * 对应表 training_growth_level（人才成长六级体系：学徒/初级工/中级工/高级工/技师/高级技师）
 * 数据来源：《云培训学院-成长等级表.xlsx》；课程成长等级标记、人才成长进度等均引用本字典
 *
 * @author 云培训学院课题小组
 */
@TableName("training_growth_level")
@KeySequence("training_growth_level_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class GrowthLevelDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 等级编码（唯一标识，课程 course_level / 人才档案等级引用）
     */
    private String code;
    /**
     * 等级序号：1 学徒 / 2 初级工 / 3 中级工 / 4 高级工 / 5 技师 / 6 高级技师
     */
    private Integer levelNo;
    /**
     * 等级名称
     */
    private String name;
    /**
     * 定位描述
     */
    private String positionDesc;
    /**
     * 独立作业权限描述
     */
    private String workPermission;
    /**
     * 带徒上限（人），0 表示不可带徒
     */
    private Integer maxApprentice;
    /**
     * 是否需人工评审（0 否 1 是，仅高级技师需评审）
     */
    private Boolean needReview;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;
    /**
     * 参考对标（如：初级工前 / 五级·初级 / 一级·高级技师）
     */
    private String reference;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
