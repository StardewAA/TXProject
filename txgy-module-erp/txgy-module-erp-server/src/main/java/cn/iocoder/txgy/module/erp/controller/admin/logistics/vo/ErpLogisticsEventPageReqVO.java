package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErpLogisticsEventPageReqVO extends PageParam {

    private String logisticsTaskNo;
    private String eventType;
    private String processStatus;

}
