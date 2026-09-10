package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticketnote.CrmAfterSaleTicketNotePageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketNoteDO;

import java.util.List;

/**
 * 售后工单流转记录（备注） Service 接口
 *
 * @author txgycloud
 */
public interface CrmAfterSaleTicketNoteService {

    /**
     * 写入一条工单流转记录（由工单指派/状态变更/转有偿等动作内部调用）
     *
     * @param ticketId       工单编号
     * @param operateType    操作类型：见 CrmAfterSaleTicketOperateTypeEnum
     * @param content        操作内容
     * @param operatorUserId 操作人编号
     * @return 记录编号
     */
    Long createTicketNote(Long ticketId, Integer operateType, String content, Long operatorUserId);

    /**
     * 获得工单流转记录
     *
     * @param id 编号
     * @return 流转记录
     */
    CrmAfterSaleTicketNoteDO getTicketNote(Long id);

    /**
     * 获得工单流转记录分页（按 ticketId 过滤）
     *
     * @param pageReqVO 分页查询
     * @return 流转记录分页
     */
    PageResult<CrmAfterSaleTicketNoteDO> getTicketNotePage(CrmAfterSaleTicketNotePageReqVO pageReqVO);

    /**
     * 获得指定工单的流转记录列表（时间正序）
     *
     * @param ticketId 工单编号
     * @return 流转记录列表
     */
    List<CrmAfterSaleTicketNoteDO> getTicketNoteListByTicketId(Long ticketId);

}
