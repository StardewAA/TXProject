package cn.iocoder.txgy.module.erp.service.recognition.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购域图片识别的统一结果。
 *
 * <p>不同业务类型只使用其中一部分字段，保留统一结构便于人工修正和审计。</p>
 */
@Data
public class ErpRecognitionDocument {

    private String documentType;
    private String documentNo;
    private String documentDate;
    private String orderNo;
    private String returnTime;
    private Long accountId;
    private String accountName;
    private BigDecimal discountPercent;
    private String supplierName;
    private Long supplierId;
    private String customerName;
    private Long customerId;
    private Long saleUserId;
    private String contact;
    private String mobile;
    private String telephone;
    private String email;
    private String fax;
    private String taxNo;
    private BigDecimal taxPercent;
    private String bankName;
    private String bankAccount;
    private String bankAddress;
    private String remark;
    private BigDecimal finalConfidence;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {

        private String productName;
        private String productCode;
        private Long productId;
        private Long productUnitId;
        private Long orderItemId;
        private Long warehouseId;
        @JsonAlias("quantity")
        private BigDecimal count;
        @JsonAlias("unitPrice")
        private BigDecimal productPrice;
        @JsonAlias("taxRate")
        private BigDecimal taxPercent;
        private String remark;
        private BigDecimal confidence;
    }

}
