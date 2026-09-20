package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderMapper;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.WMS_CALLBACK_ORDER_NOT_EXISTS;

/**
 * ERP → MES 销售订单派生集成 Service
 *
 * 销售订单审批通过后，按订单行项向 MES 推送生产工单创建请求：
 * 工单宿主与生命周期归 MES（orderSourceType=ERP，orderSourceCode=销售订单号），
 * ERP 侧仅负责按行项发出请求并记录出站台账，建单结果不影响 ERP 主流程。
 *
 * 与 {@link ErpSaleWmsIntegrationService} 同构：事务提交后推送，失败仅记台账与日志。
 */
@Service
@Slf4j
public class ErpSaleMesIntegrationService {

    private static final String SOURCE_MODULE_ERP = "ERP";

    @Resource
    private ErpSaleOrderMapper saleOrderMapper;
    @Resource
    private ErpSaleOrderItemMapper saleOrderItemMapper;
    @Resource
    private ErpProductService productService;
    @Resource
    private ErpCustomerService customerService;
    @Resource
    private ErpMesIntegrationGateway mesIntegrationGateway;
    @Autowired
    @Qualifier("erpWmsIntegrationRecordService")
    private ErpWmsIntegrationRecordService recordService;

    /**
     * 销售订单审批通过后，按行项推送 MES 建单（事务提交后执行）
     *
     * @param saleOrderId 销售订单编号
     */
    public void pushWorkOrdersAfterCommit(Long saleOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<MesWorkOrderCreateReqDTO> requests = buildWorkOrderRequests(saleOrderId, tenantId);
        if (requests.isEmpty()) {
            log.info("[pushWorkOrders][订单({}) 无订单行，跳过 MES 工单推送]", saleOrderId);
            return;
        }
        for (MesWorkOrderCreateReqDTO request : requests) {
            recordService.createOutbound(request.getRequestId(), "ERP_TO_MES", "WORK_ORDER",
                    request.getOrderSourceCode() + "/" + request.getItemCode(), request.getDataVersion(), request);
        }
        Runnable sender = () -> TenantUtils.execute(tenantId, () -> {
            for (MesWorkOrderCreateReqDTO request : requests) {
                sendWorkOrder(request);
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

    /** 单条推送：成功/失败均落台账，异常不外抛（不阻断其余行项） */
    private void sendWorkOrder(MesWorkOrderCreateReqDTO request) {
        String description = "orderSourceCode=" + request.getOrderSourceCode() + ",itemCode=" + request.getItemCode();
        try {
            Object response = mesIntegrationGateway.createWorkOrder(request);
            recordService.markSuccess(request.getRequestId(), response);
            log.info("[sendWorkOrder][订单({}) 行项({}) 推送 MES 建单成功]", request.getOrderSourceCode(),
                    request.getItemCode());
        } catch (Exception ex) {
            recordService.markFailed(request.getRequestId(), ex);
            log.error("[sendWorkOrder][{} 推送 MES 建单失败]", description, ex);
        }
    }

    /** 按订单行项组装 MES 工单创建请求（物料编码取 ERP 权威 material_code，与 MES 主数据对齐） */
    private List<MesWorkOrderCreateReqDTO> buildWorkOrderRequests(Long saleOrderId, Long tenantId) {
        ErpSaleOrderDO order = saleOrderMapper.selectById(saleOrderId);
        if (order == null) {
            throw exception(WMS_CALLBACK_ORDER_NOT_EXISTS);
        }
        List<ErpSaleOrderItemDO> orderItems = new ArrayList<>(saleOrderItemMapper.selectListByOrderId(saleOrderId));
        orderItems.sort(Comparator.comparing(ErpSaleOrderItemDO::getId));
        if (orderItems.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, ErpProductDO> productMap = convertMap(
                productService.validProductList(convertSet(orderItems, ErpSaleOrderItemDO::getProductId)),
                ErpProductDO::getId);
        String customerCode = resolveCustomerCode(order.getCustomerId());
        LocalDateTime requestDate = order.getOrderTime() != null ? order.getOrderTime() : LocalDateTime.now();
        List<MesWorkOrderCreateReqDTO> requests = new ArrayList<>(orderItems.size());
        for (ErpSaleOrderItemDO orderItem : orderItems) {
            ErpProductDO product = productMap.get(orderItem.getProductId());
            if (product == null) {
                log.warn("[buildWorkOrderRequests][订单({}) 行项({}) 产品({}) 不存在，跳过",
                        order.getNo(), orderItem.getId(), orderItem.getProductId());
                continue;
            }
            MesWorkOrderCreateReqDTO request = new MesWorkOrderCreateReqDTO()
                    .setOrderSourceCode(order.getNo())
                    .setItemCode(product.getMaterialCode())
                    .setProductName(product.getName())
                    .setQuantity(orderItem.getCount())
                    .setRequestDate(requestDate)
                    .setCustomerCode(customerCode)
                    .setRemark("由 ERP 销售订单 " + order.getNo() + " 自动下发");
            request.setRequestId(UUID.randomUUID().toString());
            request.setTenantId(tenantId);
            request.setSourceModule(SOURCE_MODULE_ERP);
            request.setOccurredAt(LocalDateTime.now());
            request.setDataVersion(orderItem.getId());
            requests.add(request);
        }
        return requests;
    }

    private String resolveCustomerCode(Long customerId) {
        if (customerId == null) {
            return null;
        }
        ErpCustomerDO customer = customerService.getCustomer(customerId);
        return customer == null ? null : customer.getPartnerCode();
    }

}
