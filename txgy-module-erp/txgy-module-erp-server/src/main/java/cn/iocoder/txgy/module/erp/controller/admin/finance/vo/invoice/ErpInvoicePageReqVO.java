package cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - ERP发票分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpInvoicePageReqVO extends PageParam {
    private String no;
    private String invoiceNo;
    private Integer bizType;
    private Integer invoiceType;
    private Integer status;
    private String title;
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] invoiceDate;
}
