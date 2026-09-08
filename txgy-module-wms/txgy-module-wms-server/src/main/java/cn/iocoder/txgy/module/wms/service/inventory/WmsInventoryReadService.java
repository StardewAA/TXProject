package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.module.wms.controller.admin.inventory.vo.WmsInventorySkuSummaryRespVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.inventory.WmsInventoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryMapper;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemSkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** 只查询，不调用库存变更、锁定或出入库流程。租户过滤由现有 MyBatis 拦截器执行。 */
@Service
@RequiredArgsConstructor
public class WmsInventoryReadService {
    private final WmsItemService itemService;
    private final WmsItemSkuService skuService;
    private final WmsInventoryMapper inventoryMapper;

    @Transactional(readOnly = true)
    public List<WmsInventorySkuSummaryRespVO> getItemInventory(Long itemId) {
        var item = itemService.validateItemExists(itemId);
        var skus = skuService.getItemSkuList(itemId);
        if (skus.isEmpty()) {
            return Collections.emptyList();
        }
        var ids = skus.stream().map(WmsItemSkuDO::getId).toList();
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (WmsInventoryDO row : inventoryMapper.selectListBySkuIds(ids)) {
            totals.merge(row.getSkuId(), row.getQuantity(), BigDecimal::add);
        }
        return skus.stream().sorted(Comparator.comparing(WmsItemSkuDO::getId)).map(sku ->
                new WmsInventorySkuSummaryRespVO()
                        .setItemId(item.getId()).setItemCode(item.getCode()).setItemName(item.getName())
                        .setSkuId(sku.getId()).setSkuCode(sku.getCode()).setSkuName(sku.getName())
                        .setUnit(item.getUnit()).setSellingPrice(sku.getSellingPrice())
                        .setTotalQuantity(totals.getOrDefault(sku.getId(), BigDecimal.ZERO)))
                .toList();
    }
}
