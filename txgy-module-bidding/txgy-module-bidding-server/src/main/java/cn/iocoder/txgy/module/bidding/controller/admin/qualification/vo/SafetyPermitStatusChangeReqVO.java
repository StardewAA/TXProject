package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import lombok.Data;


/**
 * 安许状态流转请求（显式迁移表 + version CAS）。
 *
 * <p>规范出处：v3.3 表 15-8。
 */
@Data
public class SafetyPermitStatusChangeReqVO {
    @jakarta.validation.constraints.NotNull(message = "安许不能为空")
    private Long permitId;
    @jakarta.validation.constraints.NotNull(message = "版本不能为空")
    private Integer version;
    @jakarta.validation.constraints.NotBlank(message = "目标状态不能为空")
    private String targetStatus;
    private String reason;
}
