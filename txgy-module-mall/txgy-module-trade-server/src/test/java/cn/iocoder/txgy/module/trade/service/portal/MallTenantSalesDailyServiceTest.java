package cn.iocoder.txgy.module.trade.service.portal;

import cn.iocoder.txgy.module.trade.dal.mysql.portal.MallTenantSalesDailyMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MallTenantSalesDailyServiceTest {

    @Test
    void refreshIsReplaceNotIncrementAndBoundsWindow() {
        MallTenantSalesDailyMapper mapper = mock(MallTenantSalesDailyMapper.class);
        when(mapper.insertRange(any(), any())).thenReturn(24);
        MallTenantSalesDailyService service = new MallTenantSalesDailyService(mapper);

        assertEquals(24, service.refreshRecent(10_000));

        var ordered = inOrder(mapper);
        ordered.verify(mapper).deleteRange(any(), any());
        ordered.verify(mapper).insertRange(any(), any());
    }
}
