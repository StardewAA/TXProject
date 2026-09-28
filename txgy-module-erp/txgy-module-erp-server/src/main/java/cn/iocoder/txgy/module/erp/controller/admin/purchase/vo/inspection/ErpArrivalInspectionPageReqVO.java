package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - ERP到货检验分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpArrivalInspectionPageReqVO extends PageParam {
    @Schema(description = "检验单号")
    private String no;
    @Schema(description = "采购订单号")
    private String purchaseOrderNo;
    @Schema(description = "供应商编号")
    private Long supplierId;
    @Schema(description = "检验结论：1 合格，2 让步接收，3 退货")
    private Integer result;
    @Schema(description = "审批状态")
    private Integer status;
    @Schema(description = "检验时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] inspectTime;
}
