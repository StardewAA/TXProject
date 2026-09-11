package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标签 DO（试题多维度打标）
 *
 * 对应表 training_tag（work_type/course/safety/process）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_tag")
@KeySequence("training_tag_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceTagDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 标签名
     */
    private String name;
    /**
     * 标签维度：work_type/course/safety/process
     */
    private String dimension;
    /**
     * 排序
     */
    private Integer sort;

    @TableLogic
    private Boolean deleted;

}
