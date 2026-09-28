package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpLogisticsEventPageRespVO {

    private Long id;
    private String requestId;
    private String logisticsTaskNo;
    private String eventType;
    private Long eventVersion;
    private String processStatus;
    private String processMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
