package cn.iocoder.txgy.module.mes.api.integration;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import cn.iocoder.txgy.module.mes.service.integration.MesMaterialIntegrationService;
import cn.iocoder.txgy.module.mes.service.integration.MesPartnerIntegrationService;
import cn.iocoder.txgy.module.mes.service.integration.MesWorkOrderIntegrationService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * RPC 服务 - ERP/MES 主数据集成 Api 实现类
 *
 * 提供 RESTful API 接口给 Feign 调用（ERP remote 模式直连 mes-server）。
 */
@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class MesIntegrationApiImpl implements MesIntegrationApi {

    @Resource
    private MesMaterialIntegrationService materialIntegrationService;
    @Resource
    private MesPartnerIntegrationService partnerIntegrationService;
    @Resource
    private MesWorkOrderIntegrationService workOrderIntegrationService;

    @Override
    public CommonResult<MesIntegrationAcceptedRespDTO> upsertMaterial(MesMaterialUpsertReqDTO reqDTO) {
        return success(materialIntegrationService.upsertMaterial(reqDTO));
    }

    @Override
    public CommonResult<MesIntegrationAcceptedRespDTO> upsertPartner(MesPartnerUpsertReqDTO reqDTO) {
        return success(partnerIntegrationService.upsertPartner(reqDTO));
    }

    @Override
    public CommonResult<MesIntegrationAcceptedRespDTO> createWorkOrder(MesWorkOrderCreateReqDTO reqDTO) {
        return success(workOrderIntegrationService.createWorkOrder(reqDTO));
    }

}
