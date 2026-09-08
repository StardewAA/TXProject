package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.service.product.ErpProductUnitService;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsPartnerUpsertReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.*;

@Service
@Slf4j
public class ErpWmsMasterDataSyncService {

    @Resource
    private ErpWmsIntegrationGateway integrationGateway;
    @Resource
    private ErpProductUnitService productUnitService;
    @Resource
    private ErpWmsIntegrationRecordService recordService;

    public void syncProductAfterCommit(ErpProductDO product) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ErpProductUnitDO unit = productUnitService.getProductUnit(product.getUnitId());
        if (unit == null) {
            throw new IllegalStateException("产品单位不存在，无法同步 WMS");
        }
        WmsMaterialUpsertReqDTO request = new WmsMaterialUpsertReqDTO()
                .setMaterialCode(product.getMaterialCode())
                .setName(product.getName())
                .setMaterialType(product.getMaterialType())
                .setSpecification(product.getStandard())
                .setUnitCode(unit.getCode() != null ? unit.getCode() : "UNIT-" + unit.getId())
                .setUnitName(unit.getName())
                .setBarCode(product.getBarCode())
                .setQualityRequired(Boolean.TRUE.equals(product.getQualityRequired()))
                .setWeight(product.getWeight())
                .setStatus(CommonStatusEnum.isEnable(product.getStatus()) ? STATUS_ENABLED : STATUS_DISABLED)
                .setDataVersion(product.getSyncVersion());
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule(SOURCE_ERP);
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), "MATERIAL", request.getMaterialCode(),
                request.getDataVersion(), request);
        runAfterCommit(tenantId, request.getRequestId(), () -> integrationGateway.upsertMaterial(request),
                "materialCode=" + product.getMaterialCode());
    }

    public void syncCustomerAfterCommit(ErpCustomerDO customer) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WmsPartnerUpsertReqDTO request = new WmsPartnerUpsertReqDTO()
                .setPartnerCode(customer.getPartnerCode()).setName(customer.getName()).setPartnerType("CUSTOMER")
                .setContact(customer.getContact()).setMobile(customer.getMobile()).setTelephone(customer.getTelephone())
                .setEmail(customer.getEmail()).setTaxNo(customer.getTaxNo()).setBankName(customer.getBankName())
                .setBankAccount(customer.getBankAccount()).setRemark(customer.getRemark())
                .setStatus(CommonStatusEnum.isEnable(customer.getStatus()) ? STATUS_ENABLED : STATUS_DISABLED)
                .setDataVersion(customer.getSyncVersion());
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule(SOURCE_ERP);
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), "PARTNER", request.getPartnerCode(),
                request.getDataVersion(), request);
        runAfterCommit(tenantId, request.getRequestId(), () -> integrationGateway.upsertPartner(request),
                "customer partnerCode=" + customer.getPartnerCode());
    }

    public void syncSupplierAfterCommit(ErpSupplierDO supplier) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WmsPartnerUpsertReqDTO request = new WmsPartnerUpsertReqDTO()
                .setPartnerCode(supplier.getPartnerCode()).setName(supplier.getName()).setPartnerType("SUPPLIER")
                .setContact(supplier.getContact()).setMobile(supplier.getMobile()).setTelephone(supplier.getTelephone())
                .setEmail(supplier.getEmail()).setTaxNo(supplier.getTaxNo()).setBankName(supplier.getBankName())
                .setBankAccount(supplier.getBankAccount()).setRemark(supplier.getRemark())
                .setStatus(CommonStatusEnum.isEnable(supplier.getStatus()) ? STATUS_ENABLED : STATUS_DISABLED)
                .setDataVersion(supplier.getSyncVersion());
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule(SOURCE_ERP);
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), "PARTNER", request.getPartnerCode(),
                request.getDataVersion(), request);
        runAfterCommit(tenantId, request.getRequestId(), () -> integrationGateway.upsertPartner(request),
                "supplier partnerCode=" + supplier.getPartnerCode());
    }

    private void runAfterCommit(Long tenantId, String requestId, Supplier<Object> action, String description) {
        Runnable sender = () -> TenantUtils.execute(tenantId, () -> {
            try {
                Object response = action.get();
                recordService.markSuccess(requestId, response);
            } catch (Exception ex) {
                recordService.markFailed(requestId, ex);
                log.error("[syncMasterData][{} 同步 WMS 失败]", description, ex);
            }
        });
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.run();
            }
        });
    }

}
