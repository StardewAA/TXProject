package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 员工资质全景（人 + 证书 + 培训 + 社保 + 合同 一屏看清）。
 *
 * <p>规范出处：用户业务口径"将员工资质（人员信息）作为核心模块深度管理，并与企业资质、投标项目、财务等数据互联互通"；
 * 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（人员要素）、§十五-要素聚合（聚合视图同范式）。
 * <p>业务目标：替代"翻 Excel 找一个人的所有证"，一屏给出该员工可用于资质申报/投标的全部要素与风险提示。
 */
@Schema(description = "管理后台 - 员工资质全景")
@Data
public class PersonnelOverviewRespVO {

    @Schema(description = "员工档案")
    private PersonnelProfileRespVO profile;
    @Schema(description = "身份证是否在有效期内（身份证明基础）")
    private Boolean idCardValid;
    @Schema(description = "可用证书数")
    private Integer usableCertCount;
    @Schema(description = "在册证书数")
    private Integer totalCertCount;
    @Schema(description = "证书列表")
    private List<PersonnelCertRespVO> certificates;
    @Schema(description = "最近一期社保：缴纳月份")
    private String latestSocialSecurityMonth;
    @Schema(description = "最近一期社保：缴纳状态 code")
    private String latestSocialSecurityStatus;
    @Schema(description = "最近一期社保：缴纳单位名称")
    private String latestSocialSecurityPayer;
    @Schema(description = "社保是否由本企业缴纳")
    private Boolean socialSecurityConsistent;
    @Schema(description = "是否存在覆盖今天的有效劳动合同")
    private Boolean laborContractValid;
    @Schema(description = "劳动合同主体是否为本企业")
    private Boolean laborContractConsistent;
    @Schema(description = "累计继续教育学时")
    private String totalEduHours;
    @Schema(description = "风险提示（人证分离/证书过期/身份证过期/非全职用工等）")
    private List<String> risks;
    @Schema(description = "是否可用于投标配证（在职 + 身份证有效 + 至少一张可用证书）")
    private Boolean usableForTender;
}
