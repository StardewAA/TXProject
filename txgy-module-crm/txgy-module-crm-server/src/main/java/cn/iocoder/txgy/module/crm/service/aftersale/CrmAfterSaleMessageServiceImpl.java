package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.enums.MessageTemplateConstants;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 售后站内信消息 Service 实现类
 *
 * 站内信模板不存在/发送失败不影响主流程（仅记录日志，防止邮件/站内信问题回滚业务事务）
 *
 * @author txgycloud
 */
@Service
@Validated
@Slf4j
public class CrmAfterSaleMessageServiceImpl implements CrmAfterSaleMessageService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;
    @Resource
    private CrmCustomerService customerService;

    @Override
    public void sendVisitTaskRemind(Long userId, CrmAfterSaleVisitTaskDO task) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("customerName", getCustomerName(task.getCustomerId()));
        params.put("taskDate", task.getTaskDate() != null ? task.getTaskDate().format(DATE_FORMATTER) : "");
        send(userId, MessageTemplateConstants.AFTER_SALE_VISIT_TASK_REMIND, params);
    }

    @Override
    public void sendWarrantyExpireRemind(Long userId, CrmAfterSaleWarrantyDO warranty) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("customerName", getCustomerName(warranty.getCustomerId()));
        params.put("projectName", warranty.getProjectName() != null ? warranty.getProjectName() : "");
        params.put("endDate", warranty.getWarrantyEndDate() != null
                ? warranty.getWarrantyEndDate().format(DATE_FORMATTER) : "");
        send(userId, MessageTemplateConstants.AFTER_SALE_WARRANTY_EXPIRE_REMIND, params);
    }

    @Override
    public void sendTicketAssignNotify(Long assigneeUserId, CrmAfterSaleTicketDO ticket) {
        if (assigneeUserId == null || ticket == null) {
            return;
        }
        Map<String, Object> params = new HashMap<>(4);
        params.put("ticketNo", ticket.getNo() != null ? ticket.getNo() : "");
        params.put("ticketTitle", ticket.getTitle() != null ? ticket.getTitle() : "");
        send(assigneeUserId, MessageTemplateConstants.AFTER_SALE_TICKET_ASSIGN, params);
    }

    private String getCustomerName(Long customerId) {
        if (customerId == null) {
            return "";
        }
        CrmCustomerDO customer = customerService.getCustomer(customerId);
        return customer != null && customer.getName() != null ? customer.getName() : "";
    }

    private void send(Long userId, String templateCode, Map<String, Object> params) {
        if (userId == null) {
            return;
        }
        try {
            CommonResult<Long> result = notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(userId).setTemplateCode(templateCode).setTemplateParams(params));
            if (result.isError()) {
                log.warn("[send][站内信发送失败 模板({}) 用户({}) 原因({})]", templateCode, userId, result.getMsg());
            }
        } catch (Exception ex) {
            log.error("[send][站内信发送异常 模板({}) 用户({})]", templateCode, userId, ex);
        }
    }

}
