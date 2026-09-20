package cn.iocoder.txgy.module.trade.api.order.dto;

import lombok.Data;

/** Minimal internal customer-service projection; never contains address, phone, IP or payment identifiers. */
@Data
public class TradeOrderServiceCardRespDTO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long shopId;
    private String no;
    private Integer payPrice;
    private Integer status;
}
