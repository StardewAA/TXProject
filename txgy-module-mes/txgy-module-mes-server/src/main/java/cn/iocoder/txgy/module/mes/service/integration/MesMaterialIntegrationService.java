package cn.iocoder.txgy.module.mes.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;

/**
 * MES 物料主数据集成 Service 接口
 *
 * 接收 ERP 推送的物料主数据，按 ERP 权威物料编码幂等落库到 MES。
 */
public interface MesMaterialIntegrationService {

    /**
     * 幂等新增或更新物料主数据
     *
     * @param reqDTO ERP 物料主数据推送请求
     * @return 处理结果（业务状态 CREATED / UPDATED）
     */
    MesIntegrationAcceptedRespDTO upsertMaterial(MesMaterialUpsertReqDTO reqDTO);

}
