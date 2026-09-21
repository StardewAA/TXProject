package cn.iocoder.txgy.module.mes.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;

/**
 * MES 往来企业主数据集成 Service 接口
 *
 * 接收 ERP 推送的客户 / 供应商主数据，按 ERP 权威往来企业编码幂等落库到 MES
 * （CUSTOMER 落 mes_md_client，SUPPLIER 落 mes_md_vendor）。
 */
public interface MesPartnerIntegrationService {

    /**
     * 幂等新增或更新往来企业主数据
     *
     * @param reqDTO ERP 往来企业主数据推送请求
     * @return 处理结果（业务状态 CREATED / UPDATED）
     */
    MesIntegrationAcceptedRespDTO upsertPartner(MesPartnerUpsertReqDTO reqDTO);

}
