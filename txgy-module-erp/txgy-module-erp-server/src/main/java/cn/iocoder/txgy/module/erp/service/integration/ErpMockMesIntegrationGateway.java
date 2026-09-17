package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ERP → MES 主数据推送模拟实现（默认生效）。
 *
 * 不发起真实 RPC，仅记录日志并返回契约一致的接受响应，
 * 用于 MES 服务未部署 / 未切换 remote 模式时保证 ERP 主流程不受影响。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.mes", name = "mode", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class ErpMockMesIntegrationGateway implements ErpMesIntegrationGateway {

    @Override
    public MesIntegrationAcceptedRespDTO upsertMaterial(MesMaterialUpsertReqDTO reqDTO) {
        log.info("[upsertMaterial][模拟推送 MES 物料({} - {})]", reqDTO.getMaterialCode(), reqDTO.getName());
        return accept(reqDTO.getRequestId(), reqDTO.getMaterialCode(), reqDTO.getDataVersion());
    }

    @Override
    public MesIntegrationAcceptedRespDTO upsertPartner(MesPartnerUpsertReqDTO reqDTO) {
        log.info("[upsertPartner][模拟推送 MES 往来企业({} - {} / {})]", reqDTO.getPartnerCode(),
                reqDTO.getName(), reqDTO.getPartnerType());
        return accept(reqDTO.getRequestId(), reqDTO.getPartnerCode(), reqDTO.getDataVersion());
    }

    private MesIntegrationAcceptedRespDTO accept(String requestId, String targetCode, Long dataVersion) {
        return new MesIntegrationAcceptedRespDTO().setAccepted(true)
                .setRequestId(requestId)
                .setTargetCode(targetCode)
                .setBusinessStatus("SYNCED")
                .setProcessedVersion(dataVersion);
    }

    @Override
    public MesIntegrationAcceptedRespDTO createWorkOrder(MesWorkOrderCreateReqDTO reqDTO) {
        log.info("[createWorkOrder][模拟推送 MES 工单（来源单号({}) 物料({}) 数量({})）]",
                reqDTO.getOrderSourceCode(), reqDTO.getItemCode(), reqDTO.getQuantity());
        return new MesIntegrationAcceptedRespDTO().setAccepted(true)
                .setRequestId(reqDTO.getRequestId())
                .setTargetCode("WO-MOCK-" + reqDTO.getOrderSourceCode() + "-" + reqDTO.getItemCode())
                .setBusinessStatus("CREATED")
                .setProcessedVersion(reqDTO.getDataVersion());
    }

}
