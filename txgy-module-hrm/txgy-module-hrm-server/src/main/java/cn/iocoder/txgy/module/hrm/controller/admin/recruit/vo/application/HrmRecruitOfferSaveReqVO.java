package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - HRM 招聘 Offer 请求")
@Data
public class HrmRecruitOfferSaveReqVO {
    private Long id;
    @NotNull(message = "应聘编号不能为空") private Long applicationId;
    @NotNull(message = "职位编号不能为空") private Long postId;
    @NotNull(message = "Offer 薪资不能为空")
    @DecimalMin(value = "0.01", message = "Offer 薪资必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "Offer 薪资最多 10 位整数和 2 位小数")
    private BigDecimal salaryAmount;
    @NotNull(message = "薪资单位不能为空") private Integer salaryUnit;
    private Long entryPostId;
    @NotNull(message = "计划入职日期不能为空") private LocalDate plannedOnboardDate;
    @NotNull(message = "Offer 有效期不能为空") private LocalDate validUntil;
    private String attachmentIds;
    @Size(max = 500, message = "Offer 备注不能超过 500 个字符")
    private String remark;
}
