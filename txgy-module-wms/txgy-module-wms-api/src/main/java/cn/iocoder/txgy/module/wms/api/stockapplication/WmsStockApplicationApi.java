package cn.iocoder.txgy.module.wms.api.stockapplication;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.api.stockapplication.dto.*;
import cn.iocoder.txgy.module.wms.enums.ApiConstants;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;
@FeignClient(name = ApiConstants.NAME, contextId = "wmsStockApplicationApi")
public interface WmsStockApplicationApi {
    String PREFIX = ApiConstants.PREFIX + "/stock-application";
    @PostMapping(PREFIX + "/create") CommonResult<Long> create(@Valid @RequestBody WmsStockApplicationCreateReqDTO request);
    @GetMapping(PREFIX + "/get") CommonResult<WmsStockApplicationRespDTO> get(@RequestParam("id") Long id);
    @GetMapping(PREFIX + "/page") CommonResult<PageResult<WmsStockApplicationRespDTO>> page(@Valid @SpringQueryMap WmsStockApplicationPageReqDTO request);
    @GetMapping(PREFIX + "/options") CommonResult<WmsStockApplicationOptionsRespDTO> options();
    @PostMapping(PREFIX + "/submit") CommonResult<Boolean> submit(@Valid @RequestBody WmsStockApplicationActionReqDTO request);
    @PostMapping(PREFIX + "/cancel") CommonResult<Boolean> cancel(@Valid @RequestBody WmsStockApplicationActionReqDTO request);
    @PostMapping(PREFIX + "/dispatch") CommonResult<Boolean> dispatch(@Valid @RequestBody WmsStockApplicationActionReqDTO request);
    @PostMapping(PREFIX + "/receive") CommonResult<Boolean> receive(@Valid @RequestBody WmsStockApplicationActionReqDTO request);
    @PostMapping(PREFIX + "/retry") CommonResult<Boolean> retry(@Valid @RequestBody WmsStockApplicationActionReqDTO request);
}
