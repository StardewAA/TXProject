package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.blacklist;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * HRM 员工黑名单 DO
 *
 * <p>防腐保温行业用工弹性大、人员流动频繁，常出现"某个工人不能再录用"的情况
 * （严重违纪、重大安全违规、结算纠纷等）。黑名单同时约束 HRM 录用/再入职，
 * 并可作为 MES 现场准入的参考。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_blacklist")
@KeySequence("hrm_employee_blacklist_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeBlacklistDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 员工编号（可空：仅登记手机号/身份证时为空）
     */
    private Long employeeId;
    /**
     * 姓名
     */
    private String name;
    /**
     * 手机号（命中校验的主要依据）
     */
    private String mobile;
    /**
     * 身份证号（选填，用于精确命中）
     */
    private String idCardNo;
    /**
     * 加入原因
     */
    private String reason;
    /**
     * 来源：1 HRM 手工登记，2 现场上报，3 其他
     */
    private Integer source;
    /**
     * 加入时间
     */
    private LocalDateTime joinTime;
    /**
     * 解除时间（status=2 时有效）
     */
    private LocalDateTime releaseTime;
    /**
     * 是否永久
     */
    private Boolean permanentFlag;
    /**
     * 状态：1 生效，2 已解除
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
