package cn.iocoder.txgy.module.trade.dal.mysql.order;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.txgy.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface TradeOrderMapper extends BaseMapperX<TradeOrderDO> {

    String APP_BUYER_OWNERSHIP = "((o.tenant_id=#{sourceTenantId} AND o.user_id=#{legacyMemberId}) "
            + "<if test='platformAccountId != null'> OR EXISTS (SELECT 1 FROM trade_platform_checkout_order r "
            + "JOIN trade_platform_checkout c ON c.id=r.checkout_id AND c.deleted=0 "
            + "WHERE r.deleted=0 AND r.order_id=o.id AND r.tenant_id=o.tenant_id "
            + "AND c.platform_member_id=#{platformAccountId})</if>)";

    /** Buyer-visible orders across tenants. Platform checkout ownership is the cross-tenant authorization boundary. */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT o.* FROM trade_order o WHERE o.deleted=0 AND " + APP_BUYER_OWNERSHIP
            + " <if test='status != null'>AND o.status=#{status}</if>"
            + " <if test='commentStatus != null'>AND o.comment_status=#{commentStatus}</if>"
            + " ORDER BY o.id DESC LIMIT #{offset},#{pageSize}</script>")
    List<TradeOrderDO> selectAppBuyerPage(@Param("legacyMemberId") Long legacyMemberId,
                                           @Param("sourceTenantId") Long sourceTenantId,
                                           @Param("platformAccountId") Long platformAccountId,
                                           @Param("status") Integer status,
                                           @Param("commentStatus") Boolean commentStatus,
                                           @Param("offset") long offset,
                                           @Param("pageSize") int pageSize);

    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT COUNT(*) FROM trade_order o WHERE o.deleted=0 AND " + APP_BUYER_OWNERSHIP
            + " <if test='status != null'>AND o.status=#{status}</if>"
            + " <if test='commentStatus != null'>AND o.comment_status=#{commentStatus}</if></script>")
    Long selectAppBuyerCount(@Param("legacyMemberId") Long legacyMemberId,
                             @Param("sourceTenantId") Long sourceTenantId,
                             @Param("platformAccountId") Long platformAccountId,
                             @Param("status") Integer status,
                             @Param("commentStatus") Boolean commentStatus);

    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT o.* FROM trade_order o WHERE o.deleted=0 AND o.id=#{orderId} AND "
            + APP_BUYER_OWNERSHIP + " LIMIT 1</script>")
    TradeOrderDO selectAppBuyerOwnedOrder(@Param("orderId") Long orderId,
                                           @Param("legacyMemberId") Long legacyMemberId,
                                           @Param("sourceTenantId") Long sourceTenantId,
                                           @Param("platformAccountId") Long platformAccountId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT o.tenant_id FROM trade_order o WHERE o.deleted=0 AND o.id=#{orderId} AND "
            + APP_BUYER_OWNERSHIP + " LIMIT 1</script>")
    Long selectAppBuyerOwnedOrderTenantId(@Param("orderId") Long orderId,
                                           @Param("legacyMemberId") Long legacyMemberId,
                                           @Param("sourceTenantId") Long sourceTenantId,
                                           @Param("platformAccountId") Long platformAccountId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT o.tenant_id FROM trade_order o "
            + "JOIN trade_order_item i ON i.order_id=o.id AND i.tenant_id=o.tenant_id "
            + "AND i.user_id=o.user_id AND i.deleted=0 "
            + "WHERE o.deleted=0 AND i.id=#{orderItemId} AND " + APP_BUYER_OWNERSHIP
            + " LIMIT 1</script>")
    Long selectAppBuyerOwnedOrderTenantIdByItem(@Param("orderItemId") Long orderItemId,
                                                 @Param("legacyMemberId") Long legacyMemberId,
                                                 @Param("sourceTenantId") Long sourceTenantId,
                                                 @Param("platformAccountId") Long platformAccountId);

    default int updateAddressAndClearLocation(TradeOrderDO update, Integer expectedStatus) {
        return update(update, new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getId, update.getId()).eq(TradeOrderDO::getStatus, expectedStatus)
                .set(TradeOrderDO::getReceiverLongitude, null).set(TradeOrderDO::getReceiverLatitude, null)
                .set(TradeOrderDO::getReceiverCoordSystem, null).set(TradeOrderDO::getReceiverLocationSource, null)
                .set(TradeOrderDO::getReceiverLocationPrecision, null).set(TradeOrderDO::getReceiverLocationConfirmedAt, null));
    }

    default int updateUnpaidAddressAndPrice(TradeOrderDO update, Long userId) {
        return update(update, new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getId, update.getId()).eq(TradeOrderDO::getUserId, userId)
                .eq(TradeOrderDO::getStatus, TradeOrderStatusEnum.UNPAID.getStatus())
                .eq(TradeOrderDO::getPayStatus, false)
                .eq(TradeOrderDO::getDeliveryType, DeliveryTypeEnum.EXPRESS.getType())
                .set(TradeOrderDO::getReceiverLongitude, update.getReceiverLongitude())
                .set(TradeOrderDO::getReceiverLatitude, update.getReceiverLatitude())
                .set(TradeOrderDO::getReceiverCoordSystem, update.getReceiverCoordSystem())
                .set(TradeOrderDO::getReceiverLocationSource, update.getReceiverLocationSource())
                .set(TradeOrderDO::getReceiverLocationPrecision, update.getReceiverLocationPrecision())
                .set(TradeOrderDO::getReceiverLocationConfirmedAt, update.getReceiverLocationConfirmedAt()));
    }

    /** 企业采购协作修改地址。调用方已校验当前会员资格，这里仍以企业、来源和订单状态做原子约束。 */
    default int updateEnterpriseInquiryUnpaidAddressAndPrice(TradeOrderDO update, Long buyerEnterpriseId) {
        return update(update, new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getId, update.getId())
                .eq(TradeOrderDO::getSourceType, TradeOrderDO.SOURCE_TYPE_INQUIRY)
                .eq(TradeOrderDO::getBuyerEnterpriseId, buyerEnterpriseId)
                .eq(TradeOrderDO::getStatus, TradeOrderStatusEnum.UNPAID.getStatus())
                .eq(TradeOrderDO::getPayStatus, false)
                .eq(TradeOrderDO::getDeliveryType, DeliveryTypeEnum.EXPRESS.getType())
                .set(TradeOrderDO::getReceiverLongitude, update.getReceiverLongitude())
                .set(TradeOrderDO::getReceiverLatitude, update.getReceiverLatitude())
                .set(TradeOrderDO::getReceiverCoordSystem, update.getReceiverCoordSystem())
                .set(TradeOrderDO::getReceiverLocationSource, update.getReceiverLocationSource())
                .set(TradeOrderDO::getReceiverLocationPrecision, update.getReceiverLocationPrecision())
                .set(TradeOrderDO::getReceiverLocationConfirmedAt, update.getReceiverLocationConfirmedAt()));
    }

    default TradeOrderDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getId, id).last("FOR UPDATE"));
    }

    default int updateByIdAndStatus(Long id, Integer status, TradeOrderDO update) {
        return update(update, new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getId, id).eq(TradeOrderDO::getStatus, status));
    }

    default TradeOrderDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(TradeOrderDO::getId, id, TradeOrderDO::getUserId, userId);
    }

    default TradeOrderDO selectByInquiryQuoteId(Long inquiryQuoteId) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getInquiryQuoteId, inquiryQuoteId));
    }

    default TradeOrderDO selectByInquiryQuoteIdForUpdate(Long inquiryQuoteId) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getInquiryQuoteId, inquiryQuoteId)
                .last("FOR UPDATE"));
    }

    default PageResult<TradeOrderDO> selectPage(TradeOrderPageReqVO reqVO, Set<Long> userIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TradeOrderDO>()
                .eqIfPresent(TradeOrderDO::getShopId, reqVO.getShopId())
                .likeIfPresent(TradeOrderDO::getNo, reqVO.getNo())
                .eqIfPresent(TradeOrderDO::getUserId, reqVO.getUserId())
                .eqIfPresent(TradeOrderDO::getDeliveryType, reqVO.getDeliveryType())
                .inIfPresent(TradeOrderDO::getUserId, userIds)
                .eqIfPresent(TradeOrderDO::getType, reqVO.getType())
                .eqIfPresent(TradeOrderDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TradeOrderDO::getPayChannelCode, reqVO.getPayChannelCode())
                .eqIfPresent(TradeOrderDO::getTerminal, reqVO.getTerminal())
                .eqIfPresent(TradeOrderDO::getLogisticsId, reqVO.getLogisticsId())
                .inIfPresent(TradeOrderDO::getPickUpStoreId, reqVO.getPickUpStoreIds())
                .likeIfPresent(TradeOrderDO::getPickUpVerifyCode, reqVO.getPickUpVerifyCode())
                .betweenIfPresent(TradeOrderDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(TradeOrderDO::getId));
    }

    // TODO @疯狂：如果用 map 返回，要不这里直接用 TradeOrderSummaryRespVO 返回？也算合理，就当  sql 查询出这么个玩意~~
    default List<Map<String, Object>> selectOrderSummaryGroupByRefundStatus(TradeOrderPageReqVO reqVO, Set<Long> userIds) {
        return selectMaps(new MPJLambdaWrapperX<TradeOrderDO>()
                .eqIfPresent(TradeOrderDO::getShopId, reqVO.getShopId())
                .selectAs(TradeOrderDO::getRefundStatus, TradeOrderDO::getRefundStatus)  // 售后状态
                .selectCount(TradeOrderDO::getId, "count") // 售后状态对应的数量
                .selectSum(TradeOrderDO::getPayPrice, "price")  // 售后状态对应的支付金额
                .likeIfPresent(TradeOrderDO::getNo, reqVO.getNo())
                .eqIfPresent(TradeOrderDO::getUserId, reqVO.getUserId())
                .eqIfPresent(TradeOrderDO::getDeliveryType, reqVO.getDeliveryType())
                .inIfPresent(TradeOrderDO::getUserId, userIds)
                .eqIfPresent(TradeOrderDO::getType, reqVO.getType())
                .eqIfPresent(TradeOrderDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TradeOrderDO::getPayChannelCode, reqVO.getPayChannelCode())
                .eqIfPresent(TradeOrderDO::getTerminal, reqVO.getTerminal())
                .eqIfPresent(TradeOrderDO::getLogisticsId, reqVO.getLogisticsId())
                .inIfPresent(TradeOrderDO::getPickUpStoreId, reqVO.getPickUpStoreIds())
                .likeIfPresent(TradeOrderDO::getPickUpVerifyCode, reqVO.getPickUpVerifyCode())
                .betweenIfPresent(TradeOrderDO::getCreateTime, reqVO.getCreateTime())
                .groupBy(TradeOrderDO::getRefundStatus)); // 按售后状态分组
    }

    default PageResult<TradeOrderDO> selectPage(AppTradeOrderPageReqVO reqVO, Long userId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getUserId, userId)
                .eqIfPresent(TradeOrderDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TradeOrderDO::getCommentStatus, reqVO.getCommentStatus())
                .orderByDesc(TradeOrderDO::getId)); // TODO 芋艿：未来不同的 status，不同的排序
    }

    default PageResult<TradeOrderDO> selectEnterpriseInquiryPage(AppTradeOrderPageReqVO reqVO,
                                                                  Long buyerEnterpriseId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getSourceType, TradeOrderDO.SOURCE_TYPE_INQUIRY)
                .eq(TradeOrderDO::getBuyerEnterpriseId, buyerEnterpriseId)
                .eqIfPresent(TradeOrderDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TradeOrderDO::getCommentStatus, reqVO.getCommentStatus())
                .orderByDesc(TradeOrderDO::getId));
    }

    default Long selectEnterpriseInquiryCount(Long buyerEnterpriseId, Integer status, Boolean commentStatus) {
        return selectCount(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getSourceType, TradeOrderDO.SOURCE_TYPE_INQUIRY)
                .eq(TradeOrderDO::getBuyerEnterpriseId, buyerEnterpriseId)
                .eqIfPresent(TradeOrderDO::getStatus, status)
                .eqIfPresent(TradeOrderDO::getCommentStatus, commentStatus));
    }

    default Long selectCountByUserIdAndStatus(Long userId, Integer status, Boolean commentStatus) {
        return selectCount(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getUserId, userId)
                .eqIfPresent(TradeOrderDO::getStatus, status)
                .eqIfPresent(TradeOrderDO::getCommentStatus, commentStatus));
    }

    default TradeOrderDO selectOrderByIdAndUserId(Long orderId, Long loginUserId) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getId, orderId)
                .eq(TradeOrderDO::getUserId, loginUserId));
    }

    default List<TradeOrderDO> selectListByStatusAndCreateTimeLt(Integer status, LocalDateTime createTime) {
        return selectList(new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getStatus, status)
                .lt(TradeOrderDO::getCreateTime, createTime));
    }

    /** 新单按持久化支付截止时间筛选；旧 ERP 等待字段只用于兼容扫描历史未付单。 */
    default List<TradeOrderDO> selectPaymentTimeoutCandidates(LocalDateTime now, Long afterId, int limit) {
        return selectList(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getStatus, 0)
                .gt(TradeOrderDO::getId, afterId)
                .and(w -> w.isNull(TradeOrderDO::getPayExpireSeconds)
                        .or(x -> x.le(TradeOrderDO::getPayExpireTime, now))
                        .or(x -> x.isNull(TradeOrderDO::getPayExpireTime)
                                .le(TradeOrderDO::getErpWaitExpireTime, now)))
                .orderByAsc(TradeOrderDO::getId)
                .last("LIMIT " + limit));
    }

    default List<TradeOrderDO> selectListByStatusAndDeliveryTimeLt(Integer status, LocalDateTime deliveryTime) {
        return selectList(new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getStatus, status)
                .lt(TradeOrderDO::getDeliveryTime, deliveryTime));
    }

    default List<TradeOrderDO> selectListByStatusAndReceiveTimeLt(Integer status, LocalDateTime receive,
                                                                  Boolean commentStatus) {
        return selectList(new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getStatus, status)
                .lt(TradeOrderDO::getReceiveTime, receive)
                .eq(TradeOrderDO::getCommentStatus, commentStatus));
    }

    default List<TradeOrderDO> selectListByUserIdAndActivityId(Long userId, Long activityId, TradeOrderTypeEnum type) {
        LambdaQueryWrapperX<TradeOrderDO> queryWrapperX = new LambdaQueryWrapperX<>();
        queryWrapperX.eq(TradeOrderDO::getUserId, userId);
        if (TradeOrderTypeEnum.isSeckill(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getSeckillActivityId, activityId);
        }
        if (TradeOrderTypeEnum.isBargain(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getBargainActivityId, activityId);
        }
        if (TradeOrderTypeEnum.isCombination(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getCombinationActivityId, activityId);
        }
        if (TradeOrderTypeEnum.isPoint(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getPointActivityId, activityId);
        }
        return selectList(queryWrapperX);
    }

    default TradeOrderDO selectOneByPickUpVerifyCode(String pickUpVerifyCode) {
        return selectOne(TradeOrderDO::getPickUpVerifyCode, pickUpVerifyCode);
    }

    default TradeOrderDO selectByUserIdAndCombinationActivityIdAndStatus(Long userId, Long combinationActivityId, Integer status) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getUserId, userId)
                .eq(TradeOrderDO::getStatus, status)
                .eq(TradeOrderDO::getCombinationActivityId, combinationActivityId)
        );
    }

}
