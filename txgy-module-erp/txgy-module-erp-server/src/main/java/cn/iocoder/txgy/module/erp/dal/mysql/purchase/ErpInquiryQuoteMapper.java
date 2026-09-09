package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpInquiryQuoteDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpInquiryQuoteMapper extends BaseMapperX<ErpInquiryQuoteDO> {
    default List<ErpInquiryQuoteDO> selectListByInquiryId(Long inquiryId) {
        return selectList(ErpInquiryQuoteDO::getInquiryId, inquiryId);
    }
    default ErpInquiryQuoteDO selectByNo(String no) { return selectOne(ErpInquiryQuoteDO::getNo, no); }
}
