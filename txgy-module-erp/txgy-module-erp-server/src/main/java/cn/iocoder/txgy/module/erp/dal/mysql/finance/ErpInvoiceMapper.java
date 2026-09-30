package cn.iocoder.txgy.module.erp.dal.mysql.finance;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoicePageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpInvoiceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpInvoiceMapper extends BaseMapperX<ErpInvoiceDO> {

    default PageResult<ErpInvoiceDO> selectPage(ErpInvoicePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpInvoiceDO>()
                .likeIfPresent(ErpInvoiceDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpInvoiceDO::getInvoiceNo, reqVO.getInvoiceNo())
                .likeIfPresent(ErpInvoiceDO::getTitle, reqVO.getTitle())
                .eqIfPresent(ErpInvoiceDO::getInvoiceType, reqVO.getInvoiceType())
                .eqIfPresent(ErpInvoiceDO::getBizType, reqVO.getBizType())
                .eqIfPresent(ErpInvoiceDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ErpInvoiceDO::getInvoiceDate, reqVO.getInvoiceDate())
                .orderByDesc(ErpInvoiceDO::getId));
    }

    default ErpInvoiceDO selectByNo(String no) { return selectOne(ErpInvoiceDO::getNo, no); }
}
