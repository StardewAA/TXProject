package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ErpLogisticsEventRespVO {

    private String requestId;
    private String logisticsTaskNo;
    private Long eventVersion;
    private String processStatus;
    private String processMessage;
    private String logisticsStatus;

}
