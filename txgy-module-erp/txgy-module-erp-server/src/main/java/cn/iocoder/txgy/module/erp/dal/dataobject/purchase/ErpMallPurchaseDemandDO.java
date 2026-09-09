package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * Mall shortage procurement demand. This is deliberately separate from an
 * ERP supplier purchase order: accepting a mall demand does not bypass ERP's
 * supplier selection and approval workflow.
 */
@TableName("erp_mall_purchase_demand")
@KeySequence("erp_mall_purchase_demand_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpMallPurchaseDemandDO extends BaseDO {

    @TableId
    private Long id;
    private String requestId;
    private String requestHash;
    private String mallDemandNo;
    private String orderNo;
    private String shopOrderNo;
    private Long enterpriseId;
    private Long shopId;
    private Long warehouseId;
    private String erpRequestNo;
    private String erpPurchaseOrderNo;
    private String status;
    private String reasonCode;
    private String reasonMessage;
    private LocalDate expectedArrivalDate;
    private LocalDate requiredDeliveryDate;
    private String contactRequirement;
    private String technicalRequirement;
    private String attachmentUrls;
    private String cancelReason;

}
