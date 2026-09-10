package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.*;

@Service
public class WmsMaterialIntegrationService {

    @Resource private WmsItemMapper itemMapper;
    @Resource private WmsItemSkuMapper skuMapper;

    @Transactional(rollbackFor = Exception.class)
    public WmsIntegrationAcceptedRespDTO upsert(WmsMaterialUpsertReqDTO request) {
        validateRequest(request);
        WmsItemDO item = itemMapper.selectByCode(request.getMaterialCode());
        if (item == null) {
            item = WmsItemDO.builder().code(request.getMaterialCode()).name(request.getName())
                    .materialType(request.getMaterialType()).qualityRequired(request.getQualityRequired())
                    .sourceVersion(request.getDataVersion()).status(toStatus(request.getStatus()))
                    .unit(request.getUnitName()).categoryId(0L).build();
            itemMapper.insert(item);
            skuMapper.insert(WmsItemSkuDO.builder().itemId(item.getId()).code(defaultSkuCode(request))
                    .name(defaultSkuName(request)).barCode(request.getBarCode()).build());
        } else {
            validateVersion(item, request);
            WmsItemSkuDO defaultSku = findDefaultSku(item.getId(), defaultSkuCode(request));
            itemMapper.updateById(new WmsItemDO().setId(item.getId()).setName(request.getName())
                    .setMaterialType(request.getMaterialType()).setQualityRequired(request.getQualityRequired())
                    .setSourceVersion(request.getDataVersion()).setStatus(toStatus(request.getStatus()))
                    .setUnit(request.getUnitName()));
            skuMapper.updateById(new WmsItemSkuDO().setId(defaultSku.getId())
                    .setName(defaultSkuName(request)).setBarCode(request.getBarCode()));
        }
        return new WmsIntegrationAcceptedRespDTO().setAccepted(true).setRequestId(request.getRequestId())
                .setTargetOrderNo(request.getMaterialCode()).setBusinessStatus("SYNCED")
                .setProcessedVersion(request.getDataVersion());
    }

    private void validateRequest(WmsMaterialUpsertReqDTO request) {
        if (!MATERIAL_RAW.equals(request.getMaterialType()) && !MATERIAL_FINISHED.equals(request.getMaterialType())) {
            throw exception(MATERIAL_TYPE_INVALID);
        }
        toStatus(request.getStatus());
    }

    private int toStatus(String status) {
        if (STATUS_ENABLED.equals(status)) return CommonStatusEnum.ENABLE.getStatus();
        if (STATUS_DISABLED.equals(status)) return CommonStatusEnum.DISABLE.getStatus();
        throw exception(SYNC_VERSION_STALE);
    }

    private void validateVersion(WmsItemDO item, WmsMaterialUpsertReqDTO request) {
        long current = item.getSourceVersion() == null ? 0 : item.getSourceVersion();
        if (request.getDataVersion() < current) {
            throw exception(SYNC_VERSION_STALE);
        }
        if (request.getDataVersion() == current && !(Objects.equals(item.getName(), request.getName())
                && Objects.equals(item.getMaterialType(), request.getMaterialType())
                && Objects.equals(item.getQualityRequired(), request.getQualityRequired())
                && Objects.equals(item.getUnit(), request.getUnitName())
                && Objects.equals(item.getStatus(), toStatus(request.getStatus())))) {
            throw exception(SYNC_VERSION_STALE);
        }
    }

    private WmsItemSkuDO findDefaultSku(Long itemId, String defaultCode) {
        List<WmsItemSkuDO> skus = skuMapper.selectListByItemId(itemId);
        List<WmsItemSkuDO> matches = skus.stream().filter(sku -> defaultCode.equals(sku.getCode())).toList();
        if (matches.size() != 1) {
            throw exception(INTEGRATION_MASTER_DATA_MAPPING_CONFLICT);
        }
        return matches.getFirst();
    }

    private String defaultSkuCode(WmsMaterialUpsertReqDTO request) {
        return request.getMaterialCode() + "-DEFAULT";
    }

    private String defaultSkuName(WmsMaterialUpsertReqDTO request) {
        return request.getSpecification() == null || request.getSpecification().isBlank()
                ? request.getName() : request.getSpecification();
    }
}
