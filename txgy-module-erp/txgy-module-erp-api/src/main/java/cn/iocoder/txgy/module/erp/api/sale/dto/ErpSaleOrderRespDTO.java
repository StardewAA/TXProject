package cn.iocoder.txgy.module.erp.api.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单 Response DTO
 *
 * 供 MES 等下游模块消费的只读视图，客户名称、销售员姓名已提前组装。
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 销售订单 Response DTO")
@Data
public class ErpSaleOrderRespDTO {

    @Schema(description = "销售订单编号", example = "1")
    private Long id;

    @Schema(description = "销售订单号", example = "XSDD20260910001")
    private String no;

    @Schema(description = "销售状态", example = "20")
    private Integer status;

    @Schema(description = "客户编号", example = "1")
    private Long customerId;

    @Schema(description = "客户名称", example = "XX管业有限公司")
    private String customerName;

    @Schema(description = "销售员用户编号", example = "100")
    private Long saleUserId;

    @Schema(description = "销售员姓名", example = "张三")
    private String saleUserName;

    @Schema(description = "下单时间")
    private LocalDateTime orderTime;

    @Schema(description = "合计数量", example = "1000")
    private BigDecimal totalCount;

    @Schema(description = "最终合计价格，单位：元", example = "88000.00")
    private BigDecimal totalPrice;

    @Schema(description = "备注")
    private String remark;

}
