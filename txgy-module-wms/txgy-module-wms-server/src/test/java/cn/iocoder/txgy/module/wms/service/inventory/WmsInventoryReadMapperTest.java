package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.dal.dataobject.inventory.WmsInventoryDO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WmsInventoryReadMapperTest extends BaseDbUnitTest {
    @Resource
    private WmsInventoryMapper mapper;

    @Test
    void filtersSpecificationsAndDeletedRowsWithoutChangingBalances() {
        var first = WmsInventoryDO.builder().skuId(11L).warehouseId(1L).quantity(new BigDecimal("1.25")).build();
        var second = WmsInventoryDO.builder().skuId(11L).warehouseId(2L).quantity(new BigDecimal("2.50")).build();
        var other = WmsInventoryDO.builder().skuId(12L).warehouseId(1L).quantity(new BigDecimal("99")).build();
        var deleted = WmsInventoryDO.builder().skuId(11L).warehouseId(3L).quantity(new BigDecimal("100")).build();
        mapper.insert(first);
        mapper.insert(second);
        mapper.insert(other);
        mapper.insert(deleted);
        mapper.deleteById(deleted.getId());
        var result = mapper.selectListBySkuIds(List.of(11L));
        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("3.75").compareTo(result.stream()
                .map(WmsInventoryDO::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(3L, mapper.selectCount());
        assertEquals(0, new BigDecimal("1.25").compareTo(mapper.selectById(first.getId()).getQuantity()));
        assertTrue(mapper.selectListBySkuIds(List.of()).isEmpty());
    }
}
