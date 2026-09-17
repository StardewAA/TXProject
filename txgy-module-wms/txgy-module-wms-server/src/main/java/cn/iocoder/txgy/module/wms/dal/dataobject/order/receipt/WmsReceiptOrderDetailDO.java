package cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * WMS 入库单明细 DO
 *
 * @author OPENLAB BS
 */
@TableName(value = "wms_receipt_order_detail", autoResultMap = true)
@KeySequence("wms_receipt_order_detail_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsReceiptOrderDetailDO extends BaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
    // ========= 单据商品字段 =========

    /**
     * 入库单编号
     *
     * 关联 {@link WmsReceiptOrderDO#getId()}
     */
    private Long orderId;
    /** 来源单据明细行号。 */
    private Integer sourceLineNo;
    /** 创建时收到的来源明细 JSON；仅插入，后续编辑不得覆盖。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String sourceSnapshotJson;
    /**
     * 商品 SKU 编号
     *
     * 关联 {@link WmsItemSkuDO#getId()}
     */
    private Long skuId;

    // ========= 仓库字段 =========

    /**
     * 仓库编号
     *
     * 关联 {@link WmsWarehouseDO#getId()}
     */
    private Long warehouseId;

    // ========= 数量金额字段 =========

    /**
     * 入库数量
     */
    private BigDecimal quantity;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private BigDecimal actualQuantity;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private BigDecimal qualifiedQuantity;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private BigDecimal unqualifiedQuantity;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.List<String> qualityImages;
    private String qualityRemark;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Boolean qualityRequiredSnapshot;
    /**
     * 单价
     */
    private BigDecimal price;
    /**
     * 行金额（数量 * 单价）
     */
    private BigDecimal totalPrice;

}
