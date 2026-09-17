package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;

/**
 * ERP → MES 推送网关
 *
 * 与 {@link ErpWmsIntegrationGateway} 同构：主数据（物料 / 客户 / 供应商）与工单创建请求推送到 MES。
 */
public interface ErpMesIntegrationGateway {

    /**
     * 推送物料主数据（幂等 upsert）
     */
    MesIntegrationAcceptedRespDTO upsertMaterial(MesMaterialUpsertReqDTO reqDTO);

    /**
     * 推送客户 / 供应商主数据（幂等 upsert）
     */
    MesIntegrationAcceptedRespDTO upsertPartner(MesPartnerUpsertReqDTO reqDTO);

    /**
     * 推送生产工单创建请求（MES 幂等建单）
     */
    MesIntegrationAcceptedRespDTO createWorkOrder(MesWorkOrderCreateReqDTO reqDTO);

}
