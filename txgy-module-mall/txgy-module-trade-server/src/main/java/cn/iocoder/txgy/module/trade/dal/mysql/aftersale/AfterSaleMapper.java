package cn.iocoder.txgy.module.trade.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.AfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AfterSaleMapper extends BaseMapperX<AfterSaleDO> {

    default PageResult<AfterSaleDO> selectPage(AfterSalePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AfterSaleDO>()
                .eqIfPresent(AfterSaleDO::getShopId, reqVO.getShopId())
                .eqIfPresent(AfterSaleDO::getUserId, reqVO.getUserId())
                .likeIfPresent(AfterSaleDO::getNo, reqVO.getNo())
                .eqIfPresent(AfterSaleDO::getStatus, reqVO.getStatus())
                .eqIfPresent(AfterSaleDO::getType, reqVO.getType())
                .eqIfPresent(AfterSaleDO::getWay, reqVO.getWay())
                .likeIfPresent(AfterSaleDO::getOrderNo, reqVO.getOrderNo())
                .likeIfPresent(AfterSaleDO::getSpuName, reqVO.getSpuName())
                .betweenIfPresent(AfterSaleDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(AfterSaleDO::getId));
    }

    default PageResult<AfterSaleDO> selectPage(Long userId, AppAfterSalePageReqVO pageReqVO) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<AfterSaleDO>()
                .eq(AfterSaleDO::getUserId, userId)
                .inIfPresent(AfterSaleDO::getStatus, pageReqVO.getStatuses())
                .orderByDesc(AfterSaleDO::getId));
    }

    default int updateByIdAndStatus(Long id, Integer status, AfterSaleDO update) {
        return update(update, new LambdaUpdateWrapper<AfterSaleDO>()
                .eq(AfterSaleDO::getId, id).eq(AfterSaleDO::getStatus, status));
    }

    default AfterSaleDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(AfterSaleDO::getId, id,
                AfterSaleDO::getUserId, userId);
    }

    default Long selectCountByUserIdAndStatus(Long userId, Collection<Integer> statuses) {
        return selectCount(new LambdaQueryWrapperX<AfterSaleDO>()
                .eq(AfterSaleDO::getUserId, userId)
                .in(AfterSaleDO::getStatus, statuses));
    }

    /**
     * 企业采购售后只关联当前租户、当前企业的询价成交订单。租户和企业编号均来自服务端鉴权结果，
     * 不接受前端传入，子查询显式带 tenant_id，避免租户插件只约束外层售后表时扩大范围。
     */
    default PageResult<AfterSaleDO> selectEnterpriseInquiryPage(AppAfterSalePageReqVO pageReqVO,
                                                                  Long tenantId,
                                                                  Long buyerEnterpriseId) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<AfterSaleDO>()
                .inIfPresent(AfterSaleDO::getStatus, pageReqVO.getStatuses())
                .inSql(AfterSaleDO::getOrderId, enterpriseInquiryOrderSql(tenantId, buyerEnterpriseId))
                .orderByDesc(AfterSaleDO::getId));
    }

    default Long selectEnterpriseInquiryCount(Long tenantId, Long buyerEnterpriseId,
                                               Collection<Integer> statuses) {
        return selectCount(new LambdaQueryWrapperX<AfterSaleDO>()
                .inIfPresent(AfterSaleDO::getStatus, statuses)
                .inSql(AfterSaleDO::getOrderId, enterpriseInquiryOrderSql(tenantId, buyerEnterpriseId)));
    }

    default List<AfterSaleDO> selectEnterpriseLatestByOrderItemIds(Long tenantId, Long buyerEnterpriseId,
                                                                    Collection<Long> orderItemIds) {
        return selectList(new LambdaQueryWrapperX<AfterSaleDO>()
                .inSql(AfterSaleDO::getOrderId, enterpriseInquiryOrderSql(tenantId, buyerEnterpriseId))
                .in(AfterSaleDO::getOrderItemId, orderItemIds)
                .orderByDesc(AfterSaleDO::getId));
    }

    private static String enterpriseInquiryOrderSql(Long tenantId, Long buyerEnterpriseId) {
        // 两个编号均为已校验的 Long，不拼接任何用户输入。
        return "SELECT id FROM trade_order WHERE deleted = 0 AND tenant_id = " + tenantId
                + " AND source_type = 'INQUIRY' AND buyer_enterprise_id = " + buyerEnterpriseId;
    }

    default List<AfterSaleDO> selectLatestByOrderItemIds(Long userId, Collection<Long> orderItemIds) {
        return selectList(new LambdaQueryWrapperX<AfterSaleDO>()
                .eq(AfterSaleDO::getUserId, userId)
                .in(AfterSaleDO::getOrderItemId, orderItemIds)
                .orderByDesc(AfterSaleDO::getId));
    }

}
