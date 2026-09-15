package cn.iocoder.txgy.module.wms.service.inventory;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.stock.dto.*;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsStockReadMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WmsStockQueryService {
    private final WmsStockReadMapper mapper;
    public PageResult<WmsStockRespDTO> getStockPage(WmsStockPageReqDTO request) {
        var result = mapper.selectStockPage(new Page<>(request.getPageNo(),request.getPageSize()),
                TenantContextHolder.getRequiredTenantId(),request);
        return new PageResult<>(result.getRecords(),result.getTotal());
    }
    public List<WmsWarehouseReadRespDTO> getWarehouses() {
        return mapper.selectWarehouses(TenantContextHolder.getRequiredTenantId());
    }
    public List<cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO.Sku> getSkuOptions() {
        return mapper.selectSkuOptions(TenantContextHolder.getRequiredTenantId());
    }
}
