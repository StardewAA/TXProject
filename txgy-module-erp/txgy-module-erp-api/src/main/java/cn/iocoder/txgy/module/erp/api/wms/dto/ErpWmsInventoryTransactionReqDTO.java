package cn.iocoder.txgy.module.erp.api.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * WMS 推送「库存事务单据」给 ERP 的请求 DTO（方案A 的核心契约）
 *
 * <p>WMS 每发生一笔实物异动（入库 / 出库 / 退货 / 盘点调整），调用
 * {@code POST /rpc-api/erp/wms-callback/inventory-transaction}，
 * ERP 收到后自动生成对应的入库单 / 出库单，并自动更新账面库存。</p>
 *
 * <p><b>幂等</b>：以 {@code requestId} 为准（重复推送直接返回成功）；
 * {@code transactionNo + eventVersion} 用于审计与乱序保护。</p>
 *
 * @author OPENLAB BS
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpWmsInventoryTransactionReqDTO extends ErpWmsCallbackBaseReqDTO {

    /**
     * WMS 库存事务号，WMS 侧唯一
     */
    @NotBlank(message = "WMS 库存事务号不能为空")
    private String transactionNo;

    /**
     * 事务类型，取值见 {@link cn.iocoder.txgy.module.erp.enums.wms.ErpWmsTransactionTypeEnum}
     * ：INBOUND 入库 / OUTBOUND 出库 / CHECK_ADJUST 盘点调整
     */
    @NotBlank(message = "事务类型不能为空")
    private String transactionType;

    /**
     * 来源业务类型，取值见 {@link cn.iocoder.txgy.module.erp.enums.wms.ErpWmsTransactionBizTypeEnum}
     * ：MES_PRODUCE_IN / MALL_SALE_OUT / ERP_SALE_OUT / PURCHASE_IN ...，可空
     */
    private String bizType;

    /**
     * ERP 来源单号（如 ERP 销售订单号、采购订单号），可空
     */
    private String sourceOrderNo;

    /**
     * WMS 单据号，可空
     */
    private String wmsOrderNo;

    /**
     * 单据头默认仓库编码，明细未指定仓库时使用
     */
    private String warehouseCode;

    /**
     * 事务明细
     */
    @NotEmpty(message = "库存事务明细不能为空")
    @Valid
    private List<ErpWmsInventoryTransactionItemDTO> items;

    private String remark;

}
