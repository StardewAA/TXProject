package cn.iocoder.txgy.module.wms.api.stock;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.api.stock.dto.*;
import cn.iocoder.txgy.module.wms.enums.ApiConstants;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
/** Executed WMS inventory events only; never creates or changes inventory. */
@FeignClient(name = ApiConstants.NAME, contextId = "wmsStockHistoryReadApi")
public interface WmsStockHistoryReadApi {
    String PREFIX = ApiConstants.PREFIX + "/stock-history-read";
    @GetMapping(PREFIX + "/page")
    CommonResult<PageResult<WmsStockHistoryRespDTO>> getHistoryPage(@Valid @SpringQueryMap WmsStockHistoryPageReqDTO request);
}
