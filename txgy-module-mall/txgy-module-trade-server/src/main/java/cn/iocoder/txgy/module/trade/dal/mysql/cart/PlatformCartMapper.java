package cn.iocoder.txgy.module.trade.dal.mysql.cart;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.PlatformCartDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PlatformCartMapper extends BaseMapperX<PlatformCartDO> {

    default PlatformCartDO selectByMemberAndSkuId(Long platformMemberId, Long skuId) {
        return selectOne(new LambdaQueryWrapperX<PlatformCartDO>()
                .eq(PlatformCartDO::getPlatformMemberId, platformMemberId)
                .eq(PlatformCartDO::getSkuId, skuId));
    }

    default PlatformCartDO selectByIdAndMember(Long id, Long platformMemberId) {
        return selectOne(new LambdaQueryWrapperX<PlatformCartDO>()
                .eq(PlatformCartDO::getId, id)
                .eq(PlatformCartDO::getPlatformMemberId, platformMemberId));
    }

    default List<PlatformCartDO> selectListByMember(Long platformMemberId) {
        return selectList(new LambdaQueryWrapperX<PlatformCartDO>()
                .eq(PlatformCartDO::getPlatformMemberId, platformMemberId)
                .orderByAsc(PlatformCartDO::getTenantId, PlatformCartDO::getShopId, PlatformCartDO::getId));
    }

    default List<PlatformCartDO> selectListByIdsAndMember(Collection<Long> ids, Long platformMemberId) {
        return selectList(new LambdaQueryWrapperX<PlatformCartDO>()
                .in(PlatformCartDO::getId, ids)
                .eq(PlatformCartDO::getPlatformMemberId, platformMemberId));
    }

    @Update("UPDATE trade_platform_cart SET quantity=quantity+#{delta}, selected=b'1', invalid_reason=NULL, "
            + "updater=#{platformMemberId}, update_time=NOW() "
            + "WHERE id=#{id} AND platform_member_id=#{platformMemberId} AND deleted=b'0' "
            + "AND quantity>=1 AND quantity&lt;=#{maxQuantity}-#{delta}")
    int incrementQuantityAndSelect(@Param("id") Long id, @Param("platformMemberId") Long platformMemberId,
                                   @Param("delta") Integer delta,
                                   @Param("maxQuantity") Integer maxQuantity);

    @Update("UPDATE trade_platform_cart SET quantity=#{quantity}, invalid_reason=NULL, "
            + "updater=#{platformMemberId}, update_time=NOW() "
            + "WHERE id=#{id} AND platform_member_id=#{platformMemberId} AND deleted=b'0'")
    int updateQuantity(@Param("id") Long id, @Param("platformMemberId") Long platformMemberId,
                       @Param("quantity") Integer quantity);

    @Update({"<script>",
            "UPDATE trade_platform_cart SET selected=#{selected}, updater=#{platformMemberId}, update_time=NOW()",
            "WHERE platform_member_id=#{platformMemberId} AND deleted=b'0' AND id IN",
            "(<foreach collection='ids' item='id' separator=','>#{id}</foreach>)",
            "</script>"})
    int updateSelected(@Param("platformMemberId") Long platformMemberId,
                       @Param("ids") Collection<Long> ids,
                       @Param("selected") Boolean selected);

    @Update("UPDATE trade_platform_cart SET invalid_reason=#{invalidReason}, updater=#{platformMemberId}, "
            + "update_time=NOW() WHERE id=#{id} AND platform_member_id=#{platformMemberId} AND deleted=b'0'")
    int updateInvalidReason(@Param("id") Long id, @Param("platformMemberId") Long platformMemberId,
                            @Param("invalidReason") String invalidReason);

    /**
     * 购物车是临时数据，移除时必须物理删除。
     *
     * <p>表上的唯一键包含 {@code deleted}。如果使用 MyBatis-Plus 逻辑删除，用户第二次购买同一 SKU 时，
     * 会因已有一条 {@code deleted=1} 的历史记录而触发唯一键冲突，进而回滚整个结算事务。</p>
     */
    @Delete({"<script>",
            "DELETE FROM trade_platform_cart",
            "WHERE platform_member_id=#{platformMemberId} AND deleted=b'0' AND id IN",
            "(<foreach collection='ids' item='id' separator=','>#{id}</foreach>)",
            "</script>"})
    int hardDeleteByIdsAndMember(@Param("ids") Collection<Long> ids,
                                 @Param("platformMemberId") Long platformMemberId);

}
