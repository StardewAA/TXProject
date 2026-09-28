package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 招聘企业主页保存 Request VO")
@Data
public class HrmRecruitCompanyProfileSaveReqVO {

    @Schema(description = "企业名称")
    @NotBlank
    @Size(max = 255)
    private String name;
    @Size(max = 128)
    private String shortName;
    @Size(max = 500)
    private String logo;
    @Size(max = 128)
    private String industry;
    @Size(max = 64)
    private String scale;
    @Schema(description = "融资阶段", allowableValues = {"不融资", "股权融资", "上市融资"})
    @Pattern(regexp = "不融资|股权融资|上市融资", message = "融资阶段只能选择不融资、股权融资或上市融资")
    private String financingStage;
    @Size(max = 64)
    private String nature;
    @Size(max = 2000)
    private String summary;
    @Size(max = 20000)
    private String products;
    @Size(max = 20000)
    private String album;
    @Size(max = 255)
    private String address;
    private Integer areaId;
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;
    @Size(max = 255)
    private String website;
    private LocalDate establishedDate;
    @Size(max = 64)
    private String registeredCapital;
    @Size(max = 64)
    private String creditCode;
    @Size(max = 64)
    private String legalPerson;
    @Size(max = 64)
    private String dataSource;
    @Size(max = 500)
    private String dataSourceUrl;
    private Boolean verified;
    private Integer status;
}
