package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - WMS AI 智能识别 Response VO")
@Data
public class WmsAiRecognizeRespVO {

    /**
     * 单据动作：IN = 入库，OUT = 出库，MOVE = 移库，UNKNOWN = 无法识别
     */
    @Schema(description = "单据动作：IN 入库 / OUT 出库 / MOVE 移库 / UNKNOWN 无法识别", example = "IN")
    private String action;

    @Schema(description = "动作中文名", example = "采购入库")
    private String actionName;

    @Schema(description = "单据类型（入库 100-103 / 出库 200-202）", example = "101")
    private Integer orderType;

    @Schema(description = "单据类型中文名", example = "采购入库")
    private String orderTypeName;

    /**
     * 入库 / 出库 的目标仓库
     */
    @Schema(description = "入库/出库的目标仓库")
    private AiEntity warehouse;

    /**
     * 移库的来源仓库
     */
    @Schema(description = "移库来源仓库")
    private AiEntity sourceWarehouse;

    /**
     * 移库的目标仓库
     */
    @Schema(description = "移库目标仓库")
    private AiEntity targetWarehouse;

    /**
     * 往来企业：入库=供应商，出库=客户
     */
    @Schema(description = "往来企业（入库=供应商，出库=客户）")
    private AiEntity merchant;

    @Schema(description = "识别到的明细行")
    private List<AiItem> items = new ArrayList<>();

    @Schema(description = "待办/提示信息（缺失字段、未匹配提示等）")
    private List<String> tips = new ArrayList<>();

    @Schema(description = "给用户的自然语言回复（气泡文案）")
    private String reply;

    @Schema(description = "是否已可进入确认预览（action 已识别即有商品行或仓库待选）")
    private Boolean buildable;

    @Schema(description = "是否完全就绪可直接提交（商品+仓库+数量都齐）")
    private Boolean ready;

    @Data
    public static class AiEntity {

        @Schema(description = "是否已唯一命中")
        private Boolean resolved = Boolean.FALSE;

        @Schema(description = "主键编号")
        private Long id;

        @Schema(description = "编码")
        private String code;

        @Schema(description = "展示名称")
        private String name;

        @Schema(description = "商品编号")
        private String itemCode;

        @Schema(description = "商品名称")
        private String itemName;

        @Schema(description = "规格编号")
        private String skuCode;

        @Schema(description = "规格名称")
        private String skuName;

        @Schema(description = "条码")
        private String barCode;

        @Schema(description = "单位")
        private String unit;

        @Schema(description = "参考单价（成本价）")
        private BigDecimal price;

        @Schema(description = "备注/额外说明")
        private String remark;

        @Schema(description = "候选列表（未唯一命中时供前端选择）")
        private List<AiEntity> candidates = new ArrayList<>();

    }

    @Data
    public static class AiItem {

        @Schema(description = "商品 SKU 实体")
        private AiEntity sku;

        @Schema(description = "数量")
        private BigDecimal quantity;

        @Schema(description = "单价（默认取成本价，可改）")
        private BigDecimal price;

        @Schema(description = "备注")
        private String remark;

    }

}
