package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工证书登记/更新入参。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（注册/执业编号、注册有效期）与 §一
 * （人员类证书走人员扩展）；《电子招标投标办法》（20号令）第42条（电子证书编号应可公开）；
 * 《招投标模块开发规范》§5 三-5（文件经 FileApi 上传，业务表只存 file_id）。
 * <p>业务目标：证书集中入库、分类可检索、有效期可预警、注册单位可核验（防挂证）。
 */
@Schema(description = "管理后台 - 员工证书登记/更新入参")
@Data
public class PersonnelCertificateSaveReqVO {

    @Schema(description = "主键（更新时必填）")
    private Long id;

    @Schema(description = "归属企业", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long entId;

    @Schema(description = "持证员工", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "持证员工不能为空")
    private Long personId;

    @Schema(description = "证书类型 code：ID_CARD/EDUCATION_CERT/DEGREE_CERT/TITLE_CERT/REG_PRACTICE_CERT/SAFETY_ASSESS_CERT/SKILL_CERT/TRAINING_CERT/OTHER",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "REG_PRACTICE_CERT")
    @NotBlank(message = "证书类型不能为空")
    private String certType;

    @Schema(description = "证书名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "一级建造师注册证书")
    @NotBlank(message = "证书名称不能为空")
    private String certName;

    @Schema(description = "证书编号（同人同类型唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "610000000001")
    @NotBlank(message = "证书编号不能为空")
    private String certNo;

    @Schema(description = "等级（一级/二级、安管人员 A/B/C 证）", example = "一级")
    private String certLevel;

    @Schema(description = "证书细分 code（安管人员 A/B/C、C1/C2/C3；特种设备作业 G1/G2/G3；RT/UT 等级）",
            example = "B")
    private String certSubType;

    @Schema(description = "下次复审/年审日期（司炉证届满前 1 个月复审、ISO 监督审核 ≤12 个月；与有效期独立）")
    private LocalDate nextReviewDate;

    @Schema(description = "专业（人岗匹配比对）", example = "建筑工程")
    private String major;

    @Schema(description = "学历层次 code（学历/学位证书用）", example = "BACHELOR")
    private String eduLevel;

    @Schema(description = "发证机关")
    private String issuingAuthority;

    @Schema(description = "发证日期")
    private LocalDate issueDate;

    @Schema(description = "有效期起始")
    private LocalDate validFrom;

    @Schema(description = "有效期止（身份证/执业/职称/安全类必填，预警依赖；长期可空）")
    private LocalDate validTo;

    @Schema(description = "注册/执业单位名称（防挂证核验）", example = "陕西××建设集团有限公司")
    private String registerUnit;

    @Schema(description = "注册单位统一社会信用代码")
    private String registerUnitCode;

    @Schema(description = "注册号/执业印章号")
    private String registerNo;

    @Schema(description = "是否已在四库一平台线上核验", example = "false")
    private Boolean platformVerified;

    @Schema(description = "官方核验链接")
    private String verifyUrl;

    @Schema(description = "关联通用台账 certificate_info.id（复用影像/验真/变更审计，可空）")
    private Long bindedCertId;

    @Schema(description = "证书扫描件文件 id（FileApi 上传，可空）")
    private Long fileId;

    @Schema(description = "动态扩展（JSON 串，如继续教育学时）")
    private String extraProps;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本（更新时回传）")
    private Integer version;
}
