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
 * 对应表 training_category（文档/视频/试题三类共用，type 区分，支持二级层级）
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
     * 父分类 ID（0 = 根，仅支持二级）
     */
    private Long parentId;
    /**
     * 排序
     */
    private Integer sort;

    @TableLogic
    private Boolean deleted;

}
