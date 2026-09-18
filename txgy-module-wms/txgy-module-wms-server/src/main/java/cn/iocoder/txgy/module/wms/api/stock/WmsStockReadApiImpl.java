package cn.iocoder.txgy.module.wms.api.stock;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.api.stock.dto.*;
import cn.iocoder.txgy.module.wms.service.inventory.WmsStockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequiredArgsConstructor
public class WmsStockReadApiImpl implements WmsStockReadApi, WmsStockHistoryReadApi {
    private final WmsStockQueryService service;
    private final cn.iocoder.txgy.module.wms.service.inventory.WmsStockHistoryQueryService historyService;
    @Override public CommonResult<PageResult<WmsStockHistoryRespDTO>> getHistoryPage(WmsStockHistoryPageReqDTO request) {
        return success(historyService.getHistoryPage(request));
    }
    @Override public CommonResult<PageResult<WmsStockRespDTO>> getStockPage(WmsStockPageReqDTO request) {
        return success(service.getStockPage(request));
    }
    @Override public CommonResult<List<WmsWarehouseReadRespDTO>> getWarehouses() {
        return success(service.getWarehouses());
    }
    @Override public CommonResult<List<cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO.Sku>> getSkuOptions() {
        return success(service.getSkuOptions());
    }
}
