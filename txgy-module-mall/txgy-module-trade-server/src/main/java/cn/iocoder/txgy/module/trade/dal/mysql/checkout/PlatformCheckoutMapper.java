package cn.iocoder.txgy.module.trade.dal.mysql.checkout;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface PlatformCheckoutMapper extends BaseMapperX<PlatformCheckoutDO> {

    /**
     * 原子占用平台账号下的幂等键。冲突时返回既有记录编号，不修改任何业务字段。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
        INSERT INTO trade_platform_checkout
          (checkout_no,idempotency_key,request_digest,platform_member_id,address_id,address_source_tenant_id,
           receiver_name,receiver_mobile,receiver_area_id,receiver_detail_address,receiver_longitude,
           receiver_latitude,receiver_coord_system,remark,currency,goods_amount,freight_amount,discount_amount,
           pay_amount,paid_amount,refunded_amount,pay_status,aggregate_status,failure_reason,
           platform_pay_order_id,platform_pay_order_no,version,creator,create_time,updater,update_time,deleted)
        VALUES
          (#{checkoutNo},#{idempotencyKey},#{requestDigest},#{platformMemberId},#{addressId},
           #{addressSourceTenantId},#{receiverName},#{receiverMobile},#{receiverAreaId},#{receiverDetailAddress},
           #{receiverLongitude},#{receiverLatitude},#{receiverCoordSystem},#{remark},#{currency},#{goodsAmount},
           #{freightAmount},#{discountAmount},#{payAmount},#{paidAmount},#{refundedAmount},#{payStatus},
           #{aggregateStatus},#{failureReason},#{platformPayOrderId},#{platformPayOrderNo},#{version},
           #{creator},NOW(),#{updater},NOW(),FALSE)
        ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", flushCache = Options.FlushCachePolicy.TRUE)
    int insertDraftOrGetId(PlatformCheckoutDO checkout);

    default PlatformCheckoutDO selectByNoAndMember(String checkoutNo, Long platformMemberId) {
        return selectOne(new LambdaQueryWrapperX<PlatformCheckoutDO>()
                .eq(PlatformCheckoutDO::getCheckoutNo, checkoutNo)
                .eq(PlatformCheckoutDO::getPlatformMemberId, platformMemberId));
    }

    /**
     * 锁定结算主记录，作为后续正式化准备的唯一事务入口。
     *
     * <p>拆单规划在当前版本是不可变快照；未来真正写子订单时也必须先取得这把锁，
     * 禁止多个正式化请求并发消费同一份规划。</p>
     */
    default PlatformCheckoutDO selectByNoAndMemberForUpdate(String checkoutNo, Long platformMemberId) {
        return selectOne(new LambdaQueryWrapperX<PlatformCheckoutDO>()
                .eq(PlatformCheckoutDO::getCheckoutNo, checkoutNo)
                .eq(PlatformCheckoutDO::getPlatformMemberId, platformMemberId)
                .last("FOR UPDATE"));
    }

    default PlatformCheckoutDO selectByIdempotencyKey(Long platformMemberId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<PlatformCheckoutDO>()
                .eq(PlatformCheckoutDO::getPlatformMemberId, platformMemberId)
                .eq(PlatformCheckoutDO::getIdempotencyKey, idempotencyKey));
    }

    default PageResult<PlatformCheckoutDO> selectPageByMember(PageParam pageParam, Long platformMemberId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<PlatformCheckoutDO>()
                .eq(PlatformCheckoutDO::getPlatformMemberId, platformMemberId)
                .orderByDesc(PlatformCheckoutDO::getCreateTime, PlatformCheckoutDO::getId));
    }

}
