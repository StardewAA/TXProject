package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ERP → MES 主数据同步 Service
 *
 * 与 {@link ErpWmsMasterDataSyncService} 同构：产品 / 客户 / 供应商
 * 创建、更新、删除后按 ERP 权威编码推送到 MES（幂等 upsert）。
 * 事务提交后才发起推送；失败只记出站记录与日志，不影响 ERP 主流程。
 */
@Service
@Slf4j
public class ErpMesMasterDataSyncService {

    @Resource
    private ErpMesIntegrationGateway mesIntegrationGateway;
    @Autowired
    @Qualifier("erpWmsIntegrationRecordService")
    private ErpWmsIntegrationRecordService recordService;

    public void syncProductAfterCommit(ErpProductDO product) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        MesMaterialUpsertReqDTO request = new MesMaterialUpsertReqDTO()
                .setMaterialCode(product.getMaterialCode())
                .setName(product.getName())
                .setSpecification(product.getStandard())
                .setRemark(product.getRemark())
                .setStatus(CommonStatusEnum.isEnable(product.getStatus()) ? "ENABLED" : "DISABLED")
                .setDataVersion(product.getSyncVersion());
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), "ERP_TO_MES", "MATERIAL", request.getMaterialCode(),
                request.getDataVersion(), request);
        runAfterCommit(tenantId, request.getRequestId(),
                () -> mesIntegrationGateway.upsertMaterial(request),
                "product materialCode=" + product.getMaterialCode());
    }

    public void syncCustomerAfterCommit(ErpCustomerDO customer) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        MesPartnerUpsertReqDTO request = new MesPartnerUpsertReqDTO()
                .setPartnerCode(customer.getPartnerCode()).setName(customer.getName())
                .setPartnerType("CUSTOMER")
                .setContact(customer.getContact()).setMobile(customer.getMobile())
                .setTelephone(customer.getTelephone()).setEmail(customer.getEmail())
                .setTaxNo(customer.getTaxNo()).setRemark(customer.getRemark())
                .setStatus(CommonStatusEnum.isEnable(customer.getStatus()) ? "ENABLED" : "DISABLED")
                .setDataVersion(customer.getSyncVersion());
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), "ERP_TO_MES", "PARTNER", request.getPartnerCode(),
                request.getDataVersion(), request);
        runAfterCommit(tenantId, request.getRequestId(),
                () -> mesIntegrationGateway.upsertPartner(request),
                "customer partnerCode=" + customer.getPartnerCode());
    }

    public void syncSupplierAfterCommit(ErpSupplierDO supplier) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        MesPartnerUpsertReqDTO request = new MesPartnerUpsertReqDTO()
                .setPartnerCode(supplier.getPartnerCode()).setName(supplier.getName())
                .setPartnerType("SUPPLIER")
                .setContact(supplier.getContact()).setMobile(supplier.getMobile())
                .setTelephone(supplier.getTelephone()).setEmail(supplier.getEmail())
                .setTaxNo(supplier.getTaxNo()).setRemark(supplier.getRemark())
                .setStatus(CommonStatusEnum.isEnable(supplier.getStatus()) ? "ENABLED" : "DISABLED")
                .setDataVersion(supplier.getSyncVersion());
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), "ERP_TO_MES", "PARTNER", request.getPartnerCode(),
                request.getDataVersion(), request);
        runAfterCommit(tenantId, request.getRequestId(),
                () -> mesIntegrationGateway.upsertPartner(request),
                "supplier partnerCode=" + supplier.getPartnerCode());
    }

    private void runAfterCommit(Long tenantId, String requestId, Supplier<Object> action, String description) {
        Runnable sender = () -> TenantUtils.execute(tenantId, () -> {
            try {
                Object response = action.get();
                recordService.markSuccess(requestId, response);
            } catch (Exception ex) {
                recordService.markFailed(requestId, ex);
                log.error("[syncMesMasterData][{} 推送 MES 失败]", description, ex);
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

