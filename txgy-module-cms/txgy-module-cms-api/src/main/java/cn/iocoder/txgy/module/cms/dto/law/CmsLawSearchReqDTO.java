package cn.iocoder.txgy.module.cms.dto.law;

import cn.iocoder.txgy.module.cms.enums.CmsLawStatusEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;

/** 法规检索条件。不提供可由客户端伪造的 tenantId 字段。 */
@Data
public class CmsLawSearchReqDTO {

    /** 法规名称、文号、条款关键词或自然语言问题。 */
    @NotBlank
    @Size(max = 200)
    private String keyword;

    /** 知识库范围：LAW 法律法规 / FINANCE_POLICY 金融政策 / QA 问答库；为空表示综合检索。 */
    @Size(max = 32)
    private String docCategory;

    @Size(max = 64)
    private String industryCode;

    @Size(max = 64)
    private String regionCode;

    @Size(max = 64)
    private String lawType;

    @Size(max = 200)
    private String issuingAuthority;

    private LocalDate effectiveDateFrom;

    private LocalDate effectiveDateTo;

    /** 金融政策筛选：所需支持金额下限（万元）。 */
    @DecimalMin("0")
    private BigDecimal amountMin;

    /** 金融政策筛选：所需支持金额上限（万元）。 */
    @DecimalMin("0")
    private BigDecimal amountMax;

    /** 金融政策状态：1 现行、2 即将截止、3 已废止。 */
    @Min(1)
    @Max(3)
    private Integer policyStatus;

    private LocalDate applyDeadlineFrom;

    private LocalDate applyDeadlineTo;

    /** 法律效力与平台发布状态不是同一概念。 */
    private CmsLawStatusEnum status;

    /** 查询时点，用于选择适用版本；不能仅按当前日期过滤历史问题。 */
    private LocalDate asOfDate;

    @NotNull
    @Min(1)
    private Integer pageNo = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

}
