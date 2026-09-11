package cn.iocoder.txgy.module.crm.dal.mysql.quotation;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 报价单产品明细 Mapper
 *
 * @author 防腐保温项目组
 */
@Mapper
public interface CrmQuotationProductMapper extends BaseMapperX<CrmQuotationProductDO> {

    default List<CrmQuotationProductDO> selectListByQuotationId(Long quotationId) {
        return selectList(new LambdaQueryWrapperX<CrmQuotationProductDO>()
                .eq(CrmQuotationProductDO::getQuotationId, quotationId));
    }

    default void deleteByQuotationId(Long quotationId) {
        delete(new LambdaQueryWrapperX<CrmQuotationProductDO>()
                .eq(CrmQuotationProductDO::getQuotationId, quotationId));
    }

}
