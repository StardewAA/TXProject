package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工档案出参（管理端）。
 *
 * <p>规范出处：《招投标模块开发规范》§4.8（L3 列表默认掩码、L4 任何出参只回掩码）——本 VO 不回传
 * 身份证密文，只回 {@code idCardMasked}；《电子证件全流程管理_数据库设计.md》v3.3 表 15-3 字段口径。
 * <p>业务目标：页面直接可用的员工档案视图，同时不把高敏明文带出服务端。
 */
@Schema(description = "管理后台 - 员工档案出参")
@Data
public class PersonnelProfileRespVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "租户编号")
    private Long tenantId;
    @Schema(description = "归属企业")
    private Long entId;
    @Schema(description = "员工编号")
    private String personNo;
    @Schema(description = "姓名")
    private String personName;
    @Schema(description = "身份证掩码（L4 只回掩码，如 610102********1234）")
    private String idCardMasked;
    @Schema(description = "身份证有效期止")
    private LocalDate idCardValidTo;
    @Schema(description = "性别 code")
    private String gender;
    @Schema(description = "出生日期")
    private LocalDate birthDate;
    @Schema(description = "手机（掩码）")
    private String mobile;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "学历层次 code")
    private String eduLevel;
    @Schema(description = "所学专业")
    private String eduMajor;
    @Schema(description = "毕业院校")
    private String school;
    @Schema(description = "毕业时间")
    private LocalDate graduateDate;
    @Schema(description = "职称等级 code")
    private String titleLevel;
    @Schema(description = "职称专业")
    private String titleMajor;
    @Schema(description = "参加工作时间")
    private LocalDate workStartDate;
    @Schema(description = "入职日期")
    private LocalDate joinDate;
    @Schema(description = "所属部门")
    private String deptName;
    @Schema(description = "岗位名称")
    private String positionName;
    @Schema(description = "用工形式 code")
    private String employmentType;
    @Schema(description = "是否本单位全职")
    private Boolean isFullTime;
    @Schema(description = "在职状态 code")
    private String personnelStatus;
    @Schema(description = "最近状态变更日期")
    private LocalDate statusChangeDate;
    @Schema(description = "人事档案号")
    private String archiveNo;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "乐观锁版本")
    private Integer version;
    @Schema(description = "在册证书数量")
    private Integer certCount;
    @Schema(description = "可用（有效/临近到期）证书数量")
    private Integer usableCertCount;
    @Schema(description = "身份证是否在有效期内")
    private Boolean idCardValid;
}
