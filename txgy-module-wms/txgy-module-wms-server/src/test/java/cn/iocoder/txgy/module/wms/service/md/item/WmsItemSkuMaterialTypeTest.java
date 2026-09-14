package cn.iocoder.txgy.module.wms.service.md.item;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.sku.WmsItemSkuPageReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WmsItemSkuMaterialTypeTest extends BaseDbUnitTest {
    @Resource WmsItemMapper itemMapper;
    @Resource WmsItemSkuMapper skuMapper;

    private long add(String type, int status) {
        var item = WmsItemDO.builder().name("商品").categoryId(1L).status(status).materialType(type).build();
        itemMapper.insert(item);
        var sku = WmsItemSkuDO.builder().itemId(item.getId()).name("标准").build();
        skuMapper.insert(sku);
        return sku.getId();
    }

    @Test void filtersBeforePaginationAndKeepsUnfilteredCompatibility() {
        long raw1=add("RAW_MATERIAL",0), raw2=add("RAW_MATERIAL",0);
        add("FINISHED_GOODS",0); add(null,0); add("RAW_MATERIAL",1);
        var req=JsonUtils.parseObject("{\"materialType\":\"RAW_MATERIAL\",\"itemStatus\":0,\"pageNo\":1,\"pageSize\":1}", WmsItemSkuPageReqVO.class);
        var first=skuMapper.selectPage(req);
        assertEquals(2L,first.getTotal());
        assertEquals(raw2,first.getList().getFirst().getId());
        req.setPageNo(2);
        assertEquals(raw1,skuMapper.selectPage(req).getList().getFirst().getId());
        var all=new WmsItemSkuPageReqVO(); all.setItemStatus(0);
        assertEquals(4L,skuMapper.selectPage(all).getTotal());
    }

    @Test void doesNotReturnDeletedMaterialOrSku() {
        long deletedSku=add("RAW_MATERIAL",0), deletedItemSku=add("RAW_MATERIAL",0);
        skuMapper.deleteById(deletedSku);
        itemMapper.deleteById(skuMapper.selectById(deletedItemSku).getItemId());
        add("FINISHED_GOODS",0);
        var req=JsonUtils.parseObject("{\"materialType\":\"RAW_MATERIAL\"}", WmsItemSkuPageReqVO.class);
        assertEquals(0L,skuMapper.selectPage(req).getTotal());
    }
}
