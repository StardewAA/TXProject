package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证件生命周期记录分页查询入参。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2/§4.4（分页 PageParam）；
 * 《电子证件全流程管理_数据库设计.md》v3.3 §十四（按业务对象 + 状态建索引检索）。
 * <p>业务目标：管理端"资质变更/年检台账"页面按类型与状态筛选待办。
 */
@Schema(description = "管理后台 - 证件生命周期记录分页查询入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertLifecyclePageReqVO extends PageParam {

    @Schema(description = "业务对象域 code", example = "CERT")
    private String bizDomain;

    @Schema(description = "业务对象主键")
    private Long bizId;

    @Schema(description = "生命周期类型 code：CHANGE/RENEW/REVOKE/ANNUAL", example = "ANNUAL")
    private String lifecycleType;

    @Schema(description = "状态 code：PENDING/REVIEWING/APPROVED/REJECTED/EFFECTIVE", example = "PENDING")
    private String status;

    @Schema(description = "申请单号（精确）")
    private String applyNo;
}
