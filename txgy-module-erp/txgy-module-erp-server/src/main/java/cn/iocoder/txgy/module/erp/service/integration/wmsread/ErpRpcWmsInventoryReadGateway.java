package cn.iocoder.txgy.module.erp.service.integration.wmsread;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.service.integration.wmsread.bo.ErpWmsInventorySnapshotBO;
import cn.iocoder.txgy.module.wms.api.inventoryread.WmsInventoryReadApi;
import cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.WMS_INVENTORY_READ_FAILED;

/**
 * WMS 库存只读网关 —— RPC 实现
 *
 * <p>调用 WMS 官方只读接口 {@link WmsInventoryReadApi}
 * （{@code /rpc-api/wms/inventory-read/**}）。需要配置
 * {@code txgy.integration.wms.inventory-read.mode=rpc} 才生效，
 * 且调用方必须已建立租户上下文（沿用现有 Feign 租户透传，接口不接受 tenantId 参数）。</p>
 *
 * <p><b>失败即失败</b>：接口异常、超时或 {@code code != 0} 一律抛错，绝不返回空集合或 0，
 * 避免调用方把"查询失败"误当成"零库存"而覆盖已有镜像。</p>
 *
 * @author OPENLAB BS
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.inventory-read", name = "mode", havingValue = "rpc")
@Slf4j
public class ErpRpcWmsInventoryReadGateway implements ErpWmsInventoryReadGateway {

    @Resource
    private WmsInventoryReadApi wmsInventoryReadApi;

    @Override
    public List<ErpWmsInventorySnapshotBO> getInventorySnapshot(Collection<String> materialCodes,
                                                                Collection<String> warehouseCodes) {
        try {
            List<WmsInventorySnapshotRespDTO> rows = wmsInventoryReadApi.getInventorySnapshot(
                    emptyToNull(materialCodes), emptyToNull(warehouseCodes)).getCheckedData();
            return BeanUtils.toBean(rows, ErpWmsInventorySnapshotBO.class);
        } catch (Exception ex) {
            throw readFailed("snapshot", ex);
        }
    }

    @Override
    public List<String> getExistingWarehouseCodes(Collection<String> warehouseCodes) {
        try {
            List<String> codes = wmsInventoryReadApi.getExistingWarehouseCodes(
                    emptyToNull(warehouseCodes)).getCheckedData();
            return codes == null ? Collections.emptyList() : codes;
        } catch (Exception ex) {
            throw readFailed("existing-warehouse-codes", ex);
        }
    }

    @Override
    public List<String> getExistingMaterialCodes(Collection<String> materialCodes) {
        try {
            List<String> codes = wmsInventoryReadApi.getExistingMaterialCodes(
                    emptyToNull(materialCodes)).getCheckedData();
            return codes == null ? Collections.emptyList() : codes;
        } catch (Exception ex) {
            throw readFailed("existing-material-codes", ex);
        }
    }

    /**
     * 空集合按契约视为"不限制"，统一转成 null 避免 Feign 拼出 {@code ?materialCodes=} 这种空值参数
     */
    private Collection<String> emptyToNull(Collection<String> codes) {
        return CollUtil.isEmpty(codes) ? null : codes;
    }

    private RuntimeException readFailed(String endpoint, Exception ex) {
        log.error("[readFailed][WMS 库存只读接口 {} 调用失败]", endpoint, ex);
        return exception(WMS_INVENTORY_READ_FAILED, endpoint + "：" + ex.getMessage());
    }

}
