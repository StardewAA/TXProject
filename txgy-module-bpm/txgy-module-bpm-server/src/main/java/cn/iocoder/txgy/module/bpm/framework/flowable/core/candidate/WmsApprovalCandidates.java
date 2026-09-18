package cn.iocoder.txgy.module.bpm.framework.flowable.core.candidate;

import cn.iocoder.txgy.module.bpm.dal.mysql.wms.WmsApprovalPermissionMapper;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.Resource;
import cn.iocoder.txgy.module.wms.api.warehouse.WmsWarehousePersonnelApi;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;

@Component("wmsApprovalCandidates")
@RequiredArgsConstructor
public class WmsApprovalCandidates {
    private static final Map<String, String> PERMISSIONS = Map.of(
            "wms_receipt_approval", "wms:receipt-order:approve-warehouse",
            "wms_shipment_approval", "wms:shipment-order:approve-supervisor");
    private final WmsApprovalPermissionMapper mapper;
    @Resource private WmsWarehousePersonnelApi warehousePersonnelApi;
    @Resource private RuntimeService runtimeService;

    public Set<Long> resolve(String processKey) {
        String permission = PERMISSIONS.get(processKey);
        if (permission == null) throw new IllegalArgumentException("Not a WMS receipt/shipment approval process");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long menuId = mapper.selectEnabledMenuId(permission);
        String packageMenus = mapper.selectPackageMenuIds(tenantId);
        // 租户套餐必须允许该审批权限；普通用户需由角色显式授权，启用的超级管理员按全权限处理。
        if (menuId == null || packageMenus == null || !JsonUtils.parseArray(packageMenus, Long.class).contains(menuId)) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(mapper.selectUserIds(tenantId, menuId));
    }

    public Set<Long> resolveDelegateUsers(String processKey) {
        if (!PERMISSIONS.containsKey(processKey)) throw new IllegalArgumentException("Not a WMS receipt/shipment approval process");
        Set<Long> users = new LinkedHashSet<>(resolve(processKey));
        if ("wms_shipment_approval".equals(processKey)) {
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            Long menuId = mapper.selectEnabledMenuId("wms:shipment-order:approve-decision");
            String packageMenus = mapper.selectPackageMenuIds(tenantId);
            if (menuId != null && packageMenus != null
                    && JsonUtils.parseArray(packageMenus, Long.class).contains(menuId)) {
                users.addAll(mapper.selectUserIds(tenantId, menuId));
            }
        }
        return users;
    }

    public Set<Long> resolveDelegateUsers(Task task) {
        Object rawWarehouseId = runtimeService.getVariable(task.getProcessInstanceId(), "warehouseId");
        if (rawWarehouseId == null) return new LinkedHashSet<>();
        Long warehouseId = rawWarehouseId instanceof Number number ? number.longValue() : Long.valueOf(rawWarehouseId.toString());
        return new LinkedHashSet<>(warehousePersonnelApi.getPersonnel(warehouseId).getCheckedData().getMemberUserIds());
    }
}
