package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工证书出参。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3 与 §二（状态口径对齐建办市〔2024〕51号 ZZZT）；
 * 《招投标模块开发规范》§4.4-4（字典值存 code，展示名交前端翻译）。
 * <p>业务目标：证书台账页面直接可用；remainingDays 由服务层算好，避免前端各页各算一套到期口径。
 */
@Schema(description = "管理后台 - 员工证书出参")
@Data
public class PersonnelCertRespVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "归属企业")
    private Long entId;
    @Schema(description = "持证员工")
    private Long personId;
    @Schema(description = "持证人姓名（列表回显用）")
    private String personName;
    @Schema(description = "证书类型 code")
    private String certType;
    @Schema(description = "证书名称")
    private String certName;
    @Schema(description = "证书编号")
    private String certNo;
    @Schema(description = "等级")
    private String certLevel;
    @Schema(description = "证书细分 code（A/B/C、C1/C2/C3、G1/G2/G3）")
    private String certSubType;
    @Schema(description = "下次复审/年审日期")
    private LocalDate nextReviewDate;
    @Schema(description = "复审剩余天数（空=无复审要求；负数=已逾期）")
    private Long reviewRemainingDays;
    @Schema(description = "专业")
    private String major;
    @Schema(description = "学历层次 code")
    private String eduLevel;
    @Schema(description = "发证机关")
    private String issuingAuthority;
    @Schema(description = "发证日期")
    private LocalDate issueDate;
    @Schema(description = "有效期起始")
    private LocalDate validFrom;
    @Schema(description = "有效期止（空=长期）")
    private LocalDate validTo;
    @Schema(description = "剩余有效天数（空=长期；负数=已过期）")
    private Long remainingDays;
    @Schema(description = "注册/执业单位名称")
    private String registerUnit;
    @Schema(description = "注册单位统一社会信用代码")
    private String registerUnitCode;
    @Schema(description = "注册号")
    private String registerNo;
    @Schema(description = "证书状态 code")
    private String certStatus;
    @Schema(description = "是否四库一平台可核验")
    private Boolean platformVerified;
    @Schema(description = "官方核验链接")
    private String verifyUrl;
    @Schema(description = "最近核验时间")
    private LocalDateTime verifiedAt;
    @Schema(description = "关联通用台账 id")
    private Long bindedCertId;
    @Schema(description = "扫描件文件 id")
    private Long fileId;
    @Schema(description = "数据来源 code")
    private String sourceChannel;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "乐观锁版本")
    private Integer version;
}
