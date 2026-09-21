package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 询价横向比价 Response VO（物料维度）
 *
 * <p>与 {@link ErpInquiryRespVO} 的区别：后者是<b>报价单维度</b>（一家供应商一条，含其全部明细），
 * 本 VO 是<b>物料维度</b>——同一物料下把所有供应商的报价并列铺开，
 * 并给出加权评分、排名与推荐，解决「比价靠肉眼挑下拉框」的问题。</p>
 *
 * <p><b>加权评分</b>：价格 40 + 交期 25 + 账期 20（权重可按需调整）。
 * 某项数据缺失时其权重会被剔除并重新归一化，不会因为缺数据而压低总分。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - ERP 询价横向比价 Response VO")
@Data
public class ErpInquiryComparisonRespVO {

    @Schema(description = "询价单编号", example = "1")
    private Long inquiryId;

    @Schema(description = "询价单号", example = "CGXJ20260909000001")
    private String inquiryNo;

    @Schema(description = "询价标题")
    private String title;

    @Schema(description = "参与的供应商数量", example = "3")
    private Integer supplierCount;

    @Schema(description = "物料比价明细")
    private List<Material> materials;

    @Schema(description = "管理后台 - 物料维度比价")
    @Data
    public static class Material {

        @Schema(description = "询价明细编号", example = "10")
        private Long inquiryItemId;

        @Schema(description = "产品编号", example = "9")
        private Long productId;

        @Schema(description = "产品编码", example = "CP-1001")
        private String productCode;

        @Schema(description = "产品名称", example = "环氧富锌底漆")
        private String productName;

        @Schema(description = "单位名称", example = "组")
        private String productUnitName;

        @Schema(description = "询价数量", example = "200")
        private BigDecimal count;

        @Schema(description = "最低未税单价", example = "552")
        private BigDecimal lowestPrice;

        @Schema(description = "平均未税单价", example = "560")
        private BigDecimal averagePrice;

        @Schema(description = "中位未税单价", example = "560")
        private BigDecimal medianPrice;

        @Schema(description = "最高未税单价", example = "568")
        private BigDecimal highestPrice;

        @Schema(description = "最优推荐 = 总分最高的报价项编号")
        private Long recommendedQuoteItemId;

        @Schema(description = "最优推荐供应商编号")
        private Long recommendedSupplierId;

        @Schema(description = "最优推荐供应商名称")
        private String recommendedSupplierName;

        @Schema(description = "最优推荐总分", example = "96.50")
        private BigDecimal recommendedScore;

        @Schema(description = "是否已选标（该物料已有中标报价）", example = "false")
        private Boolean awarded;

        @Schema(description = "各供应商报价（按总分降序）")
        private List<Quote> quotes;

    }

    @Schema(description = "管理后台 - 单家供应商对某物料的报价")
    @Data
    public static class Quote {

        @Schema(description = "报价明细编号", example = "100")
        private Long quoteItemId;

        @Schema(description = "报价单编号", example = "20")
        private Long quoteId;

        @Schema(description = "报价单号")
        private String quoteNo;

        @Schema(description = "供应商编号", example = "1")
        private Long supplierId;

        @Schema(description = "供应商名称")
        private String supplierName;

        @Schema(description = "报价数量", example = "200")
        private BigDecimal count;

        @Schema(description = "未税单价", example = "552")
        private BigDecimal productPrice;

        @Schema(description = "税率(%)", example = "13")
        private BigDecimal taxPercent;

        @Schema(description = "运费", example = "0")
        private BigDecimal freightPrice;

        @Schema(description = "含税总价", example = "124752")
        private BigDecimal totalPrice;

        @Schema(description = "交期")
        private LocalDateTime deliveryDate;

        @Schema(description = "账期")
        private String paymentTerm;

        @Schema(description = "是否已中标", example = "false")
        private Boolean awarded;

        @Schema(description = "价格得分（0-100）", example = "100.00")
        private BigDecimal priceScore;

        @Schema(description = "交期得分（0-100）", example = "100.00")
        private BigDecimal deliveryScore;

        @Schema(description = "账期得分（0-100）", example = "60.00")
        private BigDecimal paymentScore;

        @Schema(description = "加权总分（0-100）", example = "96.50")
        private BigDecimal totalScore;

        @Schema(description = "名次（1 为最优）", example = "1")
        private Integer rank;

        @Schema(description = "是否最低价", example = "true")
        private Boolean lowestPrice;

        @Schema(description = "是否系统推荐", example = "true")
        private Boolean recommended;

        @Schema(description = "价格异常标记：HIGH 偏高 / LOW 偏低 / null 正常", example = "null")
        private String priceAnomaly;

        @Schema(description = "评分说明（各项权重与取值，便于人工复核）")
        private String scoreDetail;

    }

}
