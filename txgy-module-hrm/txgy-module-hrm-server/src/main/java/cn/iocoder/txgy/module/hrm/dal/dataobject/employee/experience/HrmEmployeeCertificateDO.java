package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience;

import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
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
@TableName(value = "hrm_employee_certificate", autoResultMap = true)
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
     * 证照类型编号（hrm_certificate_type.id）
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
     */
    private java.time.LocalDate reviewDate;
    /**
     * 发证机构
     */
    private String issuingAuthority;
    /**
     * 发证日期
     */
    private LocalDateTime issuingTime;
    /**
     * 证件扫描件/照片（逗号分隔地址串）
     */
    @com.baomidou.mybatisplus.annotation.TableField(
            typeHandler = cn.iocoder.txgy.framework.mybatis.core.type.StringListTypeHandler.class)
    private java.util.List<String> fileUrls;
    /**
     * 核验状态：0 未核验，1 已核验，2 核验不通过
     *
     * 枚举 {@link cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateVerifyStatusEnum}
     */
    private Integer verifyStatus;
    /**
     * 核验人（后台用户编号）
     */
    private Long verifyUserId;
    /**
     * 核验时间
     */
    private LocalDateTime verifyTime;
    /**
     * 备注
     */
    private String remark;
    /**
     * 显示顺序
     */
    private Integer sort;

}
