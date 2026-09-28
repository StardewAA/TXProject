package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 员工社保缴纳记录出参。
 *
 * <p>规范出处：用户业务口径（社保缴纳单位一致性核验）；《电子证件全流程管理_数据库设计.md》v3.3 表 15-3。
 * <p>业务目标：展示社保缴纳证据链，并标注该期是否由本企业缴纳（便于人工复核挂证风险）。
 */
@Schema(description = "管理后台 - 员工社保缴纳记录出参")
@Data
public class PersonnelSocialSecurityRespVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "缴纳单位（本企业主体 id）")
    private Long entId;
    @Schema(description = "参保员工")
    private Long personId;
    @Schema(description = "缴纳单位名称")
    private String payerName;
    @Schema(description = "缴纳单位统一社会信用代码")
    private String payerCreditCode;
    @Schema(description = "缴纳月份（yyyy-MM）")
    private String payMonth;
    @Schema(description = "缴纳状态 code")
    private String payStatus;
    @Schema(description = "缴费基数")
    private BigDecimal payBase;
    @Schema(description = "缴费金额")
    private BigDecimal payAmount;
    @Schema(description = "社保个人编号")
    private String socialNo;
    @Schema(description = "数据来源 code")
    private String sourceChannel;
    @Schema(description = "参保证明文件 id")
    private Long evidenceFileId;
    @Schema(description = "备注")
    private String remark;
}
