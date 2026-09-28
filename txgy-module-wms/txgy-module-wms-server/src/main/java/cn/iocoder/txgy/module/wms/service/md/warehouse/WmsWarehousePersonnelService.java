package cn.iocoder.txgy.module.wms.service.md.warehouse;

import java.util.Set;

public interface WmsWarehousePersonnelService {
    void replacePersonnel(Long warehouseId, Long managerUserId, Set<Long> memberUserIds);
    void clearPersonnel(Long warehouseId);
    Long getManagerUserId(Long warehouseId);
    Set<Long> getMemberUserIds(Long warehouseId);
    boolean isMember(Long warehouseId, Long userId);
    void validateMember(Long warehouseId, Long userId);
    void validateConfigured(Long warehouseId);
}
