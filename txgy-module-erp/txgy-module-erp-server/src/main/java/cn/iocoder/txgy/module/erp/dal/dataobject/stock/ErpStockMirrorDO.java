package cn.iocoder.txgy.module.erp.dal.dataobject.stock;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ERP 实物库存镜像 DO
 *
 * <p>WMS 是实物库存唯一权威，ERP 不持有实物库存。本表是 WMS 库存的只读镜像，
 * 与业务账 {@link ErpStockDO} 构成双轨库存：</p>
 * <ul>
 *     <li>业务账 erp_stock —— 由 ERP 出入库单据推演，用于算销售/成本/绩效</li>
 *     <li>实物账 erp_stock_mirror —— 同步自 WMS，用于看真实可发库存、做对账</li>
 * </ul>
 *
 * <p>本表只由同步任务写入，业务代码不得直接修改。</p>
 *
 * @author OPENLAB BS
 */
@TableName("erp_stock_mirror")
@KeySequence("erp_stock_mirror_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpStockMirrorDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * ERP 产品编号
     *
     * 关联 {@link cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO#getId()}
     */
    private Long productId;
    /**
     * ERP 仓库编号
     *
     * 关联 {@link ErpWarehouseDO#getId()}
     */
    private Long warehouseId;
    /**
     * 实物数量（同步自 WMS）
     */
    private BigDecimal count;
    /**
     * WMS SKU 编号
     *
     * 映射结果，便于排查；映射链见 sql/2026-09-18-erp-stock-mirror.sql
     */
    private Long wmsSkuId;
    /**
     * WMS 仓库编号
     */
    private Long wmsWarehouseId;
    /**
     * 最近同步时间
     */
    private LocalDateTime syncTime;

}
