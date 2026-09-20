package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AfterSaleLatestResultTest {
    @InjectMocks private AfterSaleServiceImpl service;
    @Mock private AfterSaleMapper mapper;

    @Test
    void keepsOnlyTheNewestResultForEachOrderItem() {
        var newest = new AfterSaleDO().setId(12L).setOrderItemId(100L).setStatus(62).setAuditReason("资料不完整");
        var older = new AfterSaleDO().setId(11L).setOrderItemId(100L).setStatus(61);
        var another = new AfterSaleDO().setId(10L).setOrderItemId(101L).setStatus(50);
        when(mapper.selectLatestByOrderItemIds(7L, List.of(100L, 101L)))
                .thenReturn(List.of(newest, older, another));

        var result = service.getLatestAfterSales(7L, List.of(100L, 101L));

        assertEquals(newest, result.get(100L));
        assertEquals(another, result.get(101L));
        assertEquals(2, result.size());
    }
}
