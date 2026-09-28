package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import jakarta.validation.Valid;

/**
 * 售后问题工单 Service 接口
 *
 * @author txgycloud
 */
public interface CrmAfterSaleTicketService {

    /**
     * 创建问题工单；若关联回访记录，将自动带出客户并回写记录的 ticketId
     *
     * @param createReqVO 创建信息
     * @param userId      用户编号
     * @return 工单编号
     */
    Long createTicket(@Valid CrmAfterSaleTicketSaveReqVO createReqVO, Long userId);

    /**
     * 更新问题工单（仅未完成/未关闭的工单可编辑基础信息）
     *
     * @param updateReqVO 更新信息
     * @param userId      用户编号
     */
    void updateTicket(@Valid CrmAfterSaleTicketSaveReqVO updateReqVO, Long userId);

    /**
     * 删除问题工单（仅待处理且未指派的工单可删除）
     *
     * @param id     编号
     * @param userId 用户编号
     */
    void deleteTicket(Long id, Long userId);

    /**
     * 指派工单处理人
     *
     * @param id             工单编号
     * @param assigneeUserId 处理人编号
     * @param userId         操作人编号
     */
    void assignTicket(Long id, Long assigneeUserId, Long userId);

    /**
     * 更新工单状态（状态机流转：待处理-处理中-已完成/已关闭，支持退回/重开）
     *
     * @param id       工单编号
     * @param status   目标状态
     * @param content  处理内容/变更说明
     * @param userId   操作人编号
     */
    void updateTicketStatus(Long id, Integer status, String content, Long userId);

    /**
     * 获得问题工单
     *
     * @param id 编号
     * @return 问题工单
     */
    CrmAfterSaleTicketDO getTicket(Long id);

    /**
     * 校验问题工单是否存在
     *
     * @param id 编号
     * @return 问题工单
     */
    CrmAfterSaleTicketDO validateTicket(Long id);

    /**
     * 获得问题工单分页
     *
     * <p>当 pageReqVO.remindType = 工单超时预警（1）时，按「待办：工单超时预警」口径实时查询：
     * 工单状态 = 已超时（由超时检查任务/懒同步自动标记）+ 处理人在（本人 + 下属）范围内
     *
     * @param pageReqVO 分页查询
     * @param userId    当前登录用户编号（待办归属过滤使用）
     * @return 问题工单分页
     */
    PageResult<CrmAfterSaleTicketDO> getTicketPage(CrmAfterSaleTicketPageReqVO pageReqVO, Long userId);

    /**
     * 获得「工单超时预警」待办数量（实时查询，与分页查询同一口径，不落库）
     *
     * @param userId 当前登录用户编号
     * @return 待办数量
     */
    Long getTicketOverdueCount(Long userId);

    /**
     * 【定时任务 / 懒同步】工单超时自动标记
     *
     * <p>扫描所有未关闭（待处理 / 处理中 / 已完成）的工单，若「开始处理时间（未开始则以创建时间兜底）
     * + 售后设置『工单超时预警天数』」已早于当前时间，则把工单状态自动变更为「已超时」，
     * 并写入流转留痕。已超时的工单会出现在 CRM 待办事项的「工单超时预警」中。
     *
     * <p>异常处理：单条工单标记失败只记录日志，不中断其余工单的处理。
     *
     * @return 本次标记为「已超时」的工单数量
     */
    int autoMarkOverdueTickets();

}
