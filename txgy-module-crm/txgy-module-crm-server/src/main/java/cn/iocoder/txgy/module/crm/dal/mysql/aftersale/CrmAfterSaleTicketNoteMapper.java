package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticketnote.CrmAfterSaleTicketNotePageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketNoteDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 售后工单流转记录 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleTicketNoteMapper extends BaseMapperX<CrmAfterSaleTicketNoteDO> {

    default PageResult<CrmAfterSaleTicketNoteDO> selectPage(CrmAfterSaleTicketNotePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleTicketNoteDO>()
                .eqIfPresent(CrmAfterSaleTicketNoteDO::getTicketId, reqVO.getTicketId())
                .eqIfPresent(CrmAfterSaleTicketNoteDO::getOperateType, reqVO.getOperateType())
                .orderByAsc(CrmAfterSaleTicketNoteDO::getId));
    }

    default List<CrmAfterSaleTicketNoteDO> selectListByTicketId(Long ticketId) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleTicketNoteDO>()
                .eq(CrmAfterSaleTicketNoteDO::getTicketId, ticketId)
                .orderByAsc(CrmAfterSaleTicketNoteDO::getId));
    }

    default void deleteByTicketIds(Collection<Long> ticketIds) {
        delete(new LambdaQueryWrapperX<CrmAfterSaleTicketNoteDO>()
                .in(CrmAfterSaleTicketNoteDO::getTicketId, ticketIds));
    }

}
