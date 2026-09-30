package cn.iocoder.txgy.module.bidding.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证照台账主表（T 轨）。表：certificate_info（DDL 见 sql/history/2026-09-04-certificate.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二＋§18.1 v3.4 对齐；字段语义逐列对齐
 * GB/T 36903—2018 §5.2（证照元数据）与建办市〔2024〕51 号（17 信息项/ZZZT），列注释已标出处。
 * <p>设计遵循/针对：① 只存证照事实，审查/自查结论不落本表（v3.3 定位）；② 代码型字段（certType/certStatus/
 * verificationStatus/holderType/sourceChannel）存 code（开发规范 §5 三-7）；③ 文件不落本表、只留 eCertIdent
 * （GB/T 36904 标识），原件/电子证照文件实体见 CertFileVersionDO（开发规范 §5 三-5、v3.4 §18.1-1/2）；
 * ④ version 为乐观锁版本（开发规范 §4.5），更新走 CAS。
 */
@TableName("certificate_info")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertificateInfoDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 组织归属 */
    private Long orgId;
    /** 证照名称（GB/T 36903 ZZMC，AI 自查名称匹配） */
    private String certName;
    /** 证书编号（建办市51号 ZSBH；租户内唯一，UK uk_cert_no） */
    private String certNo;
    /** 证照大类 code（ADMIN_LICENSE/SYSTEM_CERT/PERSON_QUAL/HONOR/OTHER，v3.3 §二） */
    private String certType;
    /** 证照类型代码（GB/T 36902 ZZLXDM 21 位/行标） */
    private String certCategoryCode;
    /** 资质等级（一级/甲级…） */
    private String certLevel;
    /** 许可/业务范围（随表长文本） */
    private String certScope;
    /** 持证主体（GB/T 36903 CZZT；AI 自查一票否决） */
    private String holderName;
    /** 持证主体类别 code：ORG/PERSON/EXTERNAL */
    private String holderType;
    /** 关联档案 ID */
    private Long holderId;
    /** 证照颁发机构（GB/T 36903 ZZBFJG；L2） */
    private String issuingAuthority;
    /** 证照颁发机构代码（GB/T 36903 ZZBFJGDM） */
    private String issuingAuthorityCode;
    /** 证照颁发日期（GB/T 36903 ZZBFRQ） */
    private LocalDate issueDate;
    /** 证照有效期起始日期（GB/T 36903 ZZYXQQSRQ） */
    private LocalDate validFrom;
    /** 证照有效期截止日期（GB/T 36903 ZZYXQJZRQ；null=长期；到期预警扫描） */
    private LocalDate validTo;
    /** 适用行业（JSON 串） */
    private String applicableIndustries;
    /** 适用地区（JSON 串） */
    private String applicableRegions;
    /** 年检周期（月） */
    private Integer annualReviewCycle;
    /** 下次年检日 */
    private LocalDate nextReviewDate;
    /** 排他锁定（在建项目占用） */
    private Boolean isExclusiveLocked;
    /** 锁定明细（JSON 串） */
    private String lockInfo;
    /** 电子证照标识（GB/T 36904 编码，≤74 位；建办市51号 DZZZBZ） */
    private String eCertIdent;
    /** 台账状态 code（对齐 51号 ZZZT；自查只认 ACTIVE） */
    private String certStatus;
    /** 验真状态 code（UNVERIFIED/VERIFYING/VERIFIED/INVALID；与 certStatus 独立） */
    private String verificationStatus;
    /** 最近验真时间 */
    private LocalDateTime lastVerifiedAt;
    /** 验真来源 */
    private String verifyApiSource;
    /** 官方核验链接（展示） */
    private String officialVerifyUrl;
    /** 数据来源 code（MANUAL/IMPORT/OCR/API_SYNC） */
    private String sourceChannel;
    /** 动态扩展（JSON 串，如 cmmi_level） */
    private String extraProps;
    /** 备注 */
    private String remark;
    /** 乐观锁版本（开发规范 §4.5；CAS 更新） */
    @Version
    private Integer version;
}
