package cn.iocoder.txgy.module.pay.controller.app.order;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.pay.controller.admin.order.vo.PayOrderRespVO;
import cn.iocoder.txgy.module.pay.controller.admin.order.vo.PayOrderSubmitRespVO;
import cn.iocoder.txgy.module.pay.controller.app.order.vo.AppPayOrderSubmitReqVO;
import cn.iocoder.txgy.module.pay.controller.app.order.vo.AppPayOrderSubmitRespVO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.wallet.PayWalletDO;
import cn.iocoder.txgy.module.pay.enums.PayChannelEnum;
import cn.iocoder.txgy.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.impl.wallet.WalletPayClient;
import cn.iocoder.txgy.module.pay.service.order.PayOrderService;
import cn.iocoder.txgy.module.pay.service.wallet.PayWalletService;
import cn.iocoder.txgy.module.pay.service.app.PayAppService;
import cn.iocoder.txgy.module.trade.api.wms.TradeWmsStockApi;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.time.ZoneId;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.*;
import static cn.iocoder.txgy.module.pay.enums.ErrorCodeConstants.PAY_ORDER_NOT_FOUND;

@Tag(name = "用户 APP - 支付订单")
@RestController
@RequestMapping("/pay/order")
@Validated
@Slf4j
public class AppPayOrderController {

    @Resource
    private PayOrderService payOrderService;
    @Resource
    private PayWalletService payWalletService;
    @Resource
    private PayAppService payAppService;
    @Resource
    private TradeWmsStockApi tradeWmsStockApi;

    @GetMapping("/get")
    @Operation(summary = "获得支付订单")
    @Parameters({
            @Parameter(name = "id", description = "编号", example = "1024"),
            @Parameter(name = "no", description = "支付订单号", example = "Pxxx"),
            @Parameter(name = "sync", description = "是否同步", example = "true")
    })
    public CommonResult<PayOrderRespVO> getOrder(@RequestParam(value = "id", required = false) Long id,
                                                 @RequestParam(value = "no", required = false) String no,
                                                 @RequestParam(value = "sync", required = false) Boolean sync) {
        LoginUser member = requireMember();
        if ((id != null && id <= 0) || (id == null && CharSequenceUtil.isBlank(no))) {
            throw exception(BAD_REQUEST);
        }
        PayOrderDO order = null;
        if (CharSequenceUtil.isNotEmpty(no)) {
            order = payOrderService.getOrder(no);
        }
        if (ObjUtil.isNull(order) && ObjUtil.isNotNull(id)) {
            order = payOrderService.getOrder(id);
        }
        // 历史缺少归属的记录也不能公开，不能仅凭不同账号体系中相同的数字 ID 放行。
        if (!belongsToMember(order, member)) {
            return success(null);
        }

        // sync 仅在等待支付
        if (Boolean.TRUE.equals(sync) && PayOrderStatusEnum.isWaiting(order.getStatus())) {
            payOrderService.syncOrderQuietly(order.getId());
            // 重新查询，因为同步后，可能会有变化
            order = payOrderService.getOrder(order.getId());
            if (!belongsToMember(order, member)) return success(null);
        }
        PayOrderRespVO result = BeanUtils.toBean(order, PayOrderRespVO.class);
        result.setServerTime(System.currentTimeMillis());
        if (order.getExpireTime() != null) {
            result.setExpireTimestamp(order.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return success(result);
    }

    @PostMapping("/submit")
    @Operation(summary = "提交支付订单")
    public CommonResult<AppPayOrderSubmitRespVO> submitPayOrder(@Valid @RequestBody AppPayOrderSubmitReqVO reqVO) {
        LoginUser member = requireMember();
        if (reqVO == null || reqVO.getId() == null || reqVO.getId() <= 0) throw exception(BAD_REQUEST);
        PayOrderDO payOrder = payOrderService.getOrder(reqVO.getId());
        if (!belongsToMember(payOrder, member)) throw exception(PAY_ORDER_NOT_FOUND);
        var app = payAppService.getApp(payOrder.getAppId());
        if (app != null && "mall".equals(app.getAppKey())) {
            Long tradeOrderId;
            try {
                tradeOrderId = Long.valueOf(payOrder.getMerchantOrderId());
            } catch (NumberFormatException ex) {
                throw exception(PAY_ORDER_NOT_FOUND);
            }
            tradeWmsStockApi.validatePayment(tradeOrderId, payOrder.getId(), member.getId()).checkError();
        }
        // 1. 钱包支付事，需要额外传 user_id 和 user_type
        if (Objects.equals(reqVO.getChannelCode(), PayChannelEnum.WALLET.getCode())) {
            if (reqVO.getChannelExtras() == null) {
                reqVO.setChannelExtras(Maps.newHashMapWithExpectedSize(1));
            }
            PayWalletDO wallet = payWalletService.getOrCreateWallet(member.getId(), member.getUserType());
            reqVO.getChannelExtras().put(WalletPayClient.WALLET_ID_KEY, String.valueOf(wallet.getId()));
        }

        // 2. 提交支付
        PayOrderSubmitRespVO respVO = payOrderService.submitOrder(reqVO, getClientIP());
        return success(BeanUtils.toBean(respVO, AppPayOrderSubmitRespVO.class));
    }

    private LoginUser requireMember() {
        var authentication = SecurityFrameworkUtils.getAuthentication();
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (authentication == null || !authentication.isAuthenticated() || user == null
                || user.getId() == null || user.getId() <= 0) throw exception(UNAUTHORIZED);
        if (!Objects.equals(user.getUserType(), UserTypeEnum.MEMBER.getValue())) throw exception(FORBIDDEN);
        return user;
    }

    private boolean belongsToMember(PayOrderDO order, LoginUser member) {
        return order != null && Objects.equals(order.getUserId(), member.getId())
                && Objects.equals(order.getUserType(), UserTypeEnum.MEMBER.getValue());
    }

}
