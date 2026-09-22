package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ErpLogisticsExceptionHandleReqVO {

    @NotNull(message = "异常编号不能为空")
    private Long id;
    @NotBlank(message = "处理结果不能为空")
    private String handleResult;

}
