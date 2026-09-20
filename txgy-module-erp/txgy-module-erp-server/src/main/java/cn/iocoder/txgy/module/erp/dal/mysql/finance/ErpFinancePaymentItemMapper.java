package cn.iocoder.txgy.module.erp.dal.mysql.finance;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpFinancePaymentItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * ERP 付款单项 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ErpFinancePaymentItemMapper extends BaseMapperX<ErpFinancePaymentItemDO> {

    default List<ErpFinancePaymentItemDO> selectListByPaymentId(Long paymentId) {
        return selectList(ErpFinancePaymentItemDO::getPaymentId, paymentId);
    }

    default List<ErpFinancePaymentItemDO> selectListByPaymentIds(Collection<Long> paymentIds) {
        return selectList(ErpFinancePaymentItemDO::getPaymentId, paymentIds);
    }

    @Select("""
            SELECT COALESCE(SUM(i.payment_price), 0)
            FROM erp_finance_payment_item i
            INNER JOIN erp_finance_payment p ON p.id = i.payment_id AND p.deleted = 0
            WHERE i.biz_id = #{bizId} AND i.biz_type = #{bizType}
              AND i.deleted = 0 AND p.status = 20
            """)
    BigDecimal selectPaymentPriceSumByBizIdAndBizType(@Param("bizId") Long bizId,
                                                      @Param("bizType") Integer bizType);

}
