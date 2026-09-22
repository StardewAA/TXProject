package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台 - WMS AI 确认建单 Request VO
 *
 * <p>对应前端「确认建单」按钮：用户已在确认卡片上核对/改选了仓库、供应商与明细，
 * 此处提交的是**已确认**的数据，后端据此调用既有建单服务落库。
 *
 * @author 天信管业
 */
@Schema(description = "管理后台 - WMS AI 确认建单 Request VO")
@Data
public class WmsAiCreateOrderReqVO {

    @Schema(description = "会话标识（沿用识别时的 sessionId）", example = "abc-123")
    private String sessionId;

    @Schema(description = "单据动作：IN 入库 / OUT 出库 / MOVE 移库 / CHECK 盘库",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "IN")
    @NotEmpty(message = "单据动作不能为空")
    private String action;

    @Schema(description = "单据子类型（入库 100-103、出库 200-203）；为空时按动作取默认值",
            example = "101")
    private Integer type;

    @Schema(description = "仓库编号。语义随动作变化：入库=目标仓、出库=来源仓、移库=移出仓、盘库=盘库仓",
            example = "1")
    private Long warehouseId;

    @Schema(description = "移库目标仓库编号（仅 MOVE 移库需要，其余动作忽略）", example = "2")
    private Long targetWarehouseId;

    @Schema(description = "往来企业编号（入库=供应商、出库=客户；移库/盘库忽略）", example = "1")
    private Long merchantId;

    @Schema(description = "备注", example = "AI 助手建单")
    private String remark;

    @Schema(description = "商品明细（已确认）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<Item> items;

    /**
     * 已确认的商品明细行
     */
    @Schema(description = "已确认的商品明细行")
    @Data
    public static class Item {

        @Schema(description = "商品名称（展示用）", example = "玄武岩矿料")
        private String name;

        @Schema(description = "规格（展示用）", example = "20kg/袋")
        private String spec;

        @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @NotNull(message = "明细数量不能为空")
        private BigDecimal quantity;

        @Schema(description = "单位", example = "吨")
        private String unit;

        @Schema(description = "单价", example = "100.00")
        private BigDecimal price;

        @Schema(description = "重量", example = "100.00")
        private BigDecimal weight;

        @Schema(description = "是否已匹配到主数据", example = "true")
        private Boolean resolved;

        @Schema(description = "商品编号", example = "3207003")
        private Long itemId;

        @Schema(description = "商品 SKU 编号（建单依赖，缺失时由 itemId 兜底解析）", example = "3201703")
        private Long skuId;

    }

}
