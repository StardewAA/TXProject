package cn.iocoder.txgy.module.wms.api.inventoryread;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO;
import cn.iocoder.txgy.module.wms.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Collection;
import java.util.List;

@FeignClient(name = ApiConstants.NAME, contextId = "wmsInventoryReadApi")
public interface WmsInventoryReadApi {
    String PREFIX = ApiConstants.PREFIX + "/inventory-read";

    @GetMapping(PREFIX + "/snapshot")
    CommonResult<List<WmsInventorySnapshotRespDTO>> getInventorySnapshot(
            @RequestParam(value = "materialCodes", required = false) Collection<String> materialCodes,
            @RequestParam(value = "warehouseCodes", required = false) Collection<String> warehouseCodes);

    @GetMapping(PREFIX + "/existing-warehouse-codes")
    CommonResult<List<String>> getExistingWarehouseCodes(
            @RequestParam(value = "warehouseCodes", required = false) Collection<String> warehouseCodes);

    @GetMapping(PREFIX + "/existing-material-codes")
    CommonResult<List<String>> getExistingMaterialCodes(
            @RequestParam(value = "materialCodes", required = false) Collection<String> materialCodes);
}
