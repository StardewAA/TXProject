package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 资质延续审批退回请求（申报 REJECTED，主档还原 VALID）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-7（REJECTED 退回）。
 */
@Data
public class QualificationRejectReqVO {

    @NotNull(message = "申报不能为空")
    private Long applyId;
    @NotNull(message = "版本不能为空")
    private Integer version;
    /** 退回原因 */
    private String remark;
}
