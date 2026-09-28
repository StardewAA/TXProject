package cn.iocoder.txgy.module.erp.service.integration.wmsread;

import cn.iocoder.txgy.module.erp.service.integration.wmsread.bo.ErpWmsInventorySnapshotBO;

import java.util.Collection;
import java.util.List;

/**
 * WMS 库存「只读」网关
 *
 * <p>ERP 不再直接查询 WMS 的表，改为调用 WMS 提供的只读接口
 * （{@code /rpc-api/wms/inventory-read/**}）。本接口有两种实现，按配置切换：</p>
 * <ul>
 *     <li>{@code txgy.integration.wms.inventory-read.mode=local}（默认）：
 *         {@link ErpLocalWmsInventoryReadGateway}，联调前的过渡实现，直接查 WMS 表；</li>
 *     <li>{@code txgy.integration.wms.inventory-read.mode=rpc}：
 *         {@link ErpRpcWmsInventoryReadGateway}，真实调用 WMS 只读接口。</li>
 * </ul>
 *
 * <p><b>约定</b>：传空集合或 null 表示该维度不限制；编码精确匹配，不做模糊匹配、不去空格。
 * 查询失败必须抛异常，调用方<b>绝不能</b>把失败当成零库存。</p>
 *
 * @author OPENLAB BS
 */
public interface ErpWmsInventoryReadGateway {

    /**
     * 查询实物库存快照（粒度为「物料下的 SKU × 仓库」）
     *
     * @param materialCodes  WMS 物料编码集合，空表示不限制
     * @param warehouseCodes WMS 仓库编码集合，空表示不限制
     * @return 库存快照；存在的 SKU × 仓库组合即使没有库存记录也会返回，数量为 0
     */
    List<ErpWmsInventorySnapshotBO> getInventorySnapshot(Collection<String> materialCodes,
                                                         Collection<String> warehouseCodes);

    /**
     * 查询在 WMS 侧实际存在的仓库编码
     *
     * @param warehouseCodes 待校验的仓库编码集合，空表示取全部
     * @return 实际存在的仓库编码（未知编码会被忽略）
     */
    List<String> getExistingWarehouseCodes(Collection<String> warehouseCodes);

    /**
     * 查询在 WMS 侧实际存在的物料编码
     *
     * @param materialCodes 待校验的物料编码集合，空表示取全部
     * @return 实际存在的物料编码（未知编码会被忽略）
     */
    List<String> getExistingMaterialCodes(Collection<String> materialCodes);

}
