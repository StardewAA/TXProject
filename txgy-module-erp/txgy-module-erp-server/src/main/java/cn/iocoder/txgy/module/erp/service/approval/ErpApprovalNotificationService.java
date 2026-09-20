package cn.iocoder.txgy.module.erp.service.approval;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Collection;
import java.util.Map;

/** ERP 审批站内信发送服务，复用系统现有铃铛消息能力。 */
@Service
@Slf4j
public class ErpApprovalNotificationService {

    private static final String ORDER_SUBMITTED_TEMPLATE = "erp_order_submitted";
    private static final String STOCK_MOVE_TEMPLATE = "erp_stock_move_changed";

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    /**
     * 订单和流程实例都提交成功后，再给发起人发送发布成功通知。
     * 通知失败只记录日志，不能反向破坏已创建的业务订单和流程实例。
     */
    public void sendOrderSubmittedAfterCommit(Long userId, String orderType, String orderNo, Long orderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Runnable sender = () -> TenantUtils.execute(tenantId,
                () -> sendOrderSubmitted(userId, orderType, orderNo, orderId));
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

    public void sendBusinessSubmittedAfterCommit(Long userId, String businessName, String businessNo,
                                                 Long businessId, String detailPath) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Runnable sender = () -> TenantUtils.execute(tenantId,
                () -> sendBusinessSubmitted(userId, businessName, businessNo, businessId, detailPath));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { sender.run(); }
        });
    }

    /** 给调出、调入仓库负责人发送同一条调拨进度或库存变动通知。 */
    public void sendStockMoveAfterCommit(Collection<Long> userIds, String moveNo, Long moveId,
                                         String action, String fromWarehouses, String toWarehouses) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Runnable sender = () -> TenantUtils.execute(tenantId, () -> userIds.stream().distinct().forEach(userId -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("moveNo", moveNo);
                params.put("moveId", moveId);
                params.put("action", action);
                params.put("fromWarehouses", fromWarehouses);
                params.put("toWarehouses", toWarehouses);
                params.put("detailPath", "/erp/stock/move");
                notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                        .setUserId(userId).setTemplateCode(STOCK_MOVE_TEMPLATE).setTemplateParams(params)).checkError();
            } catch (Exception ex) {
                log.error("[sendStockMove][tenantId({}) userId({}) moveNo({}) 发送站内信失败]",
                        tenantId, userId, moveNo, ex);
            }
        }));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { sender.run(); }
        });
    }

    private void sendOrderSubmitted(Long userId, String orderType, String orderNo, Long orderId) {
        sendBusinessSubmitted(userId, "PURCHASE".equals(orderType) ? "采购订单" : "销售订单",
                orderNo, orderId, "/erp/approval");
    }

    private void sendBusinessSubmitted(Long userId, String businessName, String businessNo,
                                       Long businessId, String detailPath) {
        try {
            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("orderType", businessName);
            templateParams.put("orderTypeName", businessName);
            templateParams.put("orderNo", businessNo);
            templateParams.put("orderId", businessId);
            templateParams.put("detailPath", detailPath);
            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(userId)
                    .setTemplateCode(ORDER_SUBMITTED_TEMPLATE)
                    .setTemplateParams(templateParams)).checkError();
        } catch (Exception ex) {
            log.error("[sendBusinessSubmitted][tenantId({}) userId({}) businessNo({}) 发送站内信失败]",
                    TenantContextHolder.getTenantId(), userId, businessNo, ex);
        }
    }
}
