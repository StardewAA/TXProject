package cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ErpCostResultConvertReqVO {
    @NotNull(message = "造价结果不能为空")
    private Long id;
    @NotNull(message = "转换类型不能为空")
    private Integer convertedType;
}
