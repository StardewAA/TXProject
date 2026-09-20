package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpLogisticsPageRespVO {

    private Long id;
    private String logisticsTaskNo;
    private String waybillNo;
    private String sourceOrderType;
    private String sourceOrderNo;
    private String carrierName;
    private String logisticsStatus;
    private String latestNodeName;
    private LocalDateTime latestNodeTime;
    private String latestLocation;
    private LocalDateTime estimatedArrivalTime;
    private Boolean locationOnline;
    private Boolean hasException;

}
