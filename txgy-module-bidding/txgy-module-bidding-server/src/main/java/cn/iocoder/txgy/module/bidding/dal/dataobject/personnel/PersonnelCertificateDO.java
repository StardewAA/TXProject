package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工证书台账。表：personnel_certificate（DDL sql/2026-09-13-personnel-qualification.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §一（PERSON 类证书走人员扩展）、表 15-3
 * （注册/执业编号 reg_no、注册有效期 reg_valid_to）；《电子招标投标办法》（20号令）第42条（从业人员
 * 姓名与电子证书编号应可公开）；《注册建造师管理规定》（建设部令第153号）（注册单位须与聘用企业一致）。
 * <p>为什么这样写：① 证书台账独立成表（而非塞进通用 certificate_info），因为人员证书的检索维度是
 * "人 + 类型 + 专业 + 有效期"，且要按人反查；② 影像仍复用通用台账（binded_cert_id）与 FileApi（file_id），
 * 遵守 v3.3 §十五"证照影像归 certificate_info、业务要素归资质域"的边界，不重复存文件；
 * ③ register_unit/register_unit_code 单列，是"人证合一（防挂证）"核验的直接依据。
 * <p>业务目标：证书集中管理 + 分类检索 + 到期自动预警 + 投标配证取数。
 */
@TableName("personnel_certificate")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelCertificateDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 归属企业 */
    private Long entId;
    /** 持证人（bidding_personnel_profile.id） */
    private Long personId;
    /** 证书类型 code（PersonnelCertificateTypeEnum） */
    private String certType;
    /** 证书名称（照面全称） */
    private String certName;
    /** 证书编号（UK 组成，租户内同人同类型唯一） */
    private String certNo;
    /** 等级（一级/二级、安管人员 A/B/C 证等） */
    private String certLevel;
    /**
     * 证书细分 code（安管人员 A/B/C，C 可再分 C1/C2/C3；特种设备作业 G1/G2/G3；RT/UT 等级等）。
     *
     * <p>规范出处：《资质管理/06-电子证照设计材料对比与推进依据-2026-09-10.md》§二-3（附录 A §3.1/§3.4
     * 的细分口径：安许证动态核查按 A/B/C 三类计数、特种设备作业按 G1/G2/G3 区分）；
     * 值为 code，明细由主系统字典维护（v3.3 §17.6 码表口径，扩码不改 DDL）。
     * <p>业务目标：支撑"安许证 A/B/C 三类人员达标统计"与按细分工种检索，避免把不同工种的证混为一类。
     */
    private String certSubType;
    /** 专业（人岗匹配比对） */
    private String major;
    /** 学历层次 code（学历/学位证书用） */
    private String eduLevel;
    private String issuingAuthority;
    private LocalDate issueDate;
    private LocalDate validFrom;
    /** 有效期止（NULL=长期；到期预警扫描源） */
    private LocalDate validTo;
    /**
     * 下次复审/年审日期（与有效期相互独立的时间维度）。
     *
     * <p>规范出处：06 文档 §二-4（特种设备作业人员证"届满前 1 个月复审"、ISO 监督审核间隔 ≤12 个月，
     * 逾期证书被暂停/撤销——只看到期日会漏判"到期之前就已失效"）。
     * <p>业务目标：把"复审/年审逾期"也纳入预警，避免投标时用了实际已被暂停的证书。
     */
    private LocalDate nextReviewDate;
    /** 注册/执业单位名称（防挂证核验） */
    private String registerUnit;
    /** 注册单位统一社会信用代码（一致性核验键） */
    private String registerUnitCode;
    /** 注册号/执业印章号（与 15-3 reg_no 同口径） */
    private String registerNo;
    /** 台账状态 code（PersonnelCertStatusEnum；VALID/EXPIRING_SOON 才可用于配证） */
    private String certStatus;
    /** 是否已在四库一平台线上核验 */
    private Boolean platformVerified;
    private String verifyUrl;
    private LocalDateTime verifiedAt;
    /** 关联 certificate_info（复用影像/验真/变更审计） */
    private Long bindedCertId;
    /** 证书扫描件文件 id（FileApi） */
    private Long fileId;
    /** 是否当前有效记录（换证后旧证置 0） */
    private Boolean isCurrent;
    /** 数据来源 code：MANUAL/IMPORT/OCR/API_SYNC */
    private String sourceChannel;
    /** 动态扩展（JSON 串） */
    private String extraProps;
    private String remark;
    /** 乐观锁版本 */
    @Version
    private Integer version;
}
