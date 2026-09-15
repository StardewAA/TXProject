package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.time.LocalDate;

/**
 * 资质延续审批通过请求（办结回写主档 valid_to/cert_no）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-7（APPROVED→ISSUED；new_cert_no/new_valid_to 办结回写）。
 */
@Data
public class QualificationApproveReqVO {

    @NotNull(message = "申报不能为空")
    private Long applyId;
    @NotNull(message = "版本不能为空")
    private Integer version;
    /** 批复/许可决定文号 */
    private String resultNo;
    /** 新证书编号（可空） */
    private String newCertNo;
    /** 延续后有效期至（必填） */
    @NotNull(message = "延续后有效期不能为空")
    private LocalDate newValidTo;
}
