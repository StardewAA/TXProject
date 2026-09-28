package cn.iocoder.txgy.module.hrm.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningNotifyStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * HRM 证件预警记录 DO
 *
 * 预警记录是可重建的派生数据，业务事实始终以证件台账为准：通知失败不影响业务事实，
 * 仅记录失败原因与重试次数。按「证件 + 预警类型 + 预警日期」唯一去重，重复扫描不产生重复记录。
 *
 * @author OPENLAB BS
 */
@TableName("hrm_certificate_warning")
@KeySequence("hrm_certificate_warning_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmCertificateWarningDO extends BaseDO {

    /**
     * 预警记录编号
     */
    @TableId
    private Long id;
    /**
     * 证件台账编号
     *
     * 关联 {@link HrmEmployeeCertificateDO#getId()}
     */
    private Long certificateId;
    /**
     * 员工编号
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long employeeId;
    /**
     * 预警类型
     *
     * 枚举 {@link HrmCertificateWarningTypeEnum}
     */
    private Integer type;
    /**
     * 预警触发日期
     *
     * 去重键的一部分
     */
    private LocalDate warningDate;
    /**
     * 到期日期（预警类型为到期时为有效结束日期，为复审时为复审日期）
     */
    private LocalDateTime expireDate;
    /**
     * 剩余天数
     *
     * 为负数表示已过期
     */
    private Integer remainDays;
    /**
     * 处理状态
     *
     * 枚举 {@link HrmCertificateWarningStatusEnum}
     */
    private Integer status;
    /**
     * 处理人用户编号
     */
    private Long handleUserId;
    /**
     * 处理时间
     */
    private LocalDateTime handleTime;
    /**
     * 处理意见
     */
    private String handleRemark;
    /**
     * 通知状态
     *
     * 枚举 {@link HrmCertificateWarningNotifyStatusEnum}
     */
    private Integer notifyStatus;
    /**
     * 通知时间
     */
    private LocalDateTime notifyTime;
    /**
     * 通知失败原因
     */
    private String notifyFailReason;
    /**
     * 通知重试次数
     */
    private Integer notifyRetryCount;

}
