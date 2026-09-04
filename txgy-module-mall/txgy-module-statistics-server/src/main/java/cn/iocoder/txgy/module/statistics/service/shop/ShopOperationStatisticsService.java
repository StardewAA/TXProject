package cn.iocoder.txgy.module.statistics.service.shop;

import cn.iocoder.txgy.module.statistics.controller.admin.shop.vo.ShopOperationStatisticsRespVO;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopOperationStatisticsService {

    List<ShopOperationStatisticsRespVO> getOperationStatistics(Long shopId, LocalDateTime beginTime,
                                                               LocalDateTime endTime);
}

