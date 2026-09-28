package cn.iocoder.txgy.module.hrm.dal.dataobject.sandbox;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * HRM 沙箱（演示/培训）数据登记 DO（I-025）
 *
 * <p>沙箱生成的业务数据（职位、请假、员工、考勤组等）在这里登记一条记录，
 * 「一键清场」按 (bizType, bizId) 逐条软删除并清空登记，避免演示数据污染正式台账。</p>
 *
 * @author OPENLAB BS
 */
@TableName(value = "hrm_sandbox_record")
@KeySequence("hrm_sandbox_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class HrmSandboxRecordDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 业务类型：post / leave / employee / attendance_group
     */
    private String bizType;
    /**
     * 业务主键
     */
    private Long bizId;
    /**
     * 业务名称（便于人工核对）
     */
    private String bizName;
    /**
     * 说明（生成来源 / 批次）
     */
    private String description;

}
