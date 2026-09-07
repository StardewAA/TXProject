package cn.iocoder.txgy.module.wms.service.md.item;

import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality.WmsItemQualityReportSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemQualityReportDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface WmsItemQualityReportService {
    Long createQualityReport(@Valid WmsItemQualityReportSaveReqVO reqVO);
    List<WmsItemQualityReportDO> getQualityReportList(Long itemId);
    WmsItemQualityReportDO getCurrentQualityReport(Long itemId);
    Map<Long, WmsItemQualityReportDO> getCurrentQualityReportMap(Collection<Long> itemIds);
    long getQualityReportCount(Long itemId);
}
