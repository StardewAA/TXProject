package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "用户端 - 招聘企业信息")
@Data
public class HrmPortalRecruitCompanyRespVO {

    private Long tenantId;
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
    private String areaName;
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
    private Long postCount;
}
