package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 售后问题工单 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleTicketMapper extends BaseMapperX<CrmAfterSaleTicketDO> {

    default CrmAfterSaleTicketDO selectByNo(String no) {
        return selectOne(CrmAfterSaleTicketDO::getNo, no);
    }

    default PageResult<CrmAfterSaleTicketDO> selectPage(CrmAfterSaleTicketPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleTicketDO>()
                .likeIfPresent(CrmAfterSaleTicketDO::getNo, reqVO.getNo())
                .likeIfPresent(CrmAfterSaleTicketDO::getTitle, reqVO.getTitle())
                .eqIfPresent(CrmAfterSaleTicketDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CrmAfterSaleTicketDO::getWarrantyId, reqVO.getWarrantyId())
                .eqIfPresent(CrmAfterSaleTicketDO::getSource, reqVO.getSource())
                .eqIfPresent(CrmAfterSaleTicketDO::getPriority, reqVO.getPriority())
                .eqIfPresent(CrmAfterSaleTicketDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CrmAfterSaleTicketDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .betweenIfPresent(CrmAfterSaleTicketDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CrmAfterSaleTicketDO::getId));
    }

}
