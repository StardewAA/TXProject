package cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售绩效汇总 Response VO（销售员维度）
 *
 * <p>把三条口径汇总到同一个销售员身上：</p>
 * <ol>
 *     <li><b>出入库</b>：来自库存流水的责任人快照（source_user_id），按「销售员 × 产品」聚合后上卷</li>
 *     <li><b>订单</b>：该销售员名下<b>已审批</b>的销售订单金额（按 order_time 落在期间内）</li>
 *     <li><b>回款</b>：回款单明细经订单归因到销售员（按 receipt_time 落在期间内）</li>
 * </ol>
 *
 * <p>应收余额 = 订单金额 − 回款金额。为负数表示回款超过期间内登记的订单金额
 * （例如预收货款、或订单落在期间外而回款落在期间内），属正常业务情形，不做截断。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 销售绩效汇总（销售员维度） Response VO")
@Data
public class ErpSalesPerformanceSummaryRespVO {

    @Schema(description = "销售员用户编号", example = "80000")
    private Long userId;

    @Schema(description = "销售员姓名", example = "张伟")
    private String userName;

    @Schema(description = "销售出库数量", example = "200")
    private BigDecimal outCount;

    @Schema(description = "销售退货数量", example = "15")
    private BigDecimal returnCount;

    @Schema(description = "净销售数量 = 出库 - 退货", example = "185")
    private BigDecimal netCount;

    @Schema(description = "销售出库金额", example = "112000")
    private BigDecimal outAmount;

    @Schema(description = "销售退货金额", example = "8400")
    private BigDecimal returnAmount;

    @Schema(description = "净销售金额 = 出库金额 - 退货金额", example = "103600")
    private BigDecimal netAmount;

    @Schema(description = "名下已审批销售订单金额", example = "5120000")
    private BigDecimal orderAmount;

    @Schema(description = "名下已审批销售订单数", example = "3")
    private Long orderCount;

    @Schema(description = "回款金额（经订单归因）", example = "1466000")
    private BigDecimal receiptAmount;

    @Schema(description = "回款单数", example = "3")
    private Long receiptCount;

    @Schema(description = "应收余额 = 订单金额 - 回款金额；为负表示预收", example = "3654000")
    private BigDecimal unreceiptedAmount;

    @Schema(description = "回款率(%) = 回款金额 / 订单金额；订单金额为 0 时为空", example = "28.63")
    private BigDecimal receiptRate;

}
