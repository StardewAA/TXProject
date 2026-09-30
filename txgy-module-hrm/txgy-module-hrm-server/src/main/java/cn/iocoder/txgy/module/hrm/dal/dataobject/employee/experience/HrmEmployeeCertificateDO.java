package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience;

import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
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
 * HRM 员工证书 DO
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_certificate")
@KeySequence("hrm_employee_certificate_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeCertificateDO extends TenantBaseDO {

    /**
     * 证书编号
     */
    @TableId
    private Long id;
    /**
     * 员工编号
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long employeeId;
    /**
     * 证件类型编号
     *
     * 关联 {@link HrmCertificateTypeDO#getId()}，为空表示未纳入资格证件域管理的普通证书履历
     */
    private Long typeId;
    /**
     * 证书名称
     */
    private String name;
    /**
     * 证书级别
     */
    private String level;
    /**
     * 证书编号
     */
    private String no;
    /**
     * 有效开始日期
     */
    private LocalDateTime startTime;
    /**
     * 有效结束日期
     */
    private LocalDateTime endTime;
    /**
     * 复审日期
     *
     * 仅当证件类型需要复审时由续证/复审流程写入
     */
    private LocalDateTime reviewTime;
    /**
     * 发证机构
     */
    private String issuingAuthority;
    /**
     * 发证日期
     */
    private LocalDateTime issuingTime;
    /**
     * 证件附件地址
     *
     * 关联统一文件服务的文件地址
     */
    private String attachmentUrl;
    /**
     * 证件状态
     *
     * 由每日定时任务计算，枚举 {@link HrmCertificateStatusEnum}
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 显示顺序
     */
    private Integer sort;

}
