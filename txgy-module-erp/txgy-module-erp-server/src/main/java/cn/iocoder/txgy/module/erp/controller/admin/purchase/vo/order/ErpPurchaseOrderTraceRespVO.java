package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 采购订单溯源 Response VO
 *
 * <p>把「询价 → 供应商报价 → 定标 → 采购订单 → 采购入库」整条链一次返回，
 * 解决原实现「溯源字段写进去却没有任何查看入口」的问题。</p>
 *
 * <p>溯源依赖三组数据：</p>
 * <ul>
 *     <li>订单自身的 {@code sourceType / sourceId / sourceNo / businessChainNo}（由定标确认时写入）</li>
 *     <li>询价单与中标报价明细（{@code erp_inquiry_quote_item.awarded}）</li>
 *     <li>该订单下的入库单（继承同一 {@code businessChainNo}）</li>
 * </ul>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - ERP 采购订单溯源 Response VO")
@Data
public class ErpPurchaseOrderTraceRespVO {

    @Schema(description = "是否已建立溯源（订单有来源字段且有来源单号）", example = "true")
    private Boolean traceable;

    @Schema(description = "不可溯源时的原因说明", example = "该订单为手工创建，未关联询价/定标数据")
    private String traceHint;

    @Schema(description = "订单信息")
    private Order order;

    @Schema(description = "来源询价单（可能为空）")
    private Inquiry inquiry;

    @Schema(description = "中标明细（来自询价报价明细的中标行）")
    private List<AwardItem> awardItems;

    @Schema(description = "该订单下的采购入库单")
    private List<InDoc> inDocs;

    @Schema(description = "管理后台 - 订单信息")
    @Data
    public static class Order {

        @Schema(description = "订单编号", example = "30")
        private Long id;

        @Schema(description = "订单单号", example = "CGDD-2026-0001")
        private String no;

        @Schema(description = "业务链号", example = "INQUIRY-CGXJ20260909000001")
        private String businessChainNo;

        @Schema(description = "来源类型", example = "AWARD_RESULT")
        private String sourceType;

        @Schema(description = "来源单号（询价单号）", example = "CGXJ20260909000001")
        private String sourceNo;

        @Schema(description = "供应商名称")
        private String supplierName;

        @Schema(description = "订单时间")
        private LocalDateTime orderTime;

        @Schema(description = "未税金额", example = "271600")
        private BigDecimal totalProductPrice;

        @Schema(description = "含税金额", example = "306908")
        private BigDecimal totalPrice;

        @Schema(description = "订单状态", example = "20")
        private Integer status;

        @Schema(description = "已入库数量", example = "470")
        private BigDecimal inCount;

        @Schema(description = "已退货数量", example = "15")
        private BigDecimal returnCount;

    }

    @Schema(description = "管理后台 - 来源询价单")
    @Data
    public static class Inquiry {

        @Schema(description = "询价单编号", example = "1")
        private Long id;

        @Schema(description = "询价单号", example = "CGXJ20260909000001")
        private String no;

        @Schema(description = "询价标题")
        private String title;

        @Schema(description = "询价审批状态", example = "20")
        private Integer status;

        @Schema(description = "定标审批状态", example = "20")
        private Integer awardStatus;

        @Schema(description = "有效报价数量", example = "5")
        private Integer quoteCount;

        @Schema(description = "该询价已生成的采购订单号集合", example = "CGDD-2026-0001")
        private String purchaseOrderIds;

    }

    @Schema(description = "管理后台 - 中标明细")
    @Data
    public static class AwardItem {

        @Schema(description = "产品编码", example = "CP-1001")
        private String productCode;

        @Schema(description = "产品名称", example = "环氧富锌底漆")
        private String productName;

        @Schema(description = "中标供应商名称")
        private String supplierName;

        @Schema(description = "中标数量", example = "200")
        private BigDecimal awardCount;

        @Schema(description = "未税单价", example = "560")
        private BigDecimal productPrice;

        @Schema(description = "推荐原因")
        private String awardReason;

    }

    @Schema(description = "管理后台 - 采购入库单")
    @Data
    public static class InDoc {

        @Schema(description = "入库单编号", example = "22")
        private Long id;

        @Schema(description = "入库单号", example = "CGRK-2026-0001")
        private String no;

        @Schema(description = "入库时间")
        private LocalDateTime inTime;

        @Schema(description = "含税金额", example = "173600")
        private BigDecimal totalPrice;

        @Schema(description = "单据状态", example = "20")
        private Integer status;

        @Schema(description = "业务链号（应与订单一致）", example = "INQUIRY-CGXJ20260909000001")
        private String businessChainNo;

        @Schema(description = "WMS 处理状态", example = "COMPLETED")
        private String wmsStatus;

    }

}
