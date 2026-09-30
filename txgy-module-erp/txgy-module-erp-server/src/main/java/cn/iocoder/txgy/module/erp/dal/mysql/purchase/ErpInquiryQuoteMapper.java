package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpInquiryQuoteDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpInquiryQuoteMapper extends BaseMapperX<ErpInquiryQuoteDO> {
    default List<ErpInquiryQuoteDO> selectListByInquiryId(Long inquiryId) {
        return selectList(ErpInquiryQuoteDO::getInquiryId, inquiryId);
    }

    /** 供应商视角：只看自己的报价 */
    default List<ErpInquiryQuoteDO> selectListByInquiryIdAndSupplierId(Long inquiryId, Long supplierId) {
        return selectList(new LambdaQueryWrapperX<ErpInquiryQuoteDO>()
                .eq(ErpInquiryQuoteDO::getInquiryId, inquiryId)
                .eq(ErpInquiryQuoteDO::getSupplierId, supplierId));
    }

    /**
     * 列表页批量查询：一次取出多张询价单的报价，避免逐条查询造成 N+1。
     *
     * @param inquiryIds 询价单编号集合
     */
    default List<ErpInquiryQuoteDO> selectListByInquiryIds(Collection<Long> inquiryIds) {
        return selectList(new LambdaQueryWrapperX<ErpInquiryQuoteDO>()
                .in(ErpInquiryQuoteDO::getInquiryId, inquiryIds));
    }

    default ErpInquiryQuoteDO selectByNo(String no) { return selectOne(ErpInquiryQuoteDO::getNo, no); }
}
