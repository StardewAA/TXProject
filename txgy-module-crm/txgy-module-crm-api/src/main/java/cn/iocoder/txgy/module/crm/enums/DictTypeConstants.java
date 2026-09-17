package cn.iocoder.txgy.module.crm.enums;

/**
 * CRM 字典类型的枚举类
 *
 * @author OPENLAB BS
 */
public interface DictTypeConstants {

    String CRM_CUSTOMER_INDUSTRY = "crm_customer_industry"; // CRM 客户所属行业（合并：下游+上游，用于展示）
    String CRM_CUSTOMER_CATEGORY = "crm_customer_category"; // CRM 客户类别（下游客户/上游供应商）
    String CRM_CUSTOMER_INDUSTRY_DOWNSTREAM = "crm_customer_industry_downstream"; // CRM 下游客户行业
    String CRM_CUSTOMER_INDUSTRY_UPSTREAM = "crm_customer_industry_upstream"; // CRM 上游供应商行业
    String CRM_CUSTOMER_LEVEL = "crm_customer_level"; // CRM 客户等级
    String CRM_CUSTOMER_SOURCE = "crm_customer_source"; // CRM 客户来源
    String CRM_AUDIT_STATUS = "crm_audit_status"; // CRM 审批状态
    String CRM_PRODUCT_UNIT = "crm_product_unit"; // CRM 产品单位
    String CRM_PRODUCT_STATUS = "crm_product_status"; // CRM 产品状态
    String CRM_PRODUCT_DATA_SOURCE = "crm_product_data_source"; // CRM 产品数据来源
    String CRM_FOLLOW_UP_TYPE = "crm_follow_up_type"; // CRM 跟进方式
    String CRM_RECEIVABLE_RETURN_TYPE = "crm_receivable_return_type"; // CRM 回款方式
    String CRM_QUOTATION_TYPE = "crm_quotation_type"; // CRM 报价类型（购销报价/采购报价）

    // ========== CRM 售后回访与质保管理 ==========
    String CRM_BUSINESS_SOURCE = "crm_business_source"; // CRM 商机来源
    String CRM_AFTER_SALE_WARRANTY_STATUS = "crm_after_sale_warranty_status"; // CRM 质保状态
    String CRM_AFTER_SALE_PLAN_TYPE = "crm_after_sale_visit_plan_type"; // CRM 回访计划类型
    String CRM_AFTER_SALE_VISIT_PLAN_STATUS = "crm_after_sale_visit_plan_status"; // CRM 回访计划状态
    String CRM_AFTER_SALE_VISIT_CYCLE = "crm_after_sale_visit_cycle"; // CRM 回访周期类型
    String CRM_AFTER_SALE_VISIT_TASK_STATUS = "crm_after_sale_visit_task_status"; // CRM 回访任务状态
    String CRM_AFTER_SALE_VISIT_TYPE = "crm_after_sale_visit_type"; // CRM 回访方式
    String CRM_AFTER_SALE_SATISFACTION = "crm_after_sale_satisfaction"; // CRM 满意度评价
    String CRM_AFTER_SALE_TICKET_STATUS = "crm_after_sale_ticket_status"; // CRM 问题工单状态
    String CRM_AFTER_SALE_TICKET_SOURCE = "crm_after_sale_ticket_source"; // CRM 问题工单来源
    String CRM_AFTER_SALE_TICKET_PRIORITY = "crm_after_sale_ticket_priority"; // CRM 问题工单优先级
    String CRM_AFTER_SALE_TICKET_OPERATE_TYPE = "crm_after_sale_ticket_operate_type"; // CRM 工单流转操作类型
    String CRM_AFTER_SALE_BUSINESS_SOURCE_TYPE = "crm_after_sale_business_source_type"; // CRM 售后转商机方向

}
