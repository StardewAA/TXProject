package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 证照台账 建档/更新 自建 VO（页面/内部接口，非上报全量 VO —— 开发规范 §3.1）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二 表 1 字段口径＋§18.1 v3.4；
 * GB/T 36903—2018 §5.2（英文名/短名见各字段注释）；字段密级标注按《招投标模块开发规范》§4.8。
 * <p>设计遵循/针对：① 建档（id=null）走 createCertificate：cert_no 租户内唯一（UK），重复建档抛 1-060-106-001；
 * ② 更新（id/version 必填）走 updateCertificate：乐观锁 version CAS（§4.5）；③ 业务键字段（certNo/certType/holder）
 * 建档后不可改，更正走变更环节（后续 M 里程碑落 cert_change_record 表，v3.3 §七）。
 */
public class CertificateSaveReqVO {

    /** 建档校验组（controller create 用） */
    public interface Create {
    }

    /** 更新校验组（controller update 用） */
    public interface Update {
    }


    /** 主键 ID（更新必填；建档为空） */
    @NotNull(groups = Update.class, message = "证照不能为空")
    private Long id;
    /** 乐观锁版本（更新必填，来自最新查询；§4.5 乐观锁） */
    @NotNull(groups = Update.class, message = "版本不能为空")
    private Integer version;
    /** 证照名称（GB/T 36903 ZZMC；L2） */
    @NotBlank(groups = {Create.class, Update.class}, message = "证照名称不能为空")
    private String certName;
    /** 证书编号（建办市51号 ZSBH；L3 敏感——日志/列表脱敏，存储不加密） */
    @NotBlank(groups = Create.class, message = "证书编号不能为空")
    private String certNo;
    /** 证照大类 code：ADMIN_LICENSE/SYSTEM_CERT/PERSON_QUAL/HONOR/OTHER（v3.3 §二） */
    @NotBlank(groups = Create.class, message = "证照大类不能为空")
    private String certType;
    /** 证照类型代码（GB/T 36902 ZZLXDM 21 位/行标；L2） */
    private String certCategoryCode;
    /** 资质等级（一级/甲级…） */
    private String certLevel;
    /** 许可/业务范围 */
    private String certScope;
    /** 持证主体（GB/T 36903 CZZT；L2） */
    @NotBlank(groups = Create.class, message = "持证主体不能为空")
    private String holderName;
    /** 持证主体类别 code：ORG/PERSON/EXTERNAL（缺省 ORG） */
    private String holderType;
        private Long holderId;
    /** 证照颁发机构（GB/T 36903 ZZBFJG；L2） */
    private String issuingAuthority;
    /** 证照颁发机构代码（GB/T 36903 ZZBFJGDM，统一社会信用代码；L2） */
    private String issuingAuthorityCode;
    /** 证照颁发日期（GB/T 36903 ZZBFRQ） */
    private LocalDate issueDate;
    /** 证照有效期起始日期（GB/T 36903 ZZYXQQSRQ） */
    private LocalDate validFrom;
    /** 证照有效期截止日期（GB/T 36903 ZZYXQJZRQ；null=长期） */
    private LocalDate validTo;
    /** 备注（L2） */
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCertName() { return certName; }
    public void setCertName(String certName) { this.certName = certName; }
    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }
    public String getCertType() { return certType; }
    public void setCertType(String certType) { this.certType = certType; }
    public String getCertCategoryCode() { return certCategoryCode; }
    public void setCertCategoryCode(String certCategoryCode) { this.certCategoryCode = certCategoryCode; }
    public String getCertLevel() { return certLevel; }
    public void setCertLevel(String certLevel) { this.certLevel = certLevel; }
    public String getCertScope() { return certScope; }
    public void setCertScope(String certScope) { this.certScope = certScope; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getHolderType() { return holderType; }
    public void setHolderType(String holderType) { this.holderType = holderType; }
    public Long getHolderId() { return holderId; }
    public void setHolderId(Long holderId) { this.holderId = holderId; }

    public String getIssuingAuthority() { return issuingAuthority; }
    public void setIssuingAuthority(String issuingAuthority) { this.issuingAuthority = issuingAuthority; }
    public String getIssuingAuthorityCode() { return issuingAuthorityCode; }
    public void setIssuingAuthorityCode(String issuingAuthorityCode) { this.issuingAuthorityCode = issuingAuthorityCode; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
