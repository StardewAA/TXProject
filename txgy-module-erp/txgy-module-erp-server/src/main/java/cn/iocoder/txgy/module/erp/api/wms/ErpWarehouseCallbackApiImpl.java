package cn.iocoder.txgy.module.erp.api.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWarehouseUpsertCallbackReqDTO;
import cn.iocoder.txgy.module.erp.service.stock.ErpWarehouseService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * ERP 接收 WMS 仓库主数据 upsert RPC 实现。
 *
 * <p>参考 {@link ErpWmsCallbackApiImpl#callbackExecutionResult} 的 validateTenant 模式，
 * 委托给 {@link ErpWarehouseService#upsertFromWms}。</p>
 */
@RestController
@Validated
public class ErpWarehouseCallbackApiImpl implements ErpWarehouseCallbackApi {

    @Resource
    private ErpWarehouseService warehouseService;

    @Override
    public CommonResult<Boolean> callbackWarehouseUpsert(ErpWarehouseUpsertCallbackReqDTO reqDTO) {
        validateTenant(reqDTO.getTenantId());
        warehouseService.upsertFromWms(reqDTO);
        return success(true);
    }

    private void validateTenant(Long requestTenantId) {
        Long contextTenantId = TenantContextHolder.getRequiredTenantId();
        if (!contextTenantId.equals(requestTenantId)) {
            throw new IllegalArgumentException("请求租户与调用上下文不一致");
        }
    }

}
