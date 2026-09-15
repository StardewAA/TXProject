package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import lombok.Data;


/**
 * 达标核查执行请求。
 *
 * <p>规范出处：v3.3 表 15-6（SELF/DYNAMIC/PRE_APPLY；快照 + 三档结论）。
 */
@Data
public class ComplianceReviewRunReqVO {
    @jakarta.validation.constraints.NotNull(message = "资质不能为空")
    private Long qualId;
    /** 核查类型 code（缺省 SELF） */
    private String reviewType;
    /** 申报前核查关联申报流 */
    private Long linkedApplyId;
}
