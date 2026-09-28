package cn.iocoder.txgy.module.erp.api.wms.dto;

import lombok.Data;

/**
 * WMS 库存事务推送的响应 DTO（方案A）
 *
 * @author OPENLAB BS
 */
@Data
public class ErpWmsInventoryTransactionRespDTO {

    /**
     * 是否已被 ERP 接收处理
     */
    private Boolean accepted;

    /**
     * ERP 生成的单据类型：STOCK_IN（其它入库单）/ STOCK_OUT（其它出库单）/ MIXED（盘盈盘亏各生成一张）
     */
    private String erpDocumentType;

    /**
     * ERP 生成的单号，多个用英文逗号分隔
     */
    private String erpDocumentNo;

    /**
     * 处理说明（幂等命中 / 成功 / 失败原因）
     */
    private String message;

}
