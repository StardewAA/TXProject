package cn.iocoder.txgy.module.erp.service.integration.wmsread;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWmsInventoryReadonlyMapper;
import cn.iocoder.txgy.module.erp.service.integration.wmsread.bo.ErpWmsInventorySnapshotBO;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WMS 库存只读网关 —— 本地直查实现（过渡）
 *
 * <p>直接查询 WMS 表获取库存快照，语义与 WMS 只读接口保持一致。
 * 仅在 {@code txgy.integration.wms.inventory-read.mode} 未配置或为 {@code local} 时生效。</p>
 *
 * @author OPENLAB BS
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.inventory-read", name = "mode",
        havingValue = "local", matchIfMissing = true)
public class ErpLocalWmsInventoryReadGateway implements ErpWmsInventoryReadGateway {

    @Resource
    private ErpWmsInventoryReadonlyMapper readonlyMapper;

    @Override
    public List<ErpWmsInventorySnapshotBO> getInventorySnapshot(Collection<String> materialCodes,
                                                                Collection<String> warehouseCodes) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return BeanUtils.toBean(readonlyMapper.selectInventorySnapshot(tenantId, materialCodes, warehouseCodes),
                ErpWmsInventorySnapshotBO.class);
    }

    @Override
    public List<String> getExistingWarehouseCodes(Collection<String> warehouseCodes) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return intersect(readonlyMapper.selectWarehouseCodes(tenantId), warehouseCodes);
    }

    @Override
    public List<String> getExistingMaterialCodes(Collection<String> materialCodes) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return intersect(readonlyMapper.selectMaterialCodes(tenantId), materialCodes);
    }

    /**
     * 取交集：查询条件为空表示不限制，返回全部；否则只保留请求的编码
     */
    private List<String> intersect(List<String> existing, Collection<String> requested) {
        if (CollUtil.isEmpty(requested)) {
            return existing == null ? Collections.emptyList() : existing;
        }
        if (CollUtil.isEmpty(existing)) {
            return Collections.emptyList();
        }
        return existing.stream().filter(item -> requested.contains(item)).collect(Collectors.toList());
    }

}
