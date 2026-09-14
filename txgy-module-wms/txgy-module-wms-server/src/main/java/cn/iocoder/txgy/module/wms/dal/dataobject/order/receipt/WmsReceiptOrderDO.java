package cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.enums.DictTypeConstants;
import cn.iocoder.txgy.module.wms.enums.order.WmsReceiptOrderTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WMS 入库单 DO
 *
 * @author OPENLAB BS
 */
@TableName("wms_receipt_order")
@KeySequence("wms_receipt_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsReceiptOrderDO extends BaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
    private Boolean simulation;
    private String notificationStatus;
    private String notificationError;
    private Integer notificationAttempts;
    private LocalDateTime nextNotificationTime;
    /**
     * 入库单号
     */
    private String no;
    /**
     * 入库类型
     *
     * 枚举 {@link WmsReceiptOrderTypeEnum}
     * 字典 {@link DictTypeConstants#RECEIPT_ORDER_TYPE}
     */
    private Integer type;
    /**
     * 单据日期
     */
    private LocalDateTime orderTime;
    /**
     * 入库状态
     *
     * 字典 {@link DictTypeConstants#ORDER_STATUS}
     */
    private Integer status;
    private Integer approvalStatus;
    private String processInstanceId;
    private LocalDateTime approvalTime;
    private String approvalReason;
    private Integer executionStatus;
    private LocalDateTime executionTime;
    private String executionError;
    /**
     * 业务订单号
     */
    private String bizOrderNo;
    /** 集成来源模块；普通手工入库为空。 */
    private String sourceModule;
    /** 跨模块业务链编号。 */
    private String businessChainNo;
    /**
     * 供应商编号
     *
     * 关联 {@link WmsMerchantDO#getId()}
     */
    private Long merchantId;
    /**
     * 备注
     */
    private String remark;

    // ========= 仓库字段 =========

    /**
     * 仓库编号
     *
     * 关联 {@link WmsWarehouseDO#getId()}
     */
    private Long warehouseId;

    // ========= 汇总金额字段 =========

    /**
     * 总数量
     */
    private BigDecimal totalQuantity;
    /**
     * 总金额
     */
    private BigDecimal totalPrice;

}
