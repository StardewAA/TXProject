package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "ERP - Mall shortage procurement demand response")
@Data
public class MallPurchaseDemandRespVO {

    private String requestId;
    private String mallDemandNo;
    private Boolean accepted;
    private String erpRequestNo;
    private String erpOrderNo;
    private String status;
    private String reasonCode;
    private String message;
    private LocalDate estimatedArrivalDate;

}
