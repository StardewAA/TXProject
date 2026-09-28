package cn.iocoder.txgy.module.trade.api.order;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.api.order.dto.TradeOrderServiceCardRespDTO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderServiceCardMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeOrderServiceCardTest {
    TradeOrderServiceCardMapper query = mock(TradeOrderServiceCardMapper.class);
    TradeOrderApiImpl api = new TradeOrderApiImpl();
    TradeOrderServiceCardRespDTO order = new TradeOrderServiceCardRespDTO().setId(8L).setUserId(11L).setShopId(101L).setNo("T8").setPayPrice(1200).setStatus(20);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L);order.setTenantId(1L);ReflectionTestUtils.setField(api,"serviceCardMapper",query);when(query.selectOwned(8L,11L,1L)).thenReturn(order); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void projectsOnlyOwnTenantOrder() { var card=api.getServiceCard(8L,11L,1L).getCheckedData();assertNotNull(card);assertEquals(101L,card.getShopId());assertEquals(1200,card.getPayPrice()); }
    @Test void rejectsInvalidOrDifferentTenantBeforeQuery() {
        assertNull(api.getServiceCard(8L,11L,2L).getCheckedData());assertNull(api.getServiceCard(0L,11L,1L).getCheckedData());
        TenantContextHolder.setIgnore(true);assertNull(api.getServiceCard(8L,11L,1L).getCheckedData());verifyNoInteractions(query);
    }
    @Test void rejectsWrongOwnerMissingAndMismatchedRowTenant() {
        assertNull(api.getServiceCard(8L,22L,1L).getCheckedData());assertNull(api.getServiceCard(9L,11L,1L).getCheckedData());
        order.setTenantId(2L);assertNull(api.getServiceCard(8L,11L,1L).getCheckedData());
    }
}
