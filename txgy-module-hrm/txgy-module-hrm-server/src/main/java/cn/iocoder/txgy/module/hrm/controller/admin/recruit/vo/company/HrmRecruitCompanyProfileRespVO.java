package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 招聘企业主页 Response VO")
@Data
public class HrmRecruitCompanyProfileRespVO {

    private Long id;
    private String name;
    private String shortName;
    private String logo;
    private String industry;
    private String scale;
    private String financingStage;
    private String nature;
    private String summary;
    private String products;
    private String album;
    private String address;
    private Integer areaId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String website;
    private LocalDate establishedDate;
    private String registeredCapital;
    private String creditCode;
    private String legalPerson;
    private String dataSource;
    private String dataSourceUrl;
    private Boolean verified;
    private Integer sort;
    private Integer status;
}
