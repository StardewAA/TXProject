package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 企业资质主档建档/更新请求（管理端；tenant/ent 由登录上下文确定）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1（字段口径）；
 * 《招投标模块开发规范》§3.1（自建 VO）/§4.4（Controller 参数校验）。
 */
public class EnterpriseQualificationSaveReqVO {

    /** 主键（更新必填） */
    private Long id;
    /** 乐观锁版本（更新必填） */
    private Integer version;
    /** 关联企业主体（必填） */
    @NotNull(message = "企业主体不能为空")
    private Long entId;
    /** 资质序列 code（必填） */
    @NotBlank(message = "资质序列不能为空")
    private String qualSerial;
    /** 资质类别（必填） */
    @NotBlank(message = "资质类别不能为空")
    private String qualCategory;
    /** 资质等级（必填） */
    @NotBlank(message = "资质等级不能为空")
    private String qualGrade;
    /** 资质证书编号（必填，租户内 UK） */
    @NotBlank(message = "资质证书编号不能为空")
    private String certNo;
    /** 发证/许可机关 */
    private String issuingAuthority;
    /** 发证日期 */
    private LocalDate issueDate;
    /** 有效期至（NULL=长期） */
    private LocalDate validTo;
    /** 批准承包/承揽范围 */
    private String approvalScope;
    /** 关联 certificate_info（影像/验真；存在性服务校验） */
    private Long bindedCertId;
    /** 备注 */
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getEntId() { return entId; }
    public void setEntId(Long entId) { this.entId = entId; }
    public String getQualSerial() { return qualSerial; }
    public void setQualSerial(String qualSerial) { this.qualSerial = qualSerial; }
    public String getQualCategory() { return qualCategory; }
    public void setQualCategory(String qualCategory) { this.qualCategory = qualCategory; }
    public String getQualGrade() { return qualGrade; }
    public void setQualGrade(String qualGrade) { this.qualGrade = qualGrade; }
    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }
    public String getIssuingAuthority() { return issuingAuthority; }
    public void setIssuingAuthority(String issuingAuthority) { this.issuingAuthority = issuingAuthority; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getApprovalScope() { return approvalScope; }
    public void setApprovalScope(String approvalScope) { this.approvalScope = approvalScope; }
    public Long getBindedCertId() { return bindedCertId; }
    public void setBindedCertId(Long bindedCertId) { this.bindedCertId = bindedCertId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
