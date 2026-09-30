package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 企业人员配置行（要素台账 · 人员）。
 *
 * <p>规范出处：契约 §4 G5；表 15-3 qualification_staff_config。
 *
 * <p>为什么这样写（**安全要点**）：源表含 {@code id_card_enc}（身份证密文）与 {@code id_card_blind_index}
 * （盲索引），二者**一律不出接口**——密文出接口等于把 L4 加密形同虚设，盲索引出接口则可被用来做
 * 跨库关联比对。前端展示人员身份一律走姓名 + 证书编号，需要证件信息时跳证照台账（bindedCertId）。
 * 这是"最小必要字段"原则在 DTO 上的落地，而不是靠前端不显示。
 *
 * <p>业务目标：要素台账可查、可用于达标核查对账，同时不给下游任何身份证件的旁路。
 */
@Data
public class QualificationStaffRespVO {

    @Schema(description = "记录 id", example = "7001")
    private Long id;

    @Schema(description = "企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "资质主档 id", example = "9001")
    private Long qualId;

    @Schema(description = "岗位/角色（如：项目负责人 / 技术负责人 / 注册建造师）", example = "注册建造师")
    private String staffRole;

    @Schema(description = "姓名", example = "张三")
    private String personName;

    @Schema(description = "注册专业", example = "市政公用工程")
    private String major;

    @Schema(description = "注册/执业证书编号", example = "陕 1612***")
    private String regNo;

    @Schema(description = "职称/等级", example = "高级工程师")
    private String titleLevel;

    @Schema(description = "是否专职", example = "true")
    private Boolean isFullTime;

    @Schema(description = "社保缴纳状态", example = "NORMAL")
    private String socialSecurityStatus;

    @Schema(description = "社保缴纳主体", example = "本企业")
    private String socialSecurityTenant;

    @Schema(description = "已修学时", example = "48")
    private BigDecimal eduHours;

    @Schema(description = "要求学时（核对用；null=该岗位无学时要求）", example = "48")
    private BigDecimal eduHoursRequired;

    @Schema(description = "注册有效期至（驱动人员到期预警）", example = "2026-08-31")
    private LocalDate regValidTo;

    @Schema(description = "绑定证照台账 id（人员证件影像）", example = "512")
    private Long bindedCertId;

    @Schema(description = "乐观锁版本", example = "1")
    private Integer version;
}
