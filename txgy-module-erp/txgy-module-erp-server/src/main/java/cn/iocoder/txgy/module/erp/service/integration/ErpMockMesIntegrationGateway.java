package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderResumeReqDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ERP → MES 推送网关（mock 模式）：MES 未启用时返回契约一致的模拟结果，不真正调用 MES。
 * 默认生效（未配置 {@code txgy.integration.mes.mode} 时）。
 *
 * @author OPENLAB BS
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.mes", name = "mode", havingValue = "mock", matchIfMissing = true)
public class ErpMockMesIntegrationGateway implements ErpMesIntegrationGateway {

    @Override
    public MesIntegrationAcceptedRespDTO upsertMaterial(MesMaterialUpsertReqDTO reqDTO) {
        return accept(reqDTO.getRequestId(), reqDTO.getMaterialCode(), "SYNCED", reqDTO.getDataVersion());
    }

    @Override
    public MesIntegrationAcceptedRespDTO upsertPartner(MesPartnerUpsertReqDTO reqDTO) {
        return accept(reqDTO.getRequestId(), reqDTO.getPartnerCode(), "SYNCED", reqDTO.getDataVersion());
    }

    @Override
    public MesIntegrationAcceptedRespDTO createWorkOrder(MesWorkOrderCreateReqDTO reqDTO) {
        return accept(reqDTO.getRequestId(), "WO-MOCK-" + reqDTO.getOrderSourceCode(), "CREATED",
                reqDTO.getDataVersion());
    }

    @Override
    public MesIntegrationAcceptedRespDTO resumeWorkOrder(MesWorkOrderResumeReqDTO reqDTO) {
        return accept(reqDTO.getRequestId(), reqDTO.getWorkOrderCode(), "RESUMED", reqDTO.getDataVersion());
    }

    private MesIntegrationAcceptedRespDTO accept(String requestId, String targetCode, String status, Long version) {
        return new MesIntegrationAcceptedRespDTO()
                .setAccepted(true)
                .setRequestId(requestId)
                .setTargetCode(targetCode)
                .setBusinessStatus(status)
                .setProcessedVersion(version);
    }

}
