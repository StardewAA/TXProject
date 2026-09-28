package cn.iocoder.txgy.module.wms.service.md.item;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality.WmsItemQualityReportSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemQualityReportDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemQualityReportMapper;
import cn.iocoder.txgy.module.wms.enums.md.WmsItemQualityStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

@Service
@Validated
public class WmsItemQualityReportServiceImpl implements WmsItemQualityReportService {
    @Resource
    private WmsItemQualityReportMapper qualityReportMapper;
    @Resource
    private WmsItemMapper itemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQualityReport(WmsItemQualityReportSaveReqVO reqVO) {
        WmsItemDO item = reqVO.getItemId() == null ? null : itemMapper.selectById(reqVO.getItemId());
        if (item == null) {
            throw exception(ITEM_NOT_EXISTS);
        }
        validateQualityReport(reqVO);
        WmsItemQualityReportDO report = WmsItemQualityReportDO.builder()
                .itemId(reqVO.getItemId()).status(reqVO.getStatus())
                .imageUrls(JsonUtils.toJsonString(reqVO.getImageUrls() == null
                        ? Collections.emptyList() : reqVO.getImageUrls()))
                .remark(StrUtil.trim(reqVO.getRemark())).build();
        qualityReportMapper.insert(report);
        itemMapper.updateById(new WmsItemDO().setId(item.getId()).setCurrentQualityReportId(report.getId()));
        return report.getId();
    }

    private void validateQualityReport(WmsItemQualityReportSaveReqVO reqVO) {
        if (!WmsItemQualityStatusEnum.contains(reqVO.getStatus())) {
            throw exception(ITEM_QUALITY_REPORT_STATUS_INVALID);
        }
        List<String> images = reqVO.getImageUrls() == null ? Collections.emptyList() : reqVO.getImageUrls();
        if (reqVO.getStatus().equals(WmsItemQualityStatusEnum.QUALIFIED.getStatus())
                || reqVO.getStatus().equals(WmsItemQualityStatusEnum.ABNORMAL.getStatus())) {
            if (images.isEmpty() || images.size() > 9) {
                throw exception(ITEM_QUALITY_REPORT_IMAGES_INVALID);
            }
        }
        if (images.stream().anyMatch(url -> StrUtil.isBlank(url) || !isSupportedImageUrl(url))) {
            throw exception(ITEM_QUALITY_REPORT_IMAGE_URL_INVALID);
        }
        if (reqVO.getStatus().equals(WmsItemQualityStatusEnum.ABNORMAL.getStatus())
                && StrUtil.isBlank(reqVO.getRemark())) {
            throw exception(ITEM_QUALITY_REPORT_REMARK_REQUIRED);
        }
    }

    private boolean isSupportedImageUrl(String url) {
        String path = StrUtil.subBefore(StrUtil.trim(url), '?', false).toLowerCase();
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png");
    }

    @Override
    public List<WmsItemQualityReportDO> getQualityReportList(Long itemId) {
        return qualityReportMapper.selectListByItemId(itemId);
    }

    @Override
    public WmsItemQualityReportDO getCurrentQualityReport(Long itemId) {
        WmsItemDO item = itemMapper.selectById(itemId);
        return item == null || item.getCurrentQualityReportId() == null ? null
                : qualityReportMapper.selectById(item.getCurrentQualityReportId());
    }

    @Override
    public Map<Long, WmsItemQualityReportDO> getCurrentQualityReportMap(Collection<Long> itemIds) {
        if (CollUtil.isEmpty(itemIds)) {
            return Collections.emptyMap();
        }
        List<WmsItemDO> items = itemMapper.selectByIds(itemIds);
        Map<Long, Long> reportItemMap = items.stream().filter(item -> item.getCurrentQualityReportId() != null)
                .collect(Collectors.toMap(WmsItemDO::getCurrentQualityReportId, WmsItemDO::getId));
        if (reportItemMap.isEmpty()) {
            return Collections.emptyMap();
        }
        return qualityReportMapper.selectListByIds(reportItemMap.keySet()).stream()
                .collect(Collectors.toMap(report -> reportItemMap.get(report.getId()), Function.identity()));
    }

    @Override
    public long getQualityReportCount(Long itemId) {
        return qualityReportMapper.selectCountByItemId(itemId);
    }
}
