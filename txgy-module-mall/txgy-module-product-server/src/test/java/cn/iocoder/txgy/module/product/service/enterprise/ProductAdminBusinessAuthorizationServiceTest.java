package cn.iocoder.txgy.module.product.service.enterprise;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.ProductAdminBusinessMemberSaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseAdminMemberDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseAdminMemberMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopAdminAuthorizationMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.service.audit.ProductBusinessOperationAuditService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAdminBusinessAuthorizationServiceTest {

    private static final long CALLER = 10L;
    private static final long TARGET = 20L;
    private static final long ENTERPRISE = 30L;

    @Mock private ProductAdminBusinessScopeService scopeService;
    @Mock private ProductEnterpriseAdminMemberMapper memberMapper;
    @Mock private ProductShopAdminAuthorizationMapper authorizationMapper;
    @Mock private ProductShopMapper shopMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private ProductBusinessOperationAuditService operationAuditService;
    @InjectMocks private ProductAdminBusinessAuthorizationService service;

    @BeforeEach
    void setUp() {
        when(scopeService.scope(CALLER)).thenReturn(
                new ProductAdminBusinessScopeService.Scope(1L, CALLER, true, Set.of(), Set.of()));
    }

    @Test
    void cannotCreateDisabledGhostMembership() {
        ProductAdminBusinessMemberSaveReqVO request = request(1);
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.save(CALLER, request));

        assertEquals("企业成员授权不存在，无需停用", error.getMessage());
        verify(memberMapper, never()).insert(any(ProductEnterpriseAdminMemberDO.class));
        verifyNoInteractions(adminUserApi, authorizationMapper, operationAuditService);
    }

    @Test
    void activeTargetCanReceiveEnterpriseWideAuthorization() {
        ProductAdminBusinessMemberSaveReqVO request = request(0);
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(
                (ProductEnterpriseAdminMemberDO) null, (ProductEnterpriseAdminMemberDO) null);
        AdminUserRespDTO target = new AdminUserRespDTO();
        target.setId(TARGET); target.setStatus(0);
        when(adminUserApi.getUser(TARGET)).thenReturn(CommonResult.success(target));

        service.save(CALLER, request);

        ArgumentCaptor<ProductEnterpriseAdminMemberDO> membership =
                ArgumentCaptor.forClass(ProductEnterpriseAdminMemberDO.class);
        verify(memberMapper).insert(membership.capture());
        assertEquals(TARGET, membership.getValue().getAdminUserId());
        assertEquals(ENTERPRISE, membership.getValue().getEnterpriseId());
        assertEquals(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE, membership.getValue().getScopeType());
        verify(operationAuditService).success(eq(ENTERPRISE), isNull(), eq("ADMIN"), eq(CALLER),
                eq("BUSINESS_MEMBER_GRANT"), eq("ADMIN_USER"), eq(TARGET), anyString());
    }

    private static ProductAdminBusinessMemberSaveReqVO request(int status) {
        ProductAdminBusinessMemberSaveReqVO request = new ProductAdminBusinessMemberSaveReqVO();
        request.setAdminUserId(TARGET); request.setEnterpriseId(ENTERPRISE);
        request.setScopeType(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE);
        request.setStatus(status); request.setShopIds(Set.of());
        return request;
    }
}
