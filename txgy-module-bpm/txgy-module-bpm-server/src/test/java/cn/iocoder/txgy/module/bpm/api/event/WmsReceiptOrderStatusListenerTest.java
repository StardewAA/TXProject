package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WmsReceiptOrderStatusListenerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WmsReceiptOrderStatusListener listener;
    @Mock
    private RestTemplate loadBalancedRestTemplate;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresOtherProcessesAndRunningState() {
        listener.onApplicationEvent(event("wms_shipment_approval", 2));
        listener.onApplicationEvent(event("wms_receipt_approval", 1));
        verifyNoInteractions(loadBalancedRestTemplate);
    }

    @Test
    void forwardsTenantAndRpcIdentity() {
        login();
        when(loadBalancedRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"code\":0,\"data\":true}"));
        listener.onApplicationEvent(event("wms_receipt_approval", 2));
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(eq("http://wms-server/rpc-api/wms/receipt-order/update-approval-status"),
                eq(HttpMethod.POST), request.capture(), eq(String.class));
        assertEquals("7", request.getValue().getHeaders().getFirst("tenant-id"));
        assertNotNull(request.getValue().getHeaders().getFirst(SecurityFrameworkUtils.LOGIN_USER_HEADER));
        assertEquals("process-42", ((BpmProcessInstanceStatusEvent) request.getValue().getBody()).getId());
    }

    @Test
    void propagatesBusinessFailureForApprovalRollback() {
        login();
        when(loadBalancedRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"code\":1001,\"msg\":\"库存执行失败\"}"));
        assertThrows(ServiceException.class, () -> listener.onApplicationEvent(event("wms_receipt_approval", 2)));
    }

    @Test
    void missingIdentityDoesNotSendAnonymousCallback() {
        TenantContextHolder.setTenantId(7L);
        assertThrows(IllegalArgumentException.class, () -> listener.onApplicationEvent(event("wms_receipt_approval", 2)));
        verifyNoInteractions(loadBalancedRestTemplate);
    }

    private void login() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L).setUserType(2).setTenantId(7L), new MockHttpServletRequest());
    }

    private BpmProcessInstanceStatusEvent event(String key, int status) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent(this);
        event.setProcessDefinitionKey(key);
        event.setId("process-42");
        event.setBusinessKey("42");
        event.setStatus(status);
        return event;
    }
}
