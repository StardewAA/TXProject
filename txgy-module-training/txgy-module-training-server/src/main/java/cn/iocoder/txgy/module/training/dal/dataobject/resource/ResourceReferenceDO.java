package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源引用 DO（被引用资源禁止物理删除）
 *
 * 对应表 training_resource_reference
 *
 * @author 云培训学院课题小组
 */
@TableName("training_resource_reference")
@KeySequence("training_resource_reference_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceReferenceDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 资源类型：document/video/question
     */
    private String resourceType;
    /**
     * 被引用资源 ID
     */
    private Long resourceId;
    /**
     * 引用方类型：course/exam/practice
     */
    private String refType;
    /**
     * 引用方 ID（课程 ID/试卷 ID 等）
     */
    private Long refId;
    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private Boolean deleted;

}
