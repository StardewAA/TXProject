package cn.iocoder.txgy.module.mes.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;

/**
 * MES 生产工单集成 Service 接口
 *
 * 接收 ERP 推送的生产工单创建请求：工单宿主与生命周期归 MES，
 * ERP 订单仅作为工单来源之一（orderSourceType=ERP），按业务键幂等创建。
 */
public interface MesWorkOrderIntegrationService {

    /**
     * 幂等创建 ERP 来源生产工单
     *
     * @param reqDTO ERP 工单推送请求
     * @return 处理结果（业务状态 CREATED / EXISTED）
     */
    MesIntegrationAcceptedRespDTO createWorkOrder(MesWorkOrderCreateReqDTO reqDTO);

}
