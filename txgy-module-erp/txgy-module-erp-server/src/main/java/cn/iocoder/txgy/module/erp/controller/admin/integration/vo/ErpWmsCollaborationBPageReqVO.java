package cn.iocoder.txgy.module.erp.controller.admin.integration.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - ERP 供应链协同单分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpWmsCollaborationBPageReqVO extends PageParam {

    @Schema(description = "协同单类型：PURCHASE_COLLABORATION/SALE_COLLABORATION/AWARD_COLLABORATION/SHORTAGE_COLLABORATION")
    private String collaborationType;

    @Schema(description = "业务状态：PENDING_CONFIRMATION/NEED_SUPPLEMENT/CONFIRMING/ACCEPTED/REJECTED")
    private String businessStatus;

    @Schema(description = "协同单号，模糊匹配")
    private String collaborationNo;

    @Schema(description = "通用来源单号，模糊匹配")
    private String sourceOrderNo;

    @Schema(description = "业务链号，模糊匹配")
    private String businessChainNo;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @Schema(description = "接收时间范围")
    private LocalDateTime[] createTime;
}
