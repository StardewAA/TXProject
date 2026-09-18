package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.time.LocalDate;

/**
 * 资质延续申报请求。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-7（RENEW 延续、SUBMITTED 提交、预计办结 due_date）。
 */
@Data
public class QualificationRenewReqVO {

    @NotNull(message = "资质不能为空")
    private Long qualificationId;
    @NotNull(message = "版本不能为空")
    private Integer version;
    /** 预计办结日期 */
    private LocalDate dueDate;
    /** 备注 */
    private String remark;
}
