package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - HRM 招聘 Offer 请求")
@Data
public class HrmRecruitOfferSaveReqVO {
    private Long id;
    @NotNull(message = "应聘编号不能为空") private Long applicationId;
    @NotNull(message = "职位编号不能为空") private Long postId;
    private BigDecimal salaryAmount;
    private Integer salaryUnit;
    private Long entryPostId;
    private LocalDate plannedOnboardDate;
    private LocalDate validUntil;
    private String attachmentIds;
    private String remark;
}
