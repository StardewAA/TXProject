package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.service.tenant.MallAuthPolicy;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppMallAuthEntryControllerTest {
    @Test void imageDiscoveryIsIndependentOfSmsAndDoesNotEnableTenantSelection() {
        var sms = mock(MallAuthPolicy.class);
        var images = mock(cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy.class);
        var controller = new AppMallAuthEntryController(new MallTenantAuthProperties(), sms, 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "pictureEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "accountPolicy", images);
        when(images.resolve(1L, false)).thenReturn(1L); when(images.resolve(1L, true)).thenReturn(1L);
        var entry = controller.config().getCheckedData();
        assertEquals("legacy", entry.protocol()); assertEquals("image-v1", entry.registrationMethod());
        assertTrue(entry.loginNameEnabled()); assertTrue(entry.registrationAvailable()); assertFalse(entry.tenantSelection());
        verifyNoInteractions(sms);
    }
    @Test void imageDiscoveryFailsClosedWithoutFallingBackToSms() {
        var images = mock(cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy.class);
        var controller = new AppMallAuthEntryController(new MallTenantAuthProperties(), mock(MallAuthPolicy.class), 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "pictureEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "accountPolicy", images);
        when(images.resolve(1L, false)).thenThrow(new IllegalStateException());
        var entry = controller.config().getCheckedData();
        assertFalse(entry.available()); assertFalse(entry.registrationAvailable()); assertEquals("image-v1", entry.registrationMethod());
    }
    @Test void foreignTargetCannotFallBackToLegacyWhenDisabled() {
        var policy = mock(MallAuthPolicy.class);
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties(), policy, 1L).config(9001L).getCheckedData();
        assertEquals("challenge-v1", data.protocol()); assertEquals(9001L, data.tenantId());
        assertFalse(data.available()); assertFalse(data.registrationAvailable()); assertFalse(data.tenantSelection());
        verifyNoInteractions(policy);
    }
    @Test void explicitLegacyPreservesExistingEntry() {
        var controller = new AppMallAuthEntryController(new MallTenantAuthProperties(), mock(MallAuthPolicy.class), 1L);
        assertEquals(controller.config().getCheckedData(), controller.config(1L).getCheckedData());
    }
    @Test void foreignTargetChecksItsOwnLoginAndRegistrationPolicy() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(9001L, false)).thenReturn(9001L);
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L).config(9001L).getCheckedData();
        assertEquals(9001L, data.tenantId()); assertTrue(data.available()); assertTrue(data.registrationAvailable());
        assertFalse(data.tenantSelection()); verify(policy, never()).resolve(1L, false);
    }
    @Test void removedOrConflictingTargetNeverUsesLegacyPolicy() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(9001L, false)).thenThrow(exception(MALL_TENANT_UNAVAILABLE));
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L).config(9001L).getCheckedData();
        assertFalse(data.available()); assertFalse(data.registrationAvailable());
        verify(policy, never()).resolve(9001L, true); verify(policy, never()).resolve(1L, false);
    }
    @Test void foreignLoginCanRemainOpenWhileRegistrationCloses() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(9001L, false)).thenReturn(9001L);
        when(policy.resolve(9001L, true)).thenThrow(exception(MALL_TENANT_UNAVAILABLE));
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L).config(9001L).getCheckedData();
        assertTrue(data.available()); assertFalse(data.registrationAvailable());
    }
    @Test void invalidTargetsAreRejectedBeforePolicy() {
        var policy = mock(MallAuthPolicy.class);
        var controller = new AppMallAuthEntryController(new MallTenantAuthProperties(), policy, 1L);
        assertThrows(RuntimeException.class, () -> controller.config(0L));
        assertThrows(RuntimeException.class, () -> controller.config(-1L)); verifyNoInteractions(policy);
    }
    @Test void wrongResolvedTargetCannotAdvertiseAvailability() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(9001L, false)).thenReturn(1L);
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L).config(9001L).getCheckedData();
        assertFalse(data.available()); assertFalse(data.registrationAvailable());
    }
    @Test void unexpectedInfrastructureFailureIsNotAnAvailableOrLegacyEntry() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(9001L, false)).thenThrow(new IllegalStateException("unavailable"));
        var controller = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L);
        assertThrows(IllegalStateException.class, () -> controller.config(9001L));
    }
    @Test void disabledKeepsLegacyWithoutAccessingTenantRpc() {
        var policy = mock(MallAuthPolicy.class);
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties(), policy, 1L).config().getCheckedData();
        assertEquals("legacy", data.protocol()); assertTrue(data.available()); assertTrue(data.registrationAvailable());
        assertFalse(data.tenantSelection()); verifyNoInteractions(policy);
    }
    @Test void enabledButInvalidCannotAdvertiseLegacyFallback() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(1L, false)).thenThrow(exception(MALL_TENANT_UNAVAILABLE));
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L).config().getCheckedData();
        assertEquals("challenge-v1", data.protocol()); assertFalse(data.available()); assertFalse(data.registrationAvailable());
        verify(policy, never()).resolve(1L, true);
    }
    @Test void loginDoesNotImplicitlyEnableRegistration() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(1L, false)).thenReturn(1L);
        when(policy.resolve(1L, true)).thenThrow(exception(MALL_TENANT_UNAVAILABLE));
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L).config().getCheckedData();
        assertTrue(data.available()); assertFalse(data.registrationAvailable()); assertFalse(data.tenantSelection());
    }
    @Test void validChallengeEntryStillDoesNotReleaseCrossTenantSelection() {
        var policy = mock(MallAuthPolicy.class);
        when(policy.resolve(1L, false)).thenReturn(1L); when(policy.resolve(1L, true)).thenReturn(1L);
        var data = new AppMallAuthEntryController(new MallTenantAuthProperties().setEnabled(true), policy, 1L).config().getCheckedData();
        assertEquals("challenge-v1", data.protocol()); assertEquals(1L, data.tenantId());
        assertTrue(data.registrationAvailable()); assertFalse(data.tenantSelection());
    }
}
