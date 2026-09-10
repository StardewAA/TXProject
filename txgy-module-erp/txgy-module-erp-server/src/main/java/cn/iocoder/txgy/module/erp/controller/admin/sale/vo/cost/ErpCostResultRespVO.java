package cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpCostResultRespVO {
    private Long id;
    private String no;
    private String name;
    private String projectCode;
    private Integer costVersion;
    private Integer status;
    private String processInstanceId;
    private Integer businessStatus;
    private Integer sourceType;
    private String currency;
    private BigDecimal totalPrice;
    private Integer mappedCount;
    private Integer itemCount;
    private LocalDateTime receivedAt;
    private Integer convertedType;
    private Long convertedId;
    private String fileUrl;
    private String remark;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private Integer lineNo;
        private String externalMaterialCode;
        private String materialName;
        private String specification;
        private String unitName;
        private Long productId;
        private String productName;
        private Long productUnitId;
        private String productUnitName;
        private Integer mappingStatus;
        private BigDecimal count;
        private BigDecimal unitPrice;
        private BigDecimal taxPercent;
        private BigDecimal totalPrice;
        private String remark;
    }
}
