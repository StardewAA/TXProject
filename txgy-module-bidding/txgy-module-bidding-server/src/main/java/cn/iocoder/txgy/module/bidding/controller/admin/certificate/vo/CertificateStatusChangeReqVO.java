package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 证照台账状态流转请求（管理端）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二/§十三（状态口径、STATUS_CHANGE/REVOKE 日志）；
 * 51号 ZZZT 状态语义由 CertificateStatusEnum 迁移表约束（开发规范 §4.5 状态流转走接口）。
 */
@Data
public class CertificateStatusChangeReqVO {

    /** 证照主键 */
    @NotNull(message = "证照不能为空")
    private Long certificateId;

    /** 目标状态 code（ACTIVE/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED/CANCELLED） */
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    /** 乐观锁版本（来自最新详情，§4.5） */
    @NotNull(message = "版本不能为空")
    private Integer version;

    /** 流转原因/备注（吊销时必填） */
    private String reason;
}
