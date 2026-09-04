package cn.iocoder.txgy.module.bpm.service.message;

import cn.iocoder.txgy.framework.web.config.WebProperties;
import cn.iocoder.txgy.module.bpm.convert.message.BpmMessageConvert;
import cn.iocoder.txgy.module.bpm.enums.message.BpmMessageEnum;
import cn.iocoder.txgy.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.txgy.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.txgy.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.txgy.module.bpm.service.message.dto.BpmMessageSendWhenTaskCompletedReqDTO;
import cn.iocoder.txgy.module.bpm.service.message.dto.BpmMessageSendWhenTaskTimeoutReqDTO;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.txgy.module.system.api.sms.SmsSendApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * BPM 消息 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class BpmMessageServiceImpl implements BpmMessageService {

    private static final String ERP_PURCHASE_PROCESS_KEY = "erp-purchase-order-approval";
    private static final String ERP_SALE_PROCESS_KEY = "erp-sale-order-approval";
    private static final String ERP_TASK_ASSIGNED_TEMPLATE = "erp_approval_task_assigned";
    private static final String ERP_TASK_COMPLETED_TEMPLATE = "erp_approval_task_completed";
    private static final String ERP_FINISHED_TEMPLATE = "erp_approval_finished";
    private static final String ERP_REJECTED_TEMPLATE = "erp_approval_rejected";

    @Resource
    private SmsSendApi smsSendApi;

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @Resource
    private WebProperties webProperties;

    @Override
    public void sendMessageWhenProcessInstanceApprove(BpmMessageSendWhenProcessInstanceApproveReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        putErpOrderParams(templateParams, reqDTO.getOrderType(), reqDTO.getOrderNo());
        sendErpNotify(reqDTO.getProcessDefinitionKey(), reqDTO.getStartUserId(),
                ERP_FINISHED_TEMPLATE, templateParams);
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getStartUserId(),
                BpmMessageEnum.PROCESS_INSTANCE_APPROVE.getSmsTemplateCode(), templateParams)).checkError();
    }

    @Override
    public void sendMessageWhenProcessInstanceReject(BpmMessageSendWhenProcessInstanceRejectReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("reason", reqDTO.getReason());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        putErpOrderParams(templateParams, reqDTO.getOrderType(), reqDTO.getOrderNo());
        sendErpNotify(reqDTO.getProcessDefinitionKey(), reqDTO.getStartUserId(),
                ERP_REJECTED_TEMPLATE, templateParams);
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getStartUserId(),
                BpmMessageEnum.PROCESS_INSTANCE_REJECT.getSmsTemplateCode(), templateParams)).checkError();
    }

    @Override
    public void sendMessageWhenTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("startUserNickname", reqDTO.getStartUserNickname());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        putErpOrderParams(templateParams, reqDTO.getOrderType(), reqDTO.getOrderNo());
        // Flowable 已将候选角色/用户解析为最终 assignee，只给该审批账号发送，禁止广播。
        sendErpNotify(reqDTO.getProcessDefinitionKey(), reqDTO.getAssigneeUserId(),
                ERP_TASK_ASSIGNED_TEMPLATE, templateParams);
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getAssigneeUserId(),
                BpmMessageEnum.TASK_ASSIGNED.getSmsTemplateCode(), templateParams)).checkError();
    }

    @Override
    public void sendMessageWhenTaskCompleted(BpmMessageSendWhenTaskCompletedReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("result", reqDTO.getResult());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        putErpOrderParams(templateParams, reqDTO.getOrderType(), reqDTO.getOrderNo());
        sendErpNotify(reqDTO.getProcessDefinitionKey(), reqDTO.getAssigneeUserId(),
                ERP_TASK_COMPLETED_TEMPLATE, templateParams);
    }

    @Override
    public void sendMessageWhenTaskTimeout(BpmMessageSendWhenTaskTimeoutReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getAssigneeUserId(),
                BpmMessageEnum.TASK_TIMEOUT.getSmsTemplateCode(), templateParams)).checkError();
    }

    private String getProcessInstanceDetailUrl(String taskId) {
        return webProperties.getAdminUi().getUrl() + "/bpm/process-instance/detail?id=" + taskId;
    }

    private void sendErpNotify(String processDefinitionKey, Long userId, String templateCode,
                               Map<String, Object> templateParams) {
        if (!isErpOrderProcess(processDefinitionKey)) {
            return;
        }
        templateParams.put("detailPath", "/erp/approval");
        try {
            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(userId).setTemplateCode(templateCode).setTemplateParams(templateParams)).checkError();
        } catch (Exception ex) {
            // 通知属于附属能力，模板漏部署或消息服务短暂不可用时，不能回滚正常的订单审批。
            log.error("[sendErpNotify][ERP 审批站内信发送失败，processDefinitionKey={}, userId={}, templateCode={}]",
                    processDefinitionKey, userId, templateCode, ex);
        }
    }

    private static boolean isErpOrderProcess(String processDefinitionKey) {
        return ERP_PURCHASE_PROCESS_KEY.equals(processDefinitionKey)
                || ERP_SALE_PROCESS_KEY.equals(processDefinitionKey);
    }

    private static void putErpOrderParams(Map<String, Object> templateParams, String orderType, String orderNo) {
        templateParams.put("orderType", orderType);
        templateParams.put("orderTypeName", "PURCHASE".equals(orderType) ? "采购订单" : "销售订单");
        templateParams.put("orderNo", orderNo);
    }

}
