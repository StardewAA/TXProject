package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.MesIntegrationApi;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ERP → MES 主数据推送远程实现（Feign 直连 txgy-mes-server）。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.mes", name = "mode", havingValue = "remote")
public class ErpRemoteMesIntegrationGateway implements ErpMesIntegrationGateway {

    @Resource
    private MesIntegrationApi mesIntegrationApi;

    @Override
    public MesIntegrationAcceptedRespDTO upsertMaterial(MesMaterialUpsertReqDTO reqDTO) {
        return mesIntegrationApi.upsertMaterial(reqDTO).getCheckedData();
    }

    @Override
    public MesIntegrationAcceptedRespDTO upsertPartner(MesPartnerUpsertReqDTO reqDTO) {
        return mesIntegrationApi.upsertPartner(reqDTO).getCheckedData();
    }

    @Override
    public MesIntegrationAcceptedRespDTO createWorkOrder(MesWorkOrderCreateReqDTO reqDTO) {
        return mesIntegrationApi.createWorkOrder(reqDTO).getCheckedData();
    }

}
