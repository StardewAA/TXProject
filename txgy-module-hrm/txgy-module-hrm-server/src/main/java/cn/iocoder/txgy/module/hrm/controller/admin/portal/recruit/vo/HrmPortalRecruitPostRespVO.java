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
    private Integer jobNature;
    private Integer areaId;
    private String areaName;
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
