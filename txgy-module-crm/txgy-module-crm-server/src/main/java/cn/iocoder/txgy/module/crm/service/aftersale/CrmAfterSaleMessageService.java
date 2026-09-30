package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;

/**
 * 售后站内信消息 Service 接口
 *
 * 统一封装 CRM 售后各业务场景的站内信发送（V1 仅站内信，短信二期接入）
 *
 * @author txgycloud
 */
public interface CrmAfterSaleMessageService {

    /**
     * 发送「回访任务临期/到期」提醒给任务处理人
     *
     * @param userId 接收人（任务处理人）
     * @param task   回访任务
     */
    void sendVisitTaskRemind(Long userId, CrmAfterSaleVisitTaskDO task);

    /**
     * 发送「质保即将到期」提醒给指定用户
     *
     * @param userId   接收人（售后责任人/业务负责人）
     * @param warranty 质保档案
     */
    void sendWarrantyExpireRemind(Long userId, CrmAfterSaleWarrantyDO warranty);

    /**
     * 发送「质保已到期」通知给售后责任人与业务负责人
     *
     * <p>触发时机：质保状态自动流转为「已到期终止」时（每日 02:00 定时任务或手动刷新）。
     * 与「即将到期提醒」区分：提醒是提前预警，本通知是到期事实告知，
     * 用于推动到期回访、续保 / 转有偿跟进等后续动作。
     *
     * @param userId   接收人（售后责任人或业务负责人）
     * @param warranty 质保档案
     */
    void sendWarrantyExpiredNotice(Long userId, CrmAfterSaleWarrantyDO warranty);

    /**
     * 发送「问题工单指派」通知给工单处理人
     *
     * @param assigneeUserId 处理人（接收人）
     * @param ticket         问题工单
     */
    void sendTicketAssignNotify(Long assigneeUserId, CrmAfterSaleTicketDO ticket);

}
