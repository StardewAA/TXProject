package cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation;

import cn.iocoder.txgy.module.crm.framework.operatelog.core.CrmBusinessParseFunction;
import cn.iocoder.txgy.module.crm.framework.operatelog.core.CrmCustomerParseFunction;
import cn.iocoder.txgy.module.crm.framework.operatelog.core.CrmQuotationTypeParseFunction;
import cn.iocoder.txgy.module.crm.framework.operatelog.core.SysAdminUserParseFunction;
import com.mzt.logapi.starter.annotation.DiffLogField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - CRM 报价单创建/更新 Request VO")
@Data
public class CrmQuotationSaveReqVO {

    @Schema(description = "报价编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10430")
    private Long id;

    @Schema(description = "报价类型：1=购销报价 2=采购报价", example = "1")
    @DiffLogField(name = "报价类型", function = CrmQuotationTypeParseFunction.NAME)
    private Integer quotationType;

    @Schema(description = "报价名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "防腐保温工程报价")
    @DiffLogField(name = "报价名称")
    @NotNull(message = "报价名称不能为空")
    private String name;

    @Schema(description = "客户编号", example = "18336")
    @DiffLogField(name = "客户", function = CrmCustomerParseFunction.NAME)
    private Long customerId;

    @Schema(description = "供应商联系人（采购报价）", example = "张三")
    @DiffLogField(name = "供应商联系人")
    @Size(max = 64, message = "供应商联系人长度不能超过 64 个字符")
    private String supplierContact;

    @Schema(description = "供应商联系电话（采购报价）", example = "13800138000")
    @DiffLogField(name = "供应商联系电话")
    @Size(max = 64, message = "供应商联系电话长度不能超过 64 个字符")
    private String supplierPhone;

    @Schema(description = "供应商地址（采购报价）", example = "江苏省苏州市工业园区xx路1号")
    @DiffLogField(name = "供应商地址")
    @Size(max = 255, message = "供应商地址长度不能超过 255 个字符")
    private String supplierAddress;

    @Schema(description = "客户名称(自填时使用)", example = "某新客户")
    @DiffLogField(name = "客户名称(自填)")
    private String customerName;

    @Schema(description = "商机编号", example = "10864")
    @DiffLogField(name = "商机", function = CrmBusinessParseFunction.NAME)
    private Long businessId;

    @Schema(description = "负责人的用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "17144")
    @DiffLogField(name = "负责人", function = SysAdminUserParseFunction.NAME)
    @NotNull(message = "负责人不能为空")
    private Long ownerUserId;

    @Schema(description = "报价日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @DiffLogField(name = "报价日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @NotNull(message = "报价日期不能为空")
    private LocalDateTime orderDate;

    @Schema(description = "有效期至")
    @DiffLogField(name = "有效期至")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime validUntil;

    @Schema(description = "整单折扣", requiredMode = Schema.RequiredMode.REQUIRED, example = "55.00")
    @DiffLogField(name = "整单折扣")
    @NotNull(message = "整单折扣不能为空")
    private BigDecimal discountPercent;

    @Schema(description = "备注", example = "你猜")
    @DiffLogField(name = "备注")
    private String remark;

    @Schema(description = "产品列表")
    private List<Product> products;

    @Schema(description = "产品列表")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {

        @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20529")
        @NotNull(message = "产品编号不能为空")
        private Long productId;

        @Schema(description = "产品单价", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        @NotNull(message = "产品单价不能为空")
        private BigDecimal productPrice;

        @Schema(description = "报价价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        @NotNull(message = "报价价格不能为空")
        private BigDecimal quotationPrice;

        @Schema(description = "产品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "8911")
        @NotNull(message = "产品数量不能为空")
        private Integer count;

    }

}
