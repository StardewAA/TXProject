package cn.iocoder.txgy.module.training.dal.dataobject.basedata;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工种资格证要求 DO
 *
 * 对应表 training_job_cert；持证上岗校验依据
 * 数据来源：《云培训学院-各种资格证.xlsx》
 *
 * 关联关系：jobCode -> training_job.code
 *
 * @author 云培训学院课题小组
 */
@TableName("training_job_cert")
@KeySequence("training_job_cert_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class JobCertDO extends TenantBaseDO {

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
     * 资格证名称
     */
    private String certName;
    /**
     * 是否必须持证上岗（0 否 1 是）
     */
    private Boolean isRequired;
    /**
     * 有效期（月），NULL 表示长期有效
     */
    private Integer validMonths;
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
