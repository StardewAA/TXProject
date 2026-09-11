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
    /**
     * 企业级风险档 code（SafetyRiskFlagEnum）：NORMAL/YELLOW/RED/INVALID/FREQUENT；NULL=未评估（业务侧视为不阻断）。
     *
     * <p>为什么用 NULL 表示"未评估"而不是默认 NORMAL：存量数据尚未评估时若默认成"正常"，
     * 会给人"已核查通过"的错觉；NULL 让页面能明确显示"未评估"，也让上线回滚有退路（置回 NULL 即回到改造前）。
     */
    private String riskFlag;
    /** 风险等级 code（RiskLevelEnum）：LOW/MEDIUM/HIGH/MAJOR，供周检 Job 取提前量 */
    private String riskLevel;
    /** 最近风险评估时刻 */
    private java.time.LocalDateTime riskEvaluatedAt;
    /** 宽限截止（NULL=无宽限） */
    private java.time.LocalDateTime graceExpireAt;
    /** 宽限原因（须在 safety.abc.grace.reason.whitelist 内） */
    private String graceReason;
    /** 宽限依据（变更单号/决定书号，便于核查宽限真实性） */
    private String graceSourceRef;
    /** 滑动窗口内预警次数 */
    private Integer frequentAlertCount;
    /** 频繁统计滑动窗口起点 */
    private java.time.LocalDateTime frequentWindowStart;
    /** 本次评估所用规则版本（可复算） */
    private String ruleVersion;
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
