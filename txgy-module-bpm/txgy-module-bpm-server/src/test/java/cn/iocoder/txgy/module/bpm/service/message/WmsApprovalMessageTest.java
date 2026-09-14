package cn.iocoder.txgy.module.bpm.service.message;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.txgy.framework.web.config.WebProperties;
import cn.iocoder.txgy.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.txgy.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.sms.SmsSendApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WmsApprovalMessageTest extends BaseMockitoUnitTest {
    @InjectMocks private BpmMessageServiceImpl service;
    @Mock private SmsSendApi sms;
    @Mock private NotifyMessageSendApi notify;
    @Mock private WebProperties web;

    @BeforeEach void setup() {
        WebProperties.Ui ui = new WebProperties.Ui();
        ui.setUrl("http://localhost:5173/#");
        when(web.getAdminUi()).thenReturn(ui);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wms_receipt_approval", "wms_shipment_approval"})
    void smsBusinessFailureDoesNotPreventWmsReject(String key) {
        when(sms.sendSingleSmsToAdmin(any())).thenReturn(CommonResult.error(500, "sms unavailable"));
        assertDoesNotThrow(() -> service.sendMessageWhenProcessInstanceReject(reject(key)));
        verify(sms).sendSingleSmsToAdmin(any());
        verifyNoInteractions(notify);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wms_receipt_approval", "wms_shipment_approval"})
    void smsTransportFailureDoesNotPreventWmsApprove(String key) {
        when(sms.sendSingleSmsToAdmin(any())).thenThrow(new IllegalStateException("sms offline"));
        BpmMessageSendWhenProcessInstanceApproveReqDTO request = new BpmMessageSendWhenProcessInstanceApproveReqDTO();
        request.setProcessDefinitionKey(key);
        request.setProcessInstanceId("test-process");
        request.setProcessInstanceName("WMS test");
        request.setStartUserId(1L);
        assertDoesNotThrow(() -> service.sendMessageWhenProcessInstanceApprove(request));
        verify(sms).sendSingleSmsToAdmin(any());
        verifyNoInteractions(notify);
    }

    @ParameterizedTest
    @ValueSource(strings = {"oa_leave", "wms_transfer_approval"})
    void otherProcessNotificationSemanticsStayUnchanged(String key) {
        when(sms.sendSingleSmsToAdmin(any())).thenReturn(CommonResult.error(500, "sms unavailable"));
        assertThrows(ServiceException.class, () -> service.sendMessageWhenProcessInstanceReject(reject(key)));
    }

    private BpmMessageSendWhenProcessInstanceRejectReqDTO reject(String key) {
        BpmMessageSendWhenProcessInstanceRejectReqDTO request = new BpmMessageSendWhenProcessInstanceRejectReqDTO();
        request.setProcessDefinitionKey(key);
        request.setProcessInstanceId("test-process");
        request.setProcessInstanceName("WMS test");
        request.setStartUserId(1L);
        request.setReason("warehouse rejected");
        return request;
    }
}
