package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErpLogisticsPageReqVO extends PageParam {

    private String logisticsTaskNo;
    private String waybillNo;
    private String sourceOrderType;
    private String sourceOrderNo;
    private String logisticsStatus;
    private Boolean hasException;

}
