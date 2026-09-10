package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.ErpInquiryPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpInquiryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpInquiryMapper extends BaseMapperX<ErpInquiryDO> {
    default PageResult<ErpInquiryDO> selectPage(ErpInquiryPageReqVO reqVO) {
        LambdaQueryWrapperX<ErpInquiryDO> query = new LambdaQueryWrapperX<ErpInquiryDO>()
                .likeIfPresent(ErpInquiryDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpInquiryDO::getTitle, reqVO.getTitle())
                .eqIfPresent(ErpInquiryDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ErpInquiryDO::getBusinessStatus, reqVO.getBusinessStatus())
                .eqIfPresent(ErpInquiryDO::getAwardStatus, reqVO.getAwardStatus())
                .eqIfPresent(ErpInquiryDO::getInquiryMode, reqVO.getInquiryMode())
                .betweenIfPresent(ErpInquiryDO::getDeadline, reqVO.getDeadline());
        if (Boolean.TRUE.equals(reqVO.getAwardOnly())) {
            query.ge(ErpInquiryDO::getBusinessStatus, 40);
        }
        return selectPage(reqVO, query.orderByDesc(ErpInquiryDO::getId));
    }
    default ErpInquiryDO selectByNo(String no) { return selectOne(ErpInquiryDO::getNo, no); }
}
