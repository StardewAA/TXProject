package cn.iocoder.txgy.module.product.api.enterprise;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallEnterpriseCustomerApiTest {
    private ProductEnterpriseMapper mapper;
    private ProductEnterpriseCustomerApiImpl api;
    @BeforeEach void setup() { mapper=mock(ProductEnterpriseMapper.class); api=new ProductEnterpriseCustomerApiImpl(mapper); TenantContextHolder.clear(); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void absentOrIgnoredTenantCannotQueryEnterprise() {
        assertNull(api.getCustomerOwner(1L).getCheckedData());
        TenantContextHolder.setTenantId(9001L); TenantContextHolder.setIgnore(true);
        assertNull(api.getCustomerOwner(1L).getCheckedData()); verifyNoInteractions(mapper);
    }
    @Test void missingForeignOrDeletedEnterpriseReturnsNoData() {
        TenantContextHolder.setTenantId(9001L); assertNull(api.getCustomerOwner(1L).getCheckedData());
        var row=new ProductEnterpriseDO(); row.setTenantId(9002L); when(mapper.selectById(1L)).thenReturn(row);
        assertNull(api.getCustomerOwner(1L).getCheckedData()); row.setTenantId(9001L); row.setDeleted(true);
        assertNull(api.getCustomerOwner(1L).getCheckedData());
    }
    @Test void minimalProjectionDoesNotExposeLicenseCreditCodeOrContacts() {
        TenantContextHolder.setTenantId(9001L);
        var row=new ProductEnterpriseDO(); row.setId(1L); row.setTenantId(9001L); row.setName("平台"); row.setAuditStatus(1);
        row.setLicenseUrl("private"); row.setContactMobile("private"); row.setCreditCode("private");
        when(mapper.selectById(1L)).thenReturn(row);
        assertEquals(new ProductEnterpriseCustomerApi.Owner(1L,9001L,"平台",true),api.getCustomerOwner(1L).getCheckedData());
        assertEquals(4,ProductEnterpriseCustomerApi.Owner.class.getRecordComponents().length);
    }
    @Test void revokedApprovalIsNotTreatedAsApproved() {
        TenantContextHolder.setTenantId(9001L); var row=new ProductEnterpriseDO(); row.setId(1L); row.setTenantId(9001L); row.setAuditStatus(2);
        when(mapper.selectById(1L)).thenReturn(row); assertFalse(api.getCustomerOwner(1L).getCheckedData().approved());
    }
}
