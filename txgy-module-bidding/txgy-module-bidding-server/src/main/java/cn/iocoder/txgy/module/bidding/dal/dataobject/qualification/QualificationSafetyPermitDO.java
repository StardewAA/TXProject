package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 安全生产许可证（建筑施工企业必备，常与资质联动核查/预警）。表：qualification_safety_permit（DDL sql/2026-09-10-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-8：permit_no UK(tenant)、3 年一延续 valid_to 驱动预警、
 * permit_status 状态机、binded_cert_id 复用 certificate_info 影像。
 * <p>设计遵循/针对：状态机走接口（SafetyPermitStatusEnum 迁移表 + version CAS）；到期并入 QUAL_EXPIRY 扫描（§十五联动）。
 */
@TableName("qualification_safety_permit")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationSafetyPermitDO extends TenantBaseDO {

    private Long id;
    private Long entId;
    /** 安全生产许可证编号（UK） */
    private String permitNo;
    private String issuingAuthority;
    private LocalDate issueDate;
    /** 有效期至（3 年一延续；驱动到期预警） */
    private LocalDate validTo;
    /** VALID/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED */
    private String permitStatus;
    /** 法定代表人（脱敏） */
    private String legalPerson;
    private Long bindedCertId;
    private String remark;
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
