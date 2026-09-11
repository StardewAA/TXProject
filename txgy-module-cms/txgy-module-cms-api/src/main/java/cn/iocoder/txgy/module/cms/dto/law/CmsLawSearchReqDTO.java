package cn.iocoder.txgy.module.cms.dto.law;

import cn.iocoder.txgy.module.cms.enums.CmsLawStatusEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 法规检索条件。不提供可由客户端伪造的 tenantId 字段。 */
@Data
public class CmsLawSearchReqDTO {

    /** 法规名称、文号、条款关键词或自然语言问题。 */
    @NotBlank
    @Size(max = 200)
    private String keyword;

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
