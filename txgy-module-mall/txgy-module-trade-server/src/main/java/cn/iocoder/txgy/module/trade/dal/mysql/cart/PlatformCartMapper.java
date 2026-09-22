package cn.iocoder.txgy.module.trade.dal.mysql.cart;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.PlatformCartDO;
import org.apache.ibatis.annotations.Mapper;
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

    default int deleteByIdsAndMember(Collection<Long> ids, Long platformMemberId) {
        return delete(new LambdaQueryWrapperX<PlatformCartDO>()
                .in(PlatformCartDO::getId, ids)
                .eq(PlatformCartDO::getPlatformMemberId, platformMemberId));
    }

}
