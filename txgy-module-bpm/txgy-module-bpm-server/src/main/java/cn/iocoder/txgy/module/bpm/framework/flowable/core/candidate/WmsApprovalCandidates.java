package cn.iocoder.txgy.module.bpm.framework.flowable.core.candidate;

import cn.iocoder.txgy.module.bpm.dal.mysql.wms.WmsApprovalPermissionMapper;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
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

    public Set<Long> resolve(String processKey) {
        String permission = PERMISSIONS.get(processKey);
        if (permission == null) throw new IllegalArgumentException("Not a WMS receipt/shipment approval process");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long menuId = mapper.selectEnabledMenuId(permission);
        String packageMenus = mapper.selectPackageMenuIds(tenantId);
        // 必须是本租户套餐允许且角色明确勾选的审批权限，不把超级管理员的通配权限当作审批资格。
        if (menuId == null || packageMenus == null || !JsonUtils.parseArray(packageMenus, Long.class).contains(menuId)) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(mapper.selectUserIds(tenantId, menuId));
    }
}
