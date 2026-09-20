package cn.iocoder.txgy.module.erp.dal.dataobject.stock;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存出库来源构成（只读查询投影）
 *
 * <p>回答「仓库里多少是 ERP 销售的」：按来源系统 + 业务类型分组统计库存变动量。
 * 只有 sourceSystem = "ERP" 且 bizType = 50(销售出库) 才是 ERP 的销售出库；
 * 商城发货、MES 领料、WMS 手工调整都不计入。</p>
 *
 * @author OPENLAB BS
 */
@Data
public class ErpStockSourceBreakdownDO {

    /**
     * 来源系统：ERP / MALL / MES / MANUAL
     */
    private String sourceSystem;
    /**
     * 业务类型（正数入库、负数出库，见 ErpStockRecordBizTypeEnum）
     */
    private Integer bizType;
    /**
     * 变动量合计（带符号：正=入库，负=出库）
     */
    private BigDecimal totalCount;
    /**
     * 流水条数
     */
    private Integer recordCount;

}
