package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.company;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("hrm_recruit_company_profile")
@KeySequence("hrm_recruit_company_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitCompanyProfileDO extends TenantBaseDO {

    @TableId
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
