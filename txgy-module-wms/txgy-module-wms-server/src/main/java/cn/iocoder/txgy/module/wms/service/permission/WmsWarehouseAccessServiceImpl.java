package cn.iocoder.txgy.module.wms.service.permission;

import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.WAREHOUSE_USER_NOT_MEMBER;

@Service
public class WmsWarehouseAccessServiceImpl implements WmsWarehouseAccessService {
    @Resource private WmsWarehouseUserMapper personnelMapper;
    @Resource private SecurityFrameworkService securityFrameworkService;

    @Override
    public boolean hasGlobalAccess() {
        // WMS 用户可以查看 WMS 内全部仓库及其负责人、员工；仓库人员关系只用于业务操作校验。
        return securityFrameworkService.hasPermission("wms:warehouse:query")
                || securityFrameworkService.hasRole("super_admin")
                || securityFrameworkService.hasRole("tenant_admin");
    }

    @Override
    public Set<Long> getReadableWarehouseIds() {
        return hasGlobalAccess() ? null : personnelMapper.selectWarehouseIdsByUserId(getLoginUserId());
    }

    @Override
    public void validateReadable(Long warehouseId) {
        if (!hasGlobalAccess() && !personnelMapper.selectWarehouseIdsByUserId(getLoginUserId()).contains(warehouseId)) {
            throw exception(WAREHOUSE_USER_NOT_MEMBER);
        }
    }

    @Override
    public Long resolveRequestedWarehouseId(Long requestedWarehouseId) {
        if (requestedWarehouseId != null) {
            validateReadable(requestedWarehouseId);
            return requestedWarehouseId;
        }
        Set<Long> readable = getReadableWarehouseIds();
        if (readable == null) return null;
        if (readable.size() == 1) return readable.iterator().next();
        if (readable.isEmpty()) throw exception(WAREHOUSE_USER_NOT_MEMBER);
        return null;
    }
}
