package cn.iocoder.txgy.module.trade.framework.rpc.config;

import cn.iocoder.txgy.module.member.api.address.MemberAddressApi;
import cn.iocoder.txgy.module.member.api.config.MemberConfigApi;
import cn.iocoder.txgy.module.member.api.level.MemberLevelApi;
import cn.iocoder.txgy.module.member.api.point.MemberPointApi;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.refund.PayRefundApi;
import cn.iocoder.txgy.module.pay.api.transfer.PayTransferApi;
import cn.iocoder.txgy.module.pay.api.wallet.PayWalletApi;
import cn.iocoder.txgy.module.product.api.category.ProductCategoryApi;
import cn.iocoder.txgy.module.product.api.comment.ProductCommentApi;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.promotion.api.bargain.BargainActivityApi;
import cn.iocoder.txgy.module.promotion.api.bargain.BargainRecordApi;
import cn.iocoder.txgy.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.txgy.module.promotion.api.coupon.CouponApi;
import cn.iocoder.txgy.module.promotion.api.discount.DiscountActivityApi;
import cn.iocoder.txgy.module.promotion.api.point.PointActivityApi;
import cn.iocoder.txgy.module.promotion.api.reward.RewardActivityApi;
import cn.iocoder.txgy.module.promotion.api.seckill.SeckillActivityApi;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.system.api.social.SocialUserApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "tradeRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {
        BargainActivityApi.class, BargainRecordApi.class, CombinationRecordApi.class,
        CouponApi.class, DiscountActivityApi.class, RewardActivityApi.class, SeckillActivityApi.class, PointActivityApi.class,
        MemberUserApi.class, MemberPointApi.class, MemberLevelApi.class, MemberAddressApi.class, MemberConfigApi.class,
        ProductSpuApi.class, ProductSkuApi.class, ProductCommentApi.class, ProductCategoryApi.class,
        PayOrderApi.class, PayRefundApi.class, PayTransferApi.class, PayWalletApi.class,
        AdminUserApi.class, NotifyMessageSendApi.class, SocialClientApi.class, SocialUserApi.class
})
public class RpcConfiguration {
}
