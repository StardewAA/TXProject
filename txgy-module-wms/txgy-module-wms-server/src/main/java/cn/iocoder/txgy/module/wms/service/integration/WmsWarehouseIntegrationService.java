package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsWarehouseUpsertReqDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.*;

@Service
public class WmsWarehouseIntegrationService {

    @Resource
    private WmsWarehouseMapper warehouseMapper;

    @Transactional(rollbackFor = Exception.class)
    public WmsIntegrationAcceptedRespDTO upsert(WmsWarehouseUpsertReqDTO request) {
        validateType(request.getWarehouseType());
        int status = toStatus(request.getStatus());
        WmsWarehouseDO warehouse = request.getWarehouseCode() == null || request.getWarehouseCode().isBlank()
                ? null : warehouseMapper.selectByCode(request.getWarehouseCode());
        String code;
        long processedVersion;
        if (warehouse == null) {
            if (request.getExpectedVersion() != null) throw exception(WAREHOUSE_VERSION_CONFLICT);
            code = request.getWarehouseCode() == null || request.getWarehouseCode().isBlank()
                    ? generateCode() : request.getWarehouseCode();
            processedVersion = 1L;
            warehouseMapper.insert(fillPublicFields(new WmsWarehouseDO(), request, status)
                    .setCode(code).setDataVersion(processedVersion));
        } else {
            if (request.getExpectedVersion() == null
                    || !request.getExpectedVersion().equals(warehouse.getDataVersion())) {
                throw exception(WAREHOUSE_VERSION_CONFLICT);
            }
            processedVersion = warehouse.getDataVersion() + 1;
            WmsWarehouseDO updateObj = fillPublicFields(new WmsWarehouseDO(), request, status)
                    .setDataVersion(processedVersion);
            if (warehouseMapper.updateByCodeAndVersion(warehouse.getCode(), request.getExpectedVersion(), updateObj) != 1) {
                throw exception(WAREHOUSE_VERSION_CONFLICT);
            }
            code = warehouse.getCode();
        }
        return new WmsIntegrationAcceptedRespDTO().setAccepted(true).setRequestId(request.getRequestId())
                .setTargetOrderNo(code).setBusinessStatus("SYNCED").setProcessedVersion(processedVersion);
    }

    private WmsWarehouseDO fillPublicFields(WmsWarehouseDO warehouse, WmsWarehouseUpsertReqDTO request, int status) {
        return warehouse.setName(request.getName()).setWarehouseType(request.getWarehouseType())
                .setAddress(request.getAddress()).setPrincipal(request.getPrincipal()).setStatus(status)
                .setSort(request.getSort()).setRemark(request.getRemark());
    }

    private void validateType(String type) {
        if (!MATERIAL_RAW.equals(type) && !MATERIAL_FINISHED.equals(type)) throw exception(MATERIAL_TYPE_INVALID);
    }

    private int toStatus(String status) {
        if (STATUS_ENABLED.equals(status)) return CommonStatusEnum.ENABLE.getStatus();
        if (STATUS_DISABLED.equals(status)) return CommonStatusEnum.DISABLE.getStatus();
        throw exception(INTEGRATION_STATUS_INVALID);
    }

    private String generateCode() {
        return "WMS-WH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
