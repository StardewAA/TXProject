package cn.iocoder.txgy.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 订单店铺分组；支付和履约状态仍由主订单管理")
@Data
public class TradeOrderShopRespVO {
    @Schema(description = "店铺编号，0 为平台自营")
    private Long shopId;
    private String shopName;
    private List<Long> itemIds;
    private Integer productCount;
    @Schema(description = "商品原价合计，单位分")
    private Integer totalPrice;
    @Schema(description = "已分摊的运费，单位分")
    private Integer deliveryPrice;
    @Schema(description = "已分摊的实付金额，单位分；退款前金额")
    private Integer payPrice;
}
