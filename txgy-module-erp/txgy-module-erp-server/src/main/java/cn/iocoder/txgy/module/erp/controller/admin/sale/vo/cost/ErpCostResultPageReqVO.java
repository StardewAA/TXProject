package cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErpCostResultPageReqVO extends PageParam {
    private String no;
    private String name;
    private String projectCode;
    private Integer status;
    private Integer businessStatus;
}
