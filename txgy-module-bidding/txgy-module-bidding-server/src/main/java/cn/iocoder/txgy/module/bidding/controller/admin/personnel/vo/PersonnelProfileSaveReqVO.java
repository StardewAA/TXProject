package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工档案建档/更新入参（管理端自建 VO，不对外交换）。
 *
 * <p>规范出处：《招投标模块开发规范》§3.1（页面/内部接口用自建 VO，放 controller/admin/&lt;域&gt;/vo）、
 * §4.4（参数校验用 jakarta.validation + @Validated）、§4.8（L4 字段只在入参出现明文，服务层立即转密文/盲索引）。
 * <p>为什么这样写：身份证号是 L4 高敏字段，录入环节只能收明文一次，服务层随即加密入库并在出参只回掩码；
 * 因此本 VO 有 idCard 字段而 RespVO 没有（防"明文出库"）。
 * <p>业务目标：员工基本信息集中登记，作为证书/培训/社保/劳动合同的挂载点与"一人一档"判重来源。
 */
@Schema(description = "管理后台 - 员工档案建档/更新入参")
@Data
public class PersonnelProfileSaveReqVO {

    @Schema(description = "主键（更新时必填）", example = "1024")
    private Long id;

    @Schema(description = "归属企业", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "归属企业不能为空")
    private Long entId;

    @Schema(description = "员工编号（企业内部唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "E2026001")
    @NotBlank(message = "员工编号不能为空")
    private String personNo;

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotBlank(message = "姓名不能为空")
    private String personName;

    @Schema(description = "身份证号（L4 明文仅入参；服务层加密存储并生成盲索引判重）", example = "610102199001011234")
    private String idCard;

    @Schema(description = "身份证有效期止（身份证到期预警扫描源）")
    private LocalDate idCardValidTo;

    @Schema(description = "性别 code：MALE/FEMALE/UNKNOWN", example = "MALE")
    private String gender;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "手机（L3）", example = "13800000000")
    private String mobile;

    @Schema(description = "邮箱（L3）")
    private String email;

    @Schema(description = "学历层次 code：DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER", example = "BACHELOR")
    private String eduLevel;

    @Schema(description = "所学专业", example = "土木工程")
    private String eduMajor;

    @Schema(description = "毕业院校")
    private String school;

    @Schema(description = "毕业时间")
    private LocalDate graduateDate;

    @Schema(description = "职称等级 code（初级/中级/高级/正高级，与 15-3 同口径）", example = "MIDDLE")
    private String titleLevel;

    @Schema(description = "职称专业", example = "建筑工程")
    private String titleMajor;

    @Schema(description = "参加工作时间（工作年限计算基准）")
    private LocalDate workStartDate;

    @Schema(description = "入职日期")
    private LocalDate joinDate;

    @Schema(description = "所属部门", example = "工程部")
    private String deptName;

    @Schema(description = "岗位名称", example = "项目经理")
    private String positionName;

    @Schema(description = "用工形式 code：FULL_TIME/PART_TIME/LABOR_DISPATCH/RETIRED_REHIRE/OTHER",
            example = "FULL_TIME")
    private String employmentType;

    @Schema(description = "人事档案号")
    private String archiveNo;

    @Schema(description = "关联平台登录用户 id（员工自助维护本人证书时使用，可空）")
    private Long bindedAuthUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本（更新时回传，防并发覆盖）", example = "0")
    private Integer version;
}
