package cn.iocoder.txgy.module.training.dal.dataobject.plan;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 督促提醒记录 DO
 *
 * 对应表 training_class_remind。每次“一键督促”留痕：向学员发送站内信并记录结果。
 *
 * @author 云培训学院课题小组
 */
@TableName("training_class_remind")
@KeySequence("training_class_remind_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RemindRecordDO extends TenantBaseDO {

    @TableId
    private Long id;

    /**
     * 培训班 ID
     */
    private Long classId;
    /**
     * 培训班名称快照
     */
    private String className;
    /**
     * 学员用户 ID
     */
    private Long userId;
    /**
     * 学员姓名快照
     */
    private String userName;
    /**
     * 督促类型：1 学习进度督促（综合）
     */
    private Integer remindType;
    /**
     * 站内信消息 ID（发送成功回填）
     */
    private Long messageId;
    /**
     * 站内信内容快照
     */
    private String content;
    /**
     * 发送状态：0 成功 1 失败
     */
    private Integer status;
    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
