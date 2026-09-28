package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "员工端 - 招聘职位 Response VO")
@Data
public class HrmPortalRecruitPostRespVO {
    private Long id;
    private Long tenantId;
    private String postName;
    private Long postTypeId;
    private String postTypeName;
    /**
     * 岗位所属的二级分类。
     *
     * 岗位挂的是三级工种（防腐工、瓦工…），而门户分类目录只暴露到二级
     * （防腐蚀作业、土建配套…），三级到二级的父子关系只存在库里。
     * 门户前端（公司页按职位类型筛选）要跟职位页用同一套词表，就得靠这两个字段，
     * 否则只能拿到三级名，没法归到二级。
     */
    private Long postTypeParentId;
    private String postTypeParentName;
    private Integer jobNature;
    private Integer areaId;
    private String areaName;
    private String workAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String companyName;
    private String companyShortName;
    private String companyLogo;
    private String companyIndustry;
    private String companyScale;
    private String companyFinancingStage;
    private Long deptId;
    private String deptName;
    private Integer recruitNum;
    /** 招聘咨询联系人的系统用户编号，优先负责人，未配置时为岗位创建人。 */
    private Long ownerUserId;
    /** 招聘咨询联系人的昵称。 */
    private String ownerUserName;
    /** 招聘咨询联系人的头像。 */
    private String ownerUserAvatar;
    private Integer workTime;
    private Integer educationRequire;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private Integer salaryUnit;
    private Integer minAge;
    private Integer maxAge;
    private LocalDateTime latestEntryTime;
    private String description;
    private Integer emergencyLevel;
    private Integer status;
    private LocalDateTime createTime;
}
