package cn.iocoder.txgy.module.wms.api.warehouse;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.api.warehouse.dto.WmsWarehousePersonnelRespDTO;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehousePersonnelService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
public class WmsWarehousePersonnelApiImpl implements WmsWarehousePersonnelApi {
    @Resource private WmsWarehousePersonnelService personnelService;

    @Override
    public CommonResult<WmsWarehousePersonnelRespDTO> getPersonnel(Long warehouseId) {
        personnelService.validateConfigured(warehouseId);
        WmsWarehousePersonnelRespDTO dto = new WmsWarehousePersonnelRespDTO();
        dto.setWarehouseId(warehouseId);
        dto.setManagerUserId(personnelService.getManagerUserId(warehouseId));
        dto.setMemberUserIds(personnelService.getMemberUserIds(warehouseId));
        return success(dto);
    }

    @Override
    public CommonResult<Boolean> isMember(Long warehouseId, Long userId) {
        return success(personnelService.isMember(warehouseId, userId));
    }
}
