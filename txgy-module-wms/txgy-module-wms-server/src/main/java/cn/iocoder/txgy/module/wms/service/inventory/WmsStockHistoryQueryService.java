package cn.iocoder.txgy.module.wms.service.inventory;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.api.stock.dto.*;
import org.springframework.stereotype.Service;
@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class WmsStockHistoryQueryService {
    @jakarta.annotation.Resource
    private cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsStockReadMapper mapper;
    public PageResult<WmsStockHistoryRespDTO> getHistoryPage(WmsStockHistoryPageReqDTO request) {
        Long tenant = cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        if (request.getPageNo() < 1 || request.getPageSize() < 1 || request.getPageSize() > 200) {
            throw new IllegalArgumentException("分页必须为 pageNo>=1、pageSize=1..200");
        }
        var result = mapper.selectHistoryPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                request.getPageNo(), request.getPageSize()), tenant, request);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }
}
