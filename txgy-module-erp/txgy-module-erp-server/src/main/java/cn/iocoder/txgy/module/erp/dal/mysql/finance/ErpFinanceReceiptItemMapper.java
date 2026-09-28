package cn.iocoder.txgy.module.erp.dal.mysql.finance;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpFinanceReceiptItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * ERP 收款单项 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ErpFinanceReceiptItemMapper extends BaseMapperX<ErpFinanceReceiptItemDO> {

    default List<ErpFinanceReceiptItemDO> selectListByReceiptId(Long receiptId) {
        return selectList(ErpFinanceReceiptItemDO::getReceiptId, receiptId);
    }

    default List<ErpFinanceReceiptItemDO> selectListByReceiptIds(Collection<Long> receiptIds) {
        return selectList(ErpFinanceReceiptItemDO::getReceiptId, receiptIds);
    }

    @Select("""
            SELECT COALESCE(SUM(i.receipt_price), 0)
            FROM erp_finance_receipt_item i
            INNER JOIN erp_finance_receipt r ON r.id = i.receipt_id AND r.deleted = 0
            WHERE i.biz_id = #{bizId} AND i.biz_type = #{bizType}
              AND i.deleted = 0 AND r.status = 20
            """)
    BigDecimal selectReceiptPriceSumByBizIdAndBizType(@Param("bizId") Long bizId,
                                                      @Param("bizType") Integer bizType);

}
