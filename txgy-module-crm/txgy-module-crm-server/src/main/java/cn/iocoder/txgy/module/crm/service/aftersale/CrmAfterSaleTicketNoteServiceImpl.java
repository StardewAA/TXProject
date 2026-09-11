package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticketnote.CrmAfterSaleTicketNotePageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketNoteDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleTicketNoteMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 售后工单流转记录（备注） Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmAfterSaleTicketNoteServiceImpl implements CrmAfterSaleTicketNoteService {

    @Resource
    private CrmAfterSaleTicketNoteMapper ticketNoteMapper;

    @Override
    public Long createTicketNote(Long ticketId, Integer operateType, String content, Long operatorUserId) {
        CrmAfterSaleTicketNoteDO note = CrmAfterSaleTicketNoteDO.builder()
                .ticketId(ticketId).operateType(operateType)
                .content(content).operatorUserId(operatorUserId)
                .build();
        ticketNoteMapper.insert(note);
        return note.getId();
    }

    @Override
    public CrmAfterSaleTicketNoteDO getTicketNote(Long id) {
        return ticketNoteMapper.selectById(id);
    }

    @Override
    public PageResult<CrmAfterSaleTicketNoteDO> getTicketNotePage(CrmAfterSaleTicketNotePageReqVO pageReqVO) {
        return ticketNoteMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CrmAfterSaleTicketNoteDO> getTicketNoteListByTicketId(Long ticketId) {
        return ticketNoteMapper.selectListByTicketId(ticketId);
    }

}
