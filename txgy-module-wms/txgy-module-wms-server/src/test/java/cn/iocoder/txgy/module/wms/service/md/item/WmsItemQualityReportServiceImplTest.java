package cn.iocoder.txgy.module.wms.service.md.item;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality.WmsItemQualityReportSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemQualityReportDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.enums.md.WmsItemQualityStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_QUALITY_REPORT_REMARK_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import(WmsItemQualityReportServiceImpl.class)
class WmsItemQualityReportServiceImplTest extends BaseDbUnitTest {
    @Resource
    private WmsItemQualityReportService qualityReportService;
    @Resource
    private WmsItemMapper itemMapper;

    @Test
    void testCreateQualityReport_latestBecomesCurrent() {
        WmsItemDO item = createItem();
        Long firstId = qualityReportService.createQualityReport(createReq(item.getId(),
                WmsItemQualityStatusEnum.ABNORMAL.getStatus(), List.of("https://example.com/a.jpg"), "破损"));
        Long secondId = qualityReportService.createQualityReport(createReq(item.getId(),
                WmsItemQualityStatusEnum.QUALIFIED.getStatus(), List.of("https://example.com/b.png"), null));

        assertEquals(2, qualityReportService.getQualityReportList(item.getId()).size());
        WmsItemQualityReportDO current = qualityReportService.getCurrentQualityReport(item.getId());
        assertNotNull(current);
        assertEquals(secondId, current.getId());
        assertEquals(secondId, itemMapper.selectById(item.getId()).getCurrentQualityReportId());
        assertEquals(firstId, qualityReportService.getQualityReportList(item.getId()).get(1).getId());
    }

    @Test
    void testCreateQualityReport_abnormalRemarkRequired() {
        WmsItemDO item = createItem();
        WmsItemQualityReportSaveReqVO req = createReq(item.getId(),
                WmsItemQualityStatusEnum.ABNORMAL.getStatus(), List.of("https://example.com/a.jpg"), " ");
        assertServiceException(() -> qualityReportService.createQualityReport(req),
                ITEM_QUALITY_REPORT_REMARK_REQUIRED);
    }

    @Test
    void testCreateQualityReport_pendingAllowsNoImage() {
        WmsItemDO item = createItem();
        qualityReportService.createQualityReport(createReq(item.getId(),
                WmsItemQualityStatusEnum.PENDING.getStatus(), Collections.emptyList(), null));
        assertEquals(WmsItemQualityStatusEnum.PENDING.getStatus(),
                qualityReportService.getCurrentQualityReport(item.getId()).getStatus());
    }

    private WmsItemDO createItem() {
        WmsItemDO item = new WmsItemDO().setCode("ITEM-QUALITY").setName("质检测试商品")
                .setCategoryId(1L).setUnit("件");
        itemMapper.insert(item);
        return item;
    }

    private static WmsItemQualityReportSaveReqVO createReq(Long itemId, Integer status,
                                                            List<String> imageUrls, String remark) {
        WmsItemQualityReportSaveReqVO req = new WmsItemQualityReportSaveReqVO();
        req.setItemId(itemId);
        req.setStatus(status);
        req.setImageUrls(imageUrls);
        req.setRemark(remark);
        return req;
    }
}
