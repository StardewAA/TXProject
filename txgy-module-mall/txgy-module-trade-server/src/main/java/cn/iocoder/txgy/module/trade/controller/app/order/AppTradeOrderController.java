package cn.iocoder.txgy.module.trade.controller.app.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.*;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.item.AppTradeOrderItemCommentCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.item.AppTradeOrderItemRespVO;
import cn.iocoder.txgy.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutOrderService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderPaymentDeadlineService;
import cn.iocoder.txgy.module.trade.service.order.PlatformBuyerOrderQueryService;
import cn.iocoder.txgy.module.trade.service.price.TradePriceService;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;

@Tag(name = "用户 App - 交易订单")
@RestController
@RequestMapping("/trade/order")
@Validated
@Slf4j
public class AppTradeOrderController {
    @Resource
    private cn.iocoder.txgy.module.trade.service.erp.TradeErpInventoryService erpInventory;

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private PlatformCheckoutOrderService platformCheckoutOrderService;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private PlatformBuyerOrderQueryService platformBuyerOrders;
    @Resource
    private DeliveryExpressService deliveryExpressService;
    @Resource
    private AfterSaleService afterSaleService;
    @Resource
    private TradePriceService priceService;

    @Resource
    private TradeOrderProperties tradeOrderProperties;
    @Resource
    private TradeOrderPaymentDeadlineService paymentDeadline;
    @Resource
    private cn.iocoder.txgy.module.trade.service.delivery.TradeOrderLogisticsService logistics;
    @Resource
    private ProductShopApi productShopApi;

    @GetMapping("/settlement")
    @Operation(summary = "获得订单结算信息")
    public CommonResult<AppTradeOrderSettlementRespVO> settlementOrder(@Valid AppTradeOrderSettlementReqVO settlementReqVO) {
        return success(tradeOrderUpdateService.settlementOrder(getLoginUserId(), settlementReqVO));
    }

    @GetMapping("/settlement-product")
    @Operation(summary = "获得商品结算信息", description = "用于商品列表、商品详情，获得参与活动后的价格信息")
    @Parameter(name = "spuIds", description = "商品 SPU 编号数组")
    @PermitAll
    public CommonResult<List<AppTradeProductSettlementRespVO>> settlementProduct(@RequestParam("spuIds") List<Long> spuIds) {
        return success(priceService.calculateProductPrice(getLoginUserId(), spuIds));
    }

    @PostMapping("/create")
    @Operation(summary = "创建订单")
    public CommonResult<AppTradeOrderCreateRespVO> createOrder(@Valid @RequestBody AppTradeOrderCreateReqVO createReqVO) {
        TradeOrderDO order = tradeOrderUpdateService.createOrder(getLoginUserId(), createReqVO);
        return success(new AppTradeOrderCreateRespVO().setId(order.getId()).setPayOrderId(order.getPayOrderId()));
    }

    @GetMapping("/inquiry-settlement")
    @Operation(summary = "获得已采纳询价报价的结算信息")
    public CommonResult<AppTradeOrderSettlementRespVO> settlementInquiryOrder(
            @RequestParam("quoteId") @NotNull @Positive Long quoteId,
            @RequestParam("addressId") @NotNull @Positive Long addressId) {
        return success(tradeOrderUpdateService.settlementInquiryOrder(getLoginUserId(), quoteId, addressId));
    }

    @PostMapping("/inquiry-create")
    @Operation(summary = "将已采纳询价报价转换为商城订单")
    public CommonResult<AppTradeOrderCreateRespVO> createInquiryOrder(
            @Valid @RequestBody AppTradeInquiryOrderCreateReqVO createReqVO) {
        TradeOrderDO order = tradeOrderUpdateService.createInquiryOrder(getLoginUserId(), createReqVO);
        return success(new AppTradeOrderCreateRespVO().setId(order.getId()).setPayOrderId(order.getPayOrderId()));
    }

    @GetMapping("/inquiry-order")
    @Operation(summary = "查询已采纳询价报价对应的商城订单")
    public CommonResult<AppTradeOrderCreateRespVO> getInquiryOrder(
            @RequestParam("quoteId") @NotNull @Positive Long quoteId) {
        TradeOrderDO order = tradeOrderUpdateService.getInquiryOrder(getLoginUserId(), quoteId);
        return success(order == null ? null
                : new AppTradeOrderCreateRespVO().setId(order.getId()).setPayOrderId(order.getPayOrderId()));
    }

    @PostMapping("/update-paid")
    @Operation(summary = "更新订单为已支付") // 由 pay-module 支付服务，进行回调，可见 PayNotifyJob
    @PermitAll
    public CommonResult<Boolean> updateOrderPaid(@RequestBody PayOrderNotifyReqDTO notifyReqDTO) {
        Long merchantOrderId = Long.valueOf(notifyReqDTO.getMerchantOrderId());
        if (merchantOrderId < 0) {
            platformCheckoutOrderService.updatePaid(-merchantOrderId, notifyReqDTO.getPayOrderId());
        } else {
            tradeOrderUpdateService.updateOrderPaid(merchantOrderId, notifyReqDTO.getPayOrderId());
        }
        return success(true);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得交易订单")
    @Parameters({
            @Parameter(name = "id", description = "交易订单编号"),
            @Parameter(name = "sync", description = "是否同步支付状态", example = "true")
    })
    public CommonResult<AppTradeOrderDetailRespVO> getOrderDetail(@RequestParam("id") Long id,
                                                                  @RequestParam(value = "sync", required = false) Boolean sync) {
        // 1.1 查询订单
        TradeOrderDO order = platformBuyerOrders.get(id, getLoginUserId(), TenantContextHolder.getRequiredTenantId());
        if (order == null) {
            return success(null);
        }
        // 1.2 sync 仅在等待支付
        if (Boolean.TRUE.equals(sync)
                && TradeOrderStatusEnum.isUnpaid(order.getStatus()) && !order.getPayStatus() && order.getPayOrderId() != null) {
            tradeOrderUpdateService.syncOrderPayStatusQuietly(order.getId(), order.getPayOrderId());
            // 重新查询，因为同步后，可能会有变化
            order = platformBuyerOrders.get(id, getLoginUserId(), TenantContextHolder.getRequiredTenantId());
        }

        // 2.1 查询订单项
        List<TradeOrderItemDO> orderItems = platformBuyerOrders.items(List.of(order.getId()));
        // 2.2 查询物流公司
        DeliveryExpressDO express = order.getLogisticsId() != null && order.getLogisticsId() > 0 ?
                deliveryExpressService.getDeliveryExpress(order.getLogisticsId()) : null;
        // 2.3 最终组合
        var response = TradeOrderConvert.INSTANCE.convert02(order, orderItems, tradeOrderProperties, express);
        fillDispatchOrigin(response);
        fillLatestAfterSales(response.getItems());
        response.setErpInventory(erpInventory.list(order.getId()));
        paymentDeadline.fill(order, response, true);
        return success(response);
    }

    @GetMapping("/get-express-track-list")
    @Operation(summary = "获得交易订单的物流轨迹")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<List<AppOrderExpressTrackRespDTO>> getOrderExpressTrackList(@RequestParam("id") Long id) {
        return success(TradeOrderConvert.INSTANCE.convertList02(
                tradeOrderQueryService.getExpressTrackList(id, getLoginUserId())));
    }

    @GetMapping("/get-logistics")
    @Operation(summary = "获得本人订单只读物流摘要")
    public CommonResult<AppTradeOrderLogisticsRespVO> getLogistics(@RequestParam("id") Long id) {
        PlatformBuyerOrderQueryService.AuthorizedOrder authorizedOrder = platformBuyerOrders.getAuthorized(
                id, getLoginUserId(), TenantContextHolder.getRequiredTenantId());
        if (authorizedOrder == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        AppTradeOrderLogisticsRespVO[] response = new AppTradeOrderLogisticsRespVO[1];
        // 平台买家可查看其他租户下的子订单；物流、快递公司与 ERP 数据仍需在卖家租户下读取。
        TenantUtils.execute(authorizedOrder.tenantId(),
                () -> response[0] = logistics.getForAuthorizedOrder(authorizedOrder.order()));
        return success(response[0]);
    }

    @GetMapping("/page")
    @Operation(summary = "获得交易订单分页")
    public CommonResult<PageResult<AppTradeOrderPageItemRespVO>> getOrderPage(AppTradeOrderPageReqVO reqVO) {
        // 查询订单
        PageResult<TradeOrderDO> pageResult = platformBuyerOrders.page(
                getLoginUserId(), TenantContextHolder.getRequiredTenantId(), reqVO);
        // 查询订单项
        List<TradeOrderItemDO> orderItems = platformBuyerOrders.items(
                convertSet(pageResult.getList(), TradeOrderDO::getId));
        // 最终组合
        var response = TradeOrderConvert.INSTANCE.convertPage02(pageResult, orderItems);
        fillLatestAfterSales(response.getList().stream().flatMap(row -> row.getItems().stream()).toList());
        // 转换保持相同顺序；新单列表无需逐笔调用支付服务，旧待支付单保守核实历史期限。
        for (int i = 0; i < pageResult.getList().size(); i++) {
            paymentDeadline.fill(pageResult.getList().get(i), response.getList().get(i), false);
        }
        return success(response);
    }

    private void fillDispatchOrigin(AppTradeOrderDetailRespVO response) {
        if (response.getShopId() == null || response.getShopId() <= 0) {
            return;
        }
        try {
            CommonResult<ProductShopRespDTO> result = productShopApi.getShop(response.getShopId());
            if (result != null && result.isSuccess() && result.getData() != null) {
                response.setDispatchOriginAddress(result.getData().getDispatchAddress());
            }
        } catch (Exception exception) {
            // 发货地仅用于预计路线，商品服务短暂不可用不能影响订单详情主链路。
            log.warn("读取订单店铺发货地址失败，orderId={}, shopId={}", response.getId(), response.getShopId(), exception);
        }
    }

    private void fillLatestAfterSales(List<AppTradeOrderItemRespVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, AfterSaleDO> latest = afterSaleService.getLatestAfterSales(getLoginUserId(),
                items.stream().map(AppTradeOrderItemRespVO::getId).filter(Objects::nonNull).toList());
        for (AppTradeOrderItemRespVO item : items) {
            AfterSaleDO afterSale = latest.get(item.getId());
            if (afterSale == null) {
                continue;
            }
            String reason = afterSale.getAuditReason() != null && !afterSale.getAuditReason().isBlank()
                    ? afterSale.getAuditReason() : afterSale.getReceiveReason();
            item.setLatestAfterSaleId(afterSale.getId())
                    .setLatestAfterSaleStatus(afterSale.getStatus())
                    .setLatestAfterSaleReason(reason);
        }
    }

    @GetMapping("/get-count")
    @Operation(summary = "获得交易订单数量")
    public CommonResult<Map<String, Long>> getOrderCount() {
        Map<String, Long> orderCount = Maps.newLinkedHashMapWithExpectedSize(5);
        // 全部
        orderCount.put("allCount", tradeOrderQueryService.getOrderCount(getLoginUserId(), null, null));
        // 待付款（未支付）
        orderCount.put("unpaidCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.UNPAID.getStatus(), null));
        // 待发货
        orderCount.put("undeliveredCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.UNDELIVERED.getStatus(), null));
        // 待收货
        orderCount.put("deliveredCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.DELIVERED.getStatus(), null));
        // 待评价
        orderCount.put("uncommentedCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.COMPLETED.getStatus(), false));
        // 售后数量
        orderCount.put("afterSaleCount", afterSaleService.getApplyingAfterSaleCount(getLoginUserId()));
        return success(orderCount);
    }

    @PutMapping("/receive")
    @Operation(summary = "确认交易订单收货")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<Boolean> receiveOrder(@RequestParam("id") Long id) {
        PlatformBuyerOrderQueryService.AuthorizedOrder authorizedOrder = platformBuyerOrders.getAuthorized(
                id, getLoginUserId(), TenantContextHolder.getRequiredTenantId());
        if (authorizedOrder == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 跨租户平台订单需在卖家租户中更新，同时仍由上面的平台账号归属关系授权。
        TenantUtils.execute(authorizedOrder.tenantId(), () -> tradeOrderUpdateService.receiveOrderByMember(
                authorizedOrder.order().getUserId(), authorizedOrder.order().getId()));
        return success(true);
    }

    @DeleteMapping("/cancel")
    @Operation(summary = "取消交易订单")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<Boolean> cancelOrder(@RequestParam("id") Long id) {
        tradeOrderUpdateService.cancelOrderByMember(getLoginUserId(), id);
        return success(true);
    }

    @PutMapping("/update-address")
    @Operation(summary = "修改本人待支付快递订单的收货地址")
    public CommonResult<Boolean> updateOrderAddress(@Valid @RequestBody AppTradeOrderUpdateAddressReqVO reqVO) {
        tradeOrderUpdateService.updateOrderAddressByMember(getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除交易订单")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<Boolean> deleteOrder(@RequestParam("id") Long id) {
        tradeOrderUpdateService.deleteOrder(getLoginUserId(), id);
        return success(true);
    }

    // ========== 订单项 ==========

    @GetMapping("/item/get")
    @Operation(summary = "获得交易订单项")
    @Parameter(name = "id", description = "交易订单项编号")
    public CommonResult<AppTradeOrderItemRespVO> getOrderItem(@RequestParam("id") Long id) {
        TradeOrderItemDO item = tradeOrderQueryService.getOrderItem(getLoginUserId(), id);
        return success(TradeOrderConvert.INSTANCE.convert03(item));
    }

    @PostMapping("/item/create-comment")
    @Operation(summary = "创建交易订单项的评价")
    public CommonResult<Long> createOrderItemComment(@RequestBody AppTradeOrderItemCommentCreateReqVO createReqVO) {
        return success(tradeOrderUpdateService.createOrderItemCommentByMember(getLoginUserId(), createReqVO));
    }

}
