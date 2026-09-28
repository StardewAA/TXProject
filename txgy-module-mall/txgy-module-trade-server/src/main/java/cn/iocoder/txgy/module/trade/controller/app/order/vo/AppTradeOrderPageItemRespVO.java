package cn.iocoder.txgy.module.trade.controller.app.order.vo;

import cn.iocoder.txgy.module.trade.controller.app.order.vo.item.AppTradeOrderItemRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 订单交易的分页项 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppTradeOrderPageItemRespVO extends AppTradeOrderPaymentRespVO {

    @Schema(description = "订单店铺编号；平台自营或多店铺订单为 0，订单项保留实际店铺编号。", example = "1")
    private Long shopId;

    @Schema(description = "订单店铺名称快照；多店铺订单为“多店铺”", example = "测试1111")
    private String shopName;

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "订单流水号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1146347329394184195")
    private String no;

    @Schema(description = "订单类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer type;

    @Schema(description = "订单业务来源：NORMAL 普通下单，INQUIRY 询价成交", example = "INQUIRY")
    private String sourceType;

    @Schema(description = "询价单编号", example = "1024")
    private Long inquiryId;

    @Schema(description = "已采纳报价编号", example = "2048")
    private Long inquiryQuoteId;

    @Schema(description = "成交时报价快照版本", example = "1")
    private Long inquiryQuoteVersion;

    @Schema(description = "采购企业编号；仅询价成交订单有值", example = "1001")
    private Long buyerEnterpriseId;

    @Schema(description = "当前登录会员是否为实际下单人；企业协作订单用于限制支付、评价和个人售后", example = "true")
    private Boolean createdByCurrentMember;

    @Schema(description = "订单状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "购买的商品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer productCount;

    @Schema(description = "是否评价", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean commentStatus;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    // ========== 价格 + 支付基本信息 ==========

    @Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long payOrderId;

    @Schema(description = "应付金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    private Integer payPrice;

    // ========== 收件 + 物流基本信息 ==========

    @Schema(description = "配送方式", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer deliveryType;

    /**
     * 订单项数组
     */
    private List<AppTradeOrderItemRespVO> items;

    // ========== 营销基本信息 ==========

    @Schema(description = "拼团记录编号", example = "100")
    private Long combinationRecordId;

}
