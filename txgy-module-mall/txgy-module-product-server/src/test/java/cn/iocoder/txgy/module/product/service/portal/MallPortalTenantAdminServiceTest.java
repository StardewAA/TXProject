package cn.iocoder.txgy.module.product.service.portal;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantPageReqVO;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantUpdateReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.portal.MallPortalTenantDO;
import cn.iocoder.txgy.module.product.dal.mysql.portal.MallPortalTenantAdminMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MallPortalTenantAdminServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-21T04:00:00Z"), ZoneOffset.UTC);

    private MallPortalTenantAdminMapper mapper;
    private MallPortalTenantAdminService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        mapper = mock(MallPortalTenantAdminMapper.class);
        service = new MallPortalTenantAdminService(mapper, CLOCK);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void pageAndGetOnlyReadPortalDirectory() {
        MallPortalTenantPageReqVO request = new MallPortalTenantPageReqVO();
        PageResult<MallPortalTenantDO> page = new PageResult<>(List.of(portal(11L, 2009L)), 1L);
        when(mapper.selectPage(request)).thenReturn(page);
        when(mapper.selectById(11L)).thenReturn(page.getList().getFirst());

        assertSame(page, service.getPage(request));
        assertSame(page.getList().getFirst(), service.get(11L));
    }

    @Test
    void nonPlatformTenantSuperAdminCannotReadOrMutateGlobalDirectory() {
        TenantContextHolder.setTenantId(2009L);

        ServiceException pageError = assertThrows(ServiceException.class,
                () -> service.getPage(new MallPortalTenantPageReqVO()));
        ServiceException detailError = assertThrows(ServiceException.class,
                () -> service.get(11L));
        ServiceException updateError = assertThrows(ServiceException.class,
                () -> service.update(updateRequest(11L, "kedun", 1)));

        assertEquals(403, pageError.getCode());
        assertEquals(403, detailError.getCode());
        assertEquals(403, updateError.getCode());
        verify(mapper, never()).selectPage(any(MallPortalTenantPageReqVO.class));
        verify(mapper, never()).selectById(anyLong());
        verify(mapper, never()).selectPortalForUpdate(anyLong());
        verify(mapper, never()).updatePortalConfig(any(), anyString());
    }

    @Test
    void updateNormalizesSlugSyncsSystemNameAndOnlyWritesPortalMetadata() {
        MallPortalTenantDO existing = portal(11L, 2009L);
        when(mapper.selectPortalForUpdate(11L)).thenReturn(existing);
        when(mapper.selectSystemTenant(2009L)).thenReturn(systemTenant(
                2009L, "  科盾防护科技股份有限公司  ", 0,
                LocalDateTime.of(2027, 1, 1, 0, 0), false));
        when(mapper.updatePortalConfig(any(), anyString())).thenReturn(1);
        MallPortalTenantUpdateReqVO request = updateRequest(11L, "  KeDun-Mall  ", 0);
        request.setTenantLogo("  https://example.com/logo.png  ");
        request.setMainCategories("  防腐涂料、保温材料  ");
        request.setSort(20);

        service.update(request);

        ArgumentCaptor<MallPortalTenantDO> captor = ArgumentCaptor.forClass(MallPortalTenantDO.class);
        verify(mapper).updatePortalConfig(captor.capture(), anyString());
        MallPortalTenantDO update = captor.getValue();
        assertEquals(11L, update.getId());
        assertEquals(2009L, update.getTenantId());
        assertEquals("kedun-mall", update.getTenantSlug());
        assertEquals("科盾防护科技股份有限公司", update.getTenantName());
        assertEquals("https://example.com/logo.png", update.getTenantLogo());
        assertEquals("防腐涂料、保温材料", update.getMainCategories());
        assertEquals(20, update.getSort());
    }

    @Test
    void openingRejectsLegacyPublicTenant() {
        when(mapper.selectPortalForUpdate(11L)).thenReturn(portal(11L, 3000L));
        when(mapper.selectSystemTenant(3000L)).thenReturn(systemTenant(
                3000L, "历史公共租户", 0, null, false));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.update(updateRequest(11L, "legacy-public", 0)));

        assertEquals(400, error.getCode());
        verify(mapper, never()).updatePortalConfig(any(), anyString());
    }

    @Test
    void openingRejectsDisabledExpiredDeletedOrMissingSystemTenant() {
        MallPortalTenantDO existing = portal(11L, 2009L);
        when(mapper.selectPortalForUpdate(11L)).thenReturn(existing);
        MallPortalTenantUpdateReqVO request = updateRequest(11L, "kedun", 0);

        when(mapper.selectSystemTenant(2009L)).thenReturn(systemTenant(
                2009L, "科盾", 1, null, false));
        assertEquals(400, assertThrows(ServiceException.class, () -> service.update(request)).getCode());

        when(mapper.selectSystemTenant(2009L)).thenReturn(systemTenant(
                2009L, "科盾", 0, LocalDateTime.of(2026, 9, 21, 3, 59), false));
        assertEquals(400, assertThrows(ServiceException.class, () -> service.update(request)).getCode());

        when(mapper.selectSystemTenant(2009L)).thenReturn(systemTenant(
                2009L, "科盾", 0, null, true));
        assertEquals(400, assertThrows(ServiceException.class, () -> service.update(request)).getCode());

        when(mapper.selectSystemTenant(2009L)).thenReturn(null);
        assertEquals(400, assertThrows(ServiceException.class, () -> service.update(request)).getCode());
        verify(mapper, never()).updatePortalConfig(any(), anyString());
    }

    @Test
    void closingRemainsAllowedWhenSystemTenantIsUnavailable() {
        MallPortalTenantDO existing = portal(11L, 3000L);
        when(mapper.selectPortalForUpdate(11L)).thenReturn(existing);
        when(mapper.selectSystemTenant(3000L)).thenReturn(null);
        when(mapper.updatePortalConfig(any(), anyString())).thenReturn(1);
        MallPortalTenantUpdateReqVO request = updateRequest(11L, "legacy-public", 1);
        request.setTenantLogo(" ");
        request.setMainCategories(null);

        service.update(request);

        ArgumentCaptor<MallPortalTenantDO> captor = ArgumentCaptor.forClass(MallPortalTenantDO.class);
        verify(mapper).updatePortalConfig(captor.capture(), anyString());
        assertEquals(CommonStatusEnum.DISABLE.getStatus(), captor.getValue().getOpenStatus());
        assertEquals("目录租户", captor.getValue().getTenantName());
        assertNull(captor.getValue().getTenantLogo());
        assertNull(captor.getValue().getMainCategories());
    }

    @Test
    void updateRejectsInvalidOrDuplicateSlugAndMissingDirectory() {
        when(mapper.selectPortalForUpdate(11L)).thenReturn(portal(11L, 2009L));

        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.update(updateRequest(11L, "../kedun", 1))).getCode());

        when(mapper.selectBySlugExcludingId("already-used", 11L))
                .thenReturn(portal(12L, 2010L));
        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.update(updateRequest(11L, "already-used", 1))).getCode());

        when(mapper.selectPortalForUpdate(99L)).thenReturn(null);
        assertEquals(404, assertThrows(ServiceException.class,
                () -> service.update(updateRequest(99L, "missing", 1))).getCode());
    }

    @Test
    void databaseUniqueConstraintIsMappedToStableBusinessError() {
        when(mapper.selectPortalForUpdate(11L)).thenReturn(portal(11L, 2009L));
        when(mapper.selectSystemTenant(2009L)).thenReturn(systemTenant(
                2009L, "科盾", 0, null, false));
        when(mapper.updatePortalConfig(any(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_portal_slug"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.update(updateRequest(11L, "kedun", 0)));

        assertEquals(400, error.getCode());
    }

    private static MallPortalTenantDO portal(Long id, Long tenantId) {
        return MallPortalTenantDO.builder()
                .id(id)
                .tenantId(tenantId)
                .tenantSlug("tenant-" + tenantId)
                .tenantName("目录租户")
                .openStatus(1)
                .sort(0)
                .build();
    }

    private static MallPortalTenantUpdateReqVO updateRequest(Long id, String slug, int status) {
        MallPortalTenantUpdateReqVO request = new MallPortalTenantUpdateReqVO();
        request.setId(id);
        request.setTenantSlug(slug);
        request.setOpenStatus(status);
        request.setSort(0);
        return request;
    }

    private static MallPortalTenantAdminMapper.SystemTenantSnapshot systemTenant(
            Long id, String name, Integer status, LocalDateTime expireTime, boolean deleted) {
        MallPortalTenantAdminMapper.SystemTenantSnapshot row =
                new MallPortalTenantAdminMapper.SystemTenantSnapshot();
        row.setId(id);
        row.setName(name);
        row.setStatus(status);
        row.setExpireTime(expireTime);
        row.setDeleted(deleted);
        return row;
    }
}
