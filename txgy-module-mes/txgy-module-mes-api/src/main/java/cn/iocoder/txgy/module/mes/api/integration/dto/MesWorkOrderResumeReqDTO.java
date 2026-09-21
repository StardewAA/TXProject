package cn.iocoder.txgy.module.mes.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RPC 服务 - ERP 通知 MES 恢复缺料工单的请求
 *
 * 缺料采购入库完成后，ERP 通过协作单 B 的来源生产工单号（sourceProductionOrderNo）通知 MES
 * 重新向 WMS 申请领料，使因原料缺口挂起的生产工单恢复执行（设计 7.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesWorkOrderResumeReqDTO extends MesIntegrationBaseReqDTO {

    /**
     * MES 生产工单编码（协作单 B 的 sourceProductionOrderNo）
     */
    @NotBlank(message = "workOrderCode 不能为空")
    private String workOrderCode;

    private String remark;

    /**
     * 数据版本号（幂等与乐观锁依据）
     */
    @NotNull(message = "dataVersion 不能为空")
    private Long dataVersion;

}
