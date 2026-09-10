package cn.iocoder.txgy.module.erp.service.purchase.listener;
import cn.iocoder.txgy.module.bpm.api.event.*;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import cn.iocoder.txgy.module.erp.service.purchase.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
@RestController
public class ErpInquiryStatusListener extends BpmProcessInstanceStatusEventListener {
    @Resource private ErpInquiryService inquiryService;
    @Override protected String getProcessDefinitionKey() { return ErpInquiryServiceImpl.INQUIRY_BPM_KEY; }
    @Override @PostMapping(ApiConstants.PREFIX + "/inquiry/update-audit-status")
    protected void onEvent(@RequestBody BpmProcessInstanceStatusEvent event) { inquiryService.updateInquiryAuditStatus(Long.parseLong(event.getBusinessKey()), event.getStatus()); }
}
