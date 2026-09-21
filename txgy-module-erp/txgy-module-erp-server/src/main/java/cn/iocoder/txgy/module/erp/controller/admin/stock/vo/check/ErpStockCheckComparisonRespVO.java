package cn.iocoder.txgy.module.erp.controller.admin.stock.vo.check;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 盘点单三方比对 Response VO
 *
 * <p>把同一条盘点明细上的三本账摆在一起：</p>
 * <ol>
 *     <li><b>ERP 账面</b>（{@code bookCount}）：比较时刻 {@code erp_stock} 的实时库存</li>
 *     <li><b>ERP 实盘</b>（{@code actualCount}）：盘点单上的实盘数，ERP 侧据此做盈亏调整</li>
 *     <li><b>WMS 实物</b>（{@code wmsCount}）：{@code erp_stock_mirror} 镜像，即 WMS 的实物账</li>
 * </ol>
 *
 * <p>三个差异各有用途：</p>
 * <ul>
 *     <li>{@code checkDiff} = 实盘 − 账面快照 —— 盘点盈亏，ERP 侧调整依据</li>
 *     <li>{@code bookDrift} = 当前账面 − 账面快照 —— 盘点期间账面发生过变动，
 *         非 0 表示盘点结果可能已经过期，需要重新盘点或按变动修正</li>
 *     <li>{@code wmsVsActual} = WMS 实物 − ERP 实盘 —— <b>WMS 侧需要调整的量</b>，
 *         即「WMS 执行调整」的输入；为 null 表示尚无 WMS 镜像数据</li>
 * </ul>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - ERP 盘点单三方比对 Response VO")
@Data
public class ErpStockCheckComparisonRespVO {

    @Schema(description = "盘点单编号", example = "16")
    private Long checkId;

    @Schema(description = "盘点单号", example = "QCPD20260907000001")
    private String checkNo;

    @Schema(description = "盘点时间")
    private LocalDateTime checkTime;

    @Schema(description = "盘点单状态", example = "20")
    private Integer status;

    @Schema(description = "盘点行数", example = "1")
    private Integer totalLines;

    @Schema(description = "存在盘点盈亏的行数（实盘 ≠ 账面快照）", example = "1")
    private Integer checkDiffLineCount;

    @Schema(description = "有 WMS 镜像数据的行数", example = "0")
    private Integer mirrorCoveredLineCount;

    @Schema(description = "WMS 需要调整的行数（WMS 实物 ≠ ERP 实盘）", example = "0")
    private Integer wmsAdjustLineCount;

    @Schema(description = "账面在盘点期间发生变动的行数", example = "0")
    private Integer bookDriftLineCount;

    @Schema(description = "是否至少有一行具备 WMS 实物数据；为 false 时三方降级为两方", example = "false")
    private Boolean mirrorAvailable;

    @Schema(description = "三方比对明细")
    private List<Line> lines;

    @Schema(description = "管理后台 - ERP 盘点单三方比对明细行")
    @Data
    public static class Line {

        @Schema(description = "盘点项编号", example = "18")
        private Long itemId;

        @Schema(description = "产品编号", example = "5")
        private Long productId;

        @Schema(description = "产品编码", example = "ERP-MAT-000000000005")
        private String productCode;

        @Schema(description = "产品名称")
        private String productName;

        @Schema(description = "产品单位")
        private String productUnitName;

        @Schema(description = "仓库编号", example = "6")
        private Long warehouseId;

        @Schema(description = "仓库名称", example = "演示-原材料仓")
        private String warehouseName;

        @Schema(description = "仓库对应的 WMS 仓库编码；为空表示该仓库尚未映射到 WMS", example = "WH01")
        private String wmsWarehouseCode;

        @Schema(description = "① ERP 账面（实时）", example = "0")
        private BigDecimal bookCount;

        @Schema(description = "① ERP 账面（盘点单上的快照）", example = "0")
        private BigDecimal snapshotBookCount;

        @Schema(description = "② ERP 实盘", example = "25")
        private BigDecimal actualCount;

        @Schema(description = "③ WMS 实物；null 表示无镜像数据", example = "25")
        private BigDecimal wmsCount;

        @Schema(description = "WMS 镜像的同步时间；null 表示无镜像数据")
        private LocalDateTime wmsSyncTime;

        @Schema(description = "盘点盈亏 = 实盘 − 账面快照", example = "25")
        private BigDecimal checkDiff;

        @Schema(description = "盘点期间账面变动 = 当前账面 − 账面快照", example = "0")
        private BigDecimal bookDrift;

        @Schema(description = "WMS 需调整量 = WMS 实物 − ERP 实盘；null 表示无镜像数据", example = "0")
        private BigDecimal wmsVsActual;

        @Schema(description = "账实差 = WMS 实物 − ERP 账面；null 表示无镜像数据", example = "0")
        private BigDecimal wmsVsBook;

        @Schema(description = "该行是否有 WMS 镜像数据", example = "false")
        private Boolean mirrorAvailable;

        @Schema(description = "该行 WMS 是否需要调整", example = "false")
        private Boolean wmsAdjustRequired;

        @Schema(description = "该行账面在盘点期间是否发生变动", example = "false")
        private Boolean bookDriftDetected;

        @Schema(description = "行备注")
        private String remark;
    }

}
