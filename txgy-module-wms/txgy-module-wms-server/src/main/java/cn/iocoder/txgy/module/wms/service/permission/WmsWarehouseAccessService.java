package cn.iocoder.txgy.module.wms.service.permission;

import java.util.Set;

public interface WmsWarehouseAccessService {
    boolean hasGlobalAccess();
    Set<Long> getReadableWarehouseIds();
    void validateReadable(Long warehouseId);
    Long resolveRequestedWarehouseId(Long requestedWarehouseId);
}
