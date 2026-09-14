package cn.iocoder.txgy.module.crm.service.quotation.listener;

import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.txgy.module.crm.enums.ApiConstants;
import cn.iocoder.txgy.module.crm.service.quotation.CrmQuotationService;
import cn.iocoder.txgy.module.crm.service.quotation.CrmQuotationServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报价单审批的结果的监听器实现类
 *
 * @author 防腐保温项目组
 */
@RestController
@Validated
public class CrmQuotationStatusListener extends BpmProcessInstanceStatusEventListener {

    private static final String PREFIX = ApiConstants.PREFIX + "/quotation";

    @Resource
    private CrmQuotationService quotationService;

    @Override
    public String getProcessDefinitionKey() {
        return CrmQuotationServiceImpl.BPM_PROCESS_DEFINITION_KEY;
    }

    @Override
    @PostMapping(PREFIX + "/update-audit-status") // 目的：提供给 bpm-server rpc 调用
    protected void onEvent(@RequestBody BpmProcessInstanceStatusEvent event) {
        quotationService.updateQuotationAuditStatus(Long.parseLong(event.getBusinessKey()), event.getStatus());
    }

}
