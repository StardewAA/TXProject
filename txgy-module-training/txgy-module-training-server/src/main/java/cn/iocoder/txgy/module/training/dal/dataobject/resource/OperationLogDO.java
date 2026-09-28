package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源中心操作日志 DO
 *
 * 对应表 training_operation_log（资源生命周期操作留痕）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_operation_log")
@KeySequence("training_operation_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 操作人 ID
     */
    private Long userId;
    /**
     * 操作人姓名
     */
    private String userName;
    /**
     * 动作：UPLOAD/CREATE/UPDATE/DELETE/ONLINE/OFFLINE/ARCHIVE/CREDIT/TRANSCODE/ENABLE/DISABLE/IMPORT
     */
    private String action;
    /**
     * 对象类型：document/video/question/category/tag
     */
    private String targetType;
    /**
     * 对象 ID
     */
    private Long targetId;
    /**
     * 变更摘要
     */
    private String detail;

    @TableLogic
    private Boolean deleted;

}
