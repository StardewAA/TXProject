package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源中心分类 DO
 *
 * 对应表 training_category（文档/视频/试题三类共用，type 区分，支持三级层级）
 * 三类分类通过 code 保持一致，对任一类型的增删改会同步到另外两类。
 *
 * @author 云培训学院课题小组
 */
@TableName("training_category")
@KeySequence("training_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceCategoryDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 分类类型：doc 文档 / video 视频 / question 试题
     */
    private String type;
    /**
     * 分类名称
     */
    private String name;
    /**
     * 分类编码，用于 doc/video/question 三类之间同步（导入课程分类时复用课程分类编码）
     */
    private String code;
    /**
     * 父分类 ID（0 = 根，最多支持三级）
     */
    private Long parentId;
    /**
     * 排序
     */
    private Integer sort;

    @TableLogic
    private Boolean deleted;

}
