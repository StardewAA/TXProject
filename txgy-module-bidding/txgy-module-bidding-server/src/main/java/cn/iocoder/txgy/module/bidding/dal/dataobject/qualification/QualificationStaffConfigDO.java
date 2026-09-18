package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资质人员配置台账。表：qualification_staff_config（DDL sql/2026-09-09-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3：一人一岗盲索引判重、社保挂靠核查字段、
 * reg_valid_to（NULL 即不满足）、binded_cert_id 关联个人证书；角色口径见 QualificationStaffRoleEnum。
 * <p>设计遵循/针对：身份证等敏感字段仅存加密值 + 盲索引（L4/L3 分级），业务侧不落明文。
 */
@TableName("qualification_staff_config")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationStaffConfigDO extends TenantBaseDO {

    private Long id;
    private Long entId;
    /** 关联资质（分资质核算人数） */
    private Long qualId;
    private String staffRole;
    private String personName;
    private String idCardEnc;
    private String idCardBlindIndex;
    private String major;
    private String regNo;
    private String titleLevel;
    /** 是否本单位在职 */
    private Boolean isFullTime;
    private String socialSecurityStatus;
    private String socialSecurityTenant;
    private BigDecimal eduHours;
    private BigDecimal eduHoursRequired;
    /** 注册有效期止（NULL 即不满足） */
    private LocalDate regValidTo;
    private Long bindedCertId;
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
