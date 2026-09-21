package cn.iocoder.txgy.module.wms.api.inventoryread;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO;
import cn.iocoder.txgy.module.wms.service.inventory.WmsInventoryReadApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collection;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequiredArgsConstructor
public class WmsInventoryReadApiImpl implements WmsInventoryReadApi {
    private final WmsInventoryReadApiService service;
    @Override public CommonResult<List<WmsInventorySnapshotRespDTO>> getInventorySnapshot(Collection<String> materialCodes, Collection<String> warehouseCodes) {
        return success(service.getSnapshot(materialCodes, warehouseCodes));
    }
    @Override public CommonResult<List<String>> getExistingWarehouseCodes(Collection<String> warehouseCodes) {
        return success(service.getExistingWarehouseCodes(warehouseCodes));
    }
    @Override public CommonResult<List<String>> getExistingMaterialCodes(Collection<String> materialCodes) {
        return success(service.getExistingMaterialCodes(materialCodes));
    }
}
