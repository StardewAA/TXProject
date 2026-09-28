package cn.iocoder.txgy.module.crm.enums;

/**
 * CRM 消息模板编码常量
 *
 * 对应 system_notify_template 表数据（见 deploy/mysql/repair/2026-09-08-crm-after-sale-notify.sql）
 *
 * @author txgycloud
 */
public interface MessageTemplateConstants {

    // ======================= 售后（质保/回访/工单）站内信模板 =======================

    String AFTER_SALE_VISIT_TASK_REMIND = "crm_after_sale_visit_task_remind"; // 回访任务临期/到期提醒
    String AFTER_SALE_WARRANTY_EXPIRE_REMIND = "crm_after_sale_warranty_expire_remind"; // 质保即将到期提醒
    String AFTER_SALE_WARRANTY_EXPIRED = "crm_after_sale_warranty_expired"; // 质保已到期通知
    String AFTER_SALE_TICKET_ASSIGN = "crm_after_sale_ticket_assign"; // 问题工单指派通知

}
