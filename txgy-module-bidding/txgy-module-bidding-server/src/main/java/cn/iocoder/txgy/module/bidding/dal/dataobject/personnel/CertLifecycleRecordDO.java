package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证件/资质生命周期记录（变更/续期/注销吊销/年检，只追加）。表：cert_lifecycle_record
 * （DDL sql/2026-09-14-cert-lifecycle.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3（审查，含 ANNUAL 年检）、表 5（续期/补换证）、
 * 表 6（变更：change_group_id 归组、字段级 old/new 留痕）、表 7（吊销/注销/暂扣/恢复）；
 * 用户范围口径：做到"证照全生命周期管理（中级）"——变更、注销、年检、到期提醒、版本管理，不做签发。
 * <p>为什么这样写：v3.3 的四张专表约八成字段同构，且本域要同时服务"通用证照"与"员工证书"两类对象，
 * 按四表×两域摊开会得到 8 张近似表；故用 biz_domain + lifecycle_type 泛化（同 cert_alert_records 的
 * ref_type/ref_id 先例）。字段已按四类流程分组，若评审要求拆表可按 lifecycle_type 无损拆。
 * <p>业务目标：每一次证件变更/续期/注销/年检都留痕可追溯，并与主档同事务回写，
 * 替代"只在 Excel 改个数字、事后说不清谁改的"的台账模式。
 */
@TableName("cert_lifecycle_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertLifecycleRecordDO extends TenantBaseDO {

    private Long id;
    /** 业务对象域 code（CertLifecycleBizDomainEnum） */
    private String bizDomain;
    /** 业务对象主键 */
    private Long bizId;
    /** 业务对象名称快照（防主档改名后无法复算） */
    private String refName;
    /** 证书编号快照 */
    private String refNo;
    /** 归属企业 */
    private Long entId;
    /** 生命周期类型 code（CertLifecycleTypeEnum） */
    private String lifecycleType;
    /** 状态 code（CertLifecycleStatusEnum） */
    private String status;
    /** 申请单号（对外可引用） */
    private String applyNo;

    // ===== 变更（v3.3 表 6）=====
    /** 变更归组号（同一次申请多字段共用） */
    private String changeGroupId;
    /** 变更字段名（台账列名） */
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String changeSummary;

    // ===== 续期（v3.3 表 5）=====
    /** 续期类型 code（CertRenewalTypeEnum） */
    private String renewalType;
    private LocalDate oldValidFrom;
    private LocalDate oldValidTo;
    private LocalDate newValidFrom;
    private LocalDate newValidTo;
    private LocalDate newNextReviewDate;
    private String newCertNo;
    private String resultNo;

    // ===== 注销/吊销（v3.3 表 7）=====
    /** 吊销类型 code（CertRevokeTypeEnum） */
    private String revokeType;
    private String revokeAuthority;
    private String revokeReason;
    private LocalDate revokeEffectiveAt;

    // ===== 年检（v3.3 表 3 ANNUAL）=====
    private Integer reviewYear;
    private LocalDate reviewDate;
    /** 年检/审查结论：null 未出结论 / 0 不通过 / 1 通过 */
    private Integer reviewResult;

    // ===== 附件与过程 =====
    /** 新文件 id（变更页/延续页/新证扫描件） */
    private Long newCertFileId;
    /** 佐证材料文件 id 列表（JSON 串） */
    private String evidenceFileIds;
    private Long appliedBy;
    private LocalDateTime appliedAt;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime effectiveAt;
    /** 幂等键（biz_domain:biz_id:lifecycle_type:apply_no） */
    private String idempotentKey;
    private String remark;
    /** 乐观锁版本（状态机 CAS） */
    @Version
    private Integer version;
}
