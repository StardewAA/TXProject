package cn.iocoder.txgy.module.wms.api.warehouse;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.api.warehouse.dto.WmsWarehousePersonnelRespDTO;
import cn.iocoder.txgy.module.wms.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME, contextId = "wmsWarehousePersonnelApi")
public interface WmsWarehousePersonnelApi {
    String PREFIX = ApiConstants.PREFIX + "/warehouse-personnel";

    @GetMapping(PREFIX + "/get")
    CommonResult<WmsWarehousePersonnelRespDTO> getPersonnel(@RequestParam("warehouseId") Long warehouseId);

    @GetMapping(PREFIX + "/is-member")
    CommonResult<Boolean> isMember(@RequestParam("warehouseId") Long warehouseId,
                                   @RequestParam("userId") Long userId);
}
