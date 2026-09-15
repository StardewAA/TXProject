package cn.iocoder.txgy.module.wms.api.stock;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.api.stock.dto.*;
import cn.iocoder.txgy.module.wms.enums.ApiConstants;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

/** Tenant-scoped read API. Does not reserve or mutate stock. */
@FeignClient(name = ApiConstants.NAME, contextId = "wmsStockReadApi")
public interface WmsStockReadApi {
    String PREFIX = ApiConstants.PREFIX + "/stock-read";
    @GetMapping(PREFIX + "/page")
    CommonResult<PageResult<WmsStockRespDTO>> getStockPage(@Valid @SpringQueryMap WmsStockPageReqDTO request);
    @GetMapping(PREFIX + "/warehouses")
    CommonResult<List<WmsWarehouseReadRespDTO>> getWarehouses();

    /** Includes enabled material/SKU master data with no inventory balance yet. */
    @GetMapping(PREFIX + "/skus")
    CommonResult<List<cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO.Sku>> getSkuOptions();
}
