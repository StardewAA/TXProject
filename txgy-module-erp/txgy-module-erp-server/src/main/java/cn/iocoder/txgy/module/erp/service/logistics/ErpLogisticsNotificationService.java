package cn.iocoder.txgy.module.erp.service.logistics;

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
import java.util.Map;

/** 物流事件站内信。消息发送在事务提交后执行，失败不影响物流业务数据。 */
@Service
@Slf4j
public class ErpLogisticsNotificationService {

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    public void sendAfterCommit(Long userId, String templateCode, String logisticsTaskNo,
                                String sourceOrderNo, String detailPath) {
        sendAfterCommit(userId, templateCode, logisticsTaskNo, sourceOrderNo, detailPath, Map.of());
    }

    public void sendAfterCommit(Long userId, String templateCode, String logisticsTaskNo,
                                String sourceOrderNo, String detailPath, Map<String, Object> extraParams) {
        if (userId == null) {
            return;
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Runnable sender = () -> TenantUtils.execute(tenantId,
                () -> send(userId, templateCode, logisticsTaskNo, sourceOrderNo, detailPath, extraParams));
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

    private void send(Long userId, String templateCode, String logisticsTaskNo,
                      String sourceOrderNo, String detailPath, Map<String, Object> extraParams) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("logisticsTaskNo", logisticsTaskNo);
            params.put("sourceOrderNo", sourceOrderNo);
            params.put("detailPath", detailPath);
            params.putAll(extraParams);
            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(userId).setTemplateCode(templateCode).setTemplateParams(params)).checkError();
        } catch (Exception ex) {
            log.error("[send][tenantId({}) userId({}) taskNo({}) 发送物流站内信失败]",
                    TenantContextHolder.getTenantId(), userId, logisticsTaskNo, ex);
        }
    }

}
