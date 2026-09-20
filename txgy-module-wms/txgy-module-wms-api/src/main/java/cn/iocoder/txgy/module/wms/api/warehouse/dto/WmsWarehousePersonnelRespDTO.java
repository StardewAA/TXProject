package cn.iocoder.txgy.module.wms.api.warehouse.dto;

import lombok.Data;
import java.util.Set;

@Data
public class WmsWarehousePersonnelRespDTO {
    private Long warehouseId;
    private Long managerUserId;
    private Set<Long> memberUserIds;
}
