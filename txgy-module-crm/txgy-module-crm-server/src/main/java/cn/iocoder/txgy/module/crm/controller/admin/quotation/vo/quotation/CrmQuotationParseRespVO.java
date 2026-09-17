package cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - CRM 报价单文本智能解析 Response VO")
@Data
public class CrmQuotationParseRespVO {

    @Schema(description = "客户名称", example = "中石化四公司")
    private String customerName;

    @Schema(description = "联系人", example = "王经理")
    private String contactName;

    @Schema(description = "联系电话", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "wang@example.com")
    private String email;

    @Schema(description = "地址", example = "北京朝阳区xx路 123 号")
    private String address;

    @Schema(description = "QQ", example = "1234567")
    private String qq;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "识别到的产品清单")
    private List<Product> products = new ArrayList<>();

    @Schema(description = "产品项")
    @Data
    public static class Product {

        @Schema(description = "产品名称", example = "3PE 防腐钢管")
        private String productName;

        @Schema(description = "数量", example = "1000")
        private BigDecimal count;

        @Schema(description = "单位", example = "米")
        private String unit;

        @Schema(description = "单价", example = "86.00")
        private BigDecimal price;

    }

}
