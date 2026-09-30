package cn.iocoder.txgy.module.trade.dal.dataobject.wms;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 商城按“店铺 + WMS 仓库”拆分的出库申请事实记录。 */
@TableName("trade_wms_shipment")
@KeySequence("trade_wms_shipment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeWmsShipmentDO extends BaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long shopId;
    private Long warehouseId;
    private String warehouseCode;
    private String sourceOrderNo;
    private String requestId;
    private String targetOrderNo;
    private String status;
    private Long eventVersion;
    private Integer retryCount;
    private String lastError;
    private String requestJson;
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime lastSyncedAt;
}
