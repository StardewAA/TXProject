package cn.iocoder.txgy.module.trade.dal.mysql.checkout;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutFormalizationDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface PlatformCheckoutFormalizationMapper extends BaseMapperX<PlatformCheckoutFormalizationDO> {

    /** 原子占用结算单下的正式化幂等键；冲突时只返回原记录编号。 */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
        INSERT INTO trade_platform_checkout_formalization
          (checkout_id,checkout_no,platform_member_id,idempotency_key,snapshot_digest,attempt_status,
           blocker_codes,blocker_reason,version,creator,create_time,updater,update_time,deleted)
        VALUES
          (#{checkoutId},#{checkoutNo},#{platformMemberId},#{idempotencyKey},#{snapshotDigest},#{attemptStatus},
           #{blockerCodes},#{blockerReason},#{version},#{creator},NOW(),#{updater},NOW(),FALSE)
        ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", flushCache = Options.FlushCachePolicy.TRUE)
    int insertBlockedOrGetId(PlatformCheckoutFormalizationDO attempt);

    default PlatformCheckoutFormalizationDO selectByCheckoutAndKey(Long checkoutId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<PlatformCheckoutFormalizationDO>()
                .eq(PlatformCheckoutFormalizationDO::getCheckoutId, checkoutId)
                .eq(PlatformCheckoutFormalizationDO::getIdempotencyKey, idempotencyKey));
    }
}
