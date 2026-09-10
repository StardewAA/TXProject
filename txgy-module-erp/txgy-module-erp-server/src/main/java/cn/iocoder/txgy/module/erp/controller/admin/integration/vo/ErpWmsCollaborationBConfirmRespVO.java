package cn.iocoder.txgy.module.erp.controller.admin.integration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "管理后台 - ERP 协作单 B 业务确认 Response VO")
@Data
@Accessors(chain = true)
public class ErpWmsCollaborationBConfirmRespVO {
    private Long collaborationId;
    private String collaborationNo;
    private String businessStatus;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
}
