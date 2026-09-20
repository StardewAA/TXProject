package cn.iocoder.txgy.module.erp.controller.admin.recognition.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ERP 采购订单 Excel 导入行。
 *
 * <p>一行代表采购订单中的一条产品明细；订单头字段可只填写在第一行，
 * 后续明细行会自动沿用上一行的供应商、日期和外部单号。</p>
 */
@Data
public class ErpPurchaseOrderImportExcelVO {

    @ExcelProperty("供应商名称")
    private String supplierName;

    @ExcelProperty("订单日期")
    private String documentDate;

    @ExcelProperty("外部单号")
    private String documentNo;

    @ExcelProperty("结算账户")
    private String accountName;

    @ExcelProperty("产品编码")
    private String productCode;

    @ExcelProperty("产品名称")
    private String productName;

    @ExcelProperty("数量")
    private BigDecimal count;

    @ExcelProperty("单价")
    private BigDecimal productPrice;

    @ExcelProperty("税率(%)")
    private String taxPercent;

    @ExcelProperty("备注")
    private String remark;

}
