package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WmsInventoryReadApiService {
    private final WmsInventoryReadMapper mapper;

    public List<WmsInventorySnapshotRespDTO> getSnapshot(Collection<String> materialCodes, Collection<String> warehouseCodes) {
        return mapper.selectSnapshot(TenantContextHolder.getRequiredTenantId(), materialCodes, warehouseCodes);
    }
    public List<String> getExistingWarehouseCodes(Collection<String> codes) {
        return mapper.selectExistingWarehouseCodes(TenantContextHolder.getRequiredTenantId(), codes);
    }
    public List<String> getExistingMaterialCodes(Collection<String> codes) {
        return mapper.selectExistingMaterialCodes(TenantContextHolder.getRequiredTenantId(), codes);
    }
}
