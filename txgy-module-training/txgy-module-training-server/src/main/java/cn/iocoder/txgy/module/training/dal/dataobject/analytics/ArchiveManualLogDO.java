package cn.iocoder.txgy.module.training.dal.dataobject.analytics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 档案人工变更日志 DO
 *
 * 对应表 training_archive_manual_log（95 档案人工变更：留痕审计，业务上禁止删除）
 * // TODO[模块九-档案变更] 变更审计查询与导出后续实现
 *
 * @author 云培训学院课题小组
 */
@TableName("training_archive_manual_log")
@KeySequence("training_archive_manual_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveManualLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 被变更学员用户ID
     */
    private Long targetUserId;
    /**
     * 被变更学员姓名
     */
    private String targetUserName;
    /**
     * 变更字段名
     */
    private String fieldName;
    /**
     * 变更前值
     */
    private String beforeValue;
    /**
     * 变更后值
     */
    private String afterValue;
    /**
     * 变更备注
     */
    private String remark;
    /**
     * 操作人ID
     */
    private Long operatorId;
    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 逻辑删除标记（框架逻辑删除；业务上禁止删除，仅留痕）
     */
    @TableLogic
    private Boolean deleted;

}
