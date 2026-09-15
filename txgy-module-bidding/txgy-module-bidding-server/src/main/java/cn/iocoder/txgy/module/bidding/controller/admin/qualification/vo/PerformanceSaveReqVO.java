package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import lombok.Data;


/**
 * 工程业绩录入请求（qual_id 归属资质，§17.6）。
 *
 * <p>规范出处：v3.3 表 15-4。
 */
@Data
public class PerformanceSaveReqVO {
    @jakarta.validation.constraints.NotNull(message = "企业不能为空")
    private Long entId;
    @jakarta.validation.constraints.NotNull(message = "归属资质不能为空")
    private Long qualId;
    @jakarta.validation.constraints.NotBlank(message = "项目名称不能为空")
    private String projectName;
    private String projectType;
    private String role;
    private java.math.BigDecimal contractAmount;
    private java.time.LocalDate completionDate;
    private String qualityResult;
    private String ownerName;
    private Boolean isPlatformVerified;
    private String evidenceFileIds;
}
