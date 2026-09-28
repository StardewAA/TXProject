package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import lombok.Data;


/**
 * 安全生产许可证建档请求。
 *
 * <p>规范出处：v3.3 表 15-8（permit_no UK；binded_cert_id 影像边界）。
 */
@Data
public class PermitCreateReqVO {
    /** 关联企业主体 */
    private Long entId;
    @jakarta.validation.constraints.NotBlank(message = "许可证编号不能为空")
    private String permitNo;
    private String issuingAuthority;
    private java.time.LocalDate issueDate;
    /** 有效期至（3 年一延续） */
    private java.time.LocalDate validTo;
    private String legalPerson;
    private Long bindedCertId;
    private String remark;
}
