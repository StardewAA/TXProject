package cn.iocoder.txgy.module.erp.controller.admin.recognition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - ERP 图片识别人工修正 Request VO")
@Data
public class ErpRecognitionResultCorrectReqVO {

    @NotNull(message = "任务编号不能为空")
    private Long taskId;
    @NotNull(message = "识别结果版本不能为空")
    private Integer resultVersion;
    private String documentNo;
    private String documentDate;
    private Long supplierId;
    private String supplierName;
    private Long customerId;
    private String customerName;
    private Long saleUserId;
    private String orderNo;
    private String returnTime;
    private Long accountId;
    private BigDecimal discountPercent;
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
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        private String productName;
        private String productCode;
        @NotNull(message = "请选择产品")
        private Long productId;
        private Long orderItemId;
        private Long warehouseId;
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "数量必须大于 0")
        private BigDecimal count;
        private BigDecimal productPrice;
        private BigDecimal taxPercent;
        private String remark;
    }

}
