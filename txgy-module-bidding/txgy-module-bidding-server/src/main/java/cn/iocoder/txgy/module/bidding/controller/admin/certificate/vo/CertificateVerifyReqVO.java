package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 证照验真发起请求（管理端）。
 *
 * <p>规范出处：GB/T 36906—2018 §6.4（verifyinfo）；《电子证件全流程管理_数据库设计.md》v3.3 §17.5（验真历史）；
 * 渠道缺省 FOUR_PLATFORM（四库一平台）。
 */
@Data
public class CertificateVerifyReqVO {

    /** 被验真证照主键 */
    @NotNull(message = "证照不能为空")
    private Long certId;

    /** 验真渠道 code（缺省 FOUR_PLATFORM）：FOUR_PLATFORM/MINISTRY/PROVINCE/THIRD_PARTY */
    private String verifyChannel;
}
