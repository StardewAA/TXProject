package cn.iocoder.txgy.module.mes.api.integration;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * RPC 服务 - ERP/MES 主数据集成
 *
 * ERP 为物料、往来企业主数据的权威来源，变更后推送到 MES，保证两端编码一致。
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - ERP/MES 主数据集成")
public interface MesIntegrationApi {

    String PREFIX = ApiConstants.PREFIX + "/integration";

    @PutMapping(PREFIX + "/master-data/material")
    @Operation(summary = "幂等新增或更新物料主数据")
    CommonResult<MesIntegrationAcceptedRespDTO> upsertMaterial(@Valid @RequestBody MesMaterialUpsertReqDTO reqDTO);

    @PutMapping(PREFIX + "/master-data/partner")
    @Operation(summary = "幂等新增或更新往来企业主数据（客户/供应商）")
    CommonResult<MesIntegrationAcceptedRespDTO> upsertPartner(@Valid @RequestBody MesPartnerUpsertReqDTO reqDTO);

    @PostMapping(PREFIX + "/work-order/create")
    @Operation(summary = "按 ERP 销售订单行项幂等创建生产工单")
    CommonResult<MesIntegrationAcceptedRespDTO> createWorkOrder(@Valid @RequestBody MesWorkOrderCreateReqDTO reqDTO);

}
